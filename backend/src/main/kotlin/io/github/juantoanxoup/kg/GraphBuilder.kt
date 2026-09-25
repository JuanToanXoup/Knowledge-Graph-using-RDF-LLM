package io.github.juantoanxoup.kg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.sparql.core.Transactional
import org.apache.jena.sparql.vocabulary.FOAF
import org.apache.jena.system.Txn
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/** Graph statistics as exposed by the API (`get_statistics`). */
@Serializable
data class GraphStatistics(
    @SerialName("total_triples") val totalTriples: Int,
    @SerialName("unique_subjects") val uniqueSubjects: Int,
    @SerialName("unique_predicates") val uniquePredicates: Int,
    @SerialName("unique_objects") val uniqueObjects: Int,
)

/**
 * Builds and manages the RDF knowledge graph with Apache Jena (graph_builder.py).
 *
 * By default the graph is an in-memory model used while extracting. [GraphStore.graph] returns a builder over a
 * stored graph instead, with [transactional] set, so every access runs inside a TDB2 transaction.
 */
class KnowledgeGraphBuilder(
    val namespace: String = Config.DEFAULT_NAMESPACE,
    val model: Model = ModelFactory.createDefaultModel(),
    private val transactional: Transactional? = null,
) {
    private val log = LoggerFactory.getLogger(KnowledgeGraphBuilder::class.java)

    init {
        if (transactional == null) model.setNsPrefixes(prefixes(namespace))
    }

    /** Runs [block] against the model, inside a read transaction when the model is stored. */
    fun <T> read(block: (Model) -> T): T =
        if (transactional == null) block(model) else Txn.calculateRead(transactional) { block(model) }

    /** Runs [block] against the model, inside a write transaction when the model is stored. */
    fun <T> write(block: (Model) -> T): T =
        if (transactional == null) block(model) else Txn.calculateWrite(transactional) { block(model) }

    /** Adds `entity a Class ; rdfs:label "text"`. */
    fun addEntity(entity: Entity) =
        write { m ->
            val resource = m.createResource(namespace + createUri(entity.text))
            resource.addProperty(RDF.type, mapEntityType(entity.type))
            resource.addProperty(RDFS.label, entity.text)
        }

    /** Adds `subject predicate object` with all three minted in the namespace. */
    fun addRelation(relation: Relation) =
        write { m ->
            val subject = m.createResource(namespace + createUri(relation.subject))
            val predicate = m.createProperty(namespace + createUri(relation.predicate))
            val obj = m.createResource(namespace + createUri(relation.obj))
            m.add(subject, predicate, obj)
        }

    /** Labelled, typed resources of the graph as [Entity] values, sorted by text: the inverse of [addEntity]. */
    fun entities(): List<Entity> =
        read { m ->
            m
                .listSubjectsWithProperty(RDFS.label)
                .asSequence()
                .mapNotNull { subject ->
                    val label =
                        subject
                            .getProperty(RDFS.label)
                            ?.`object`
                            ?.asLiteral()
                            ?.lexicalForm
                    val type = subject.getProperty(RDF.type)?.`object`?.asResource()
                    if (label == null || type == null) null else Entity(label, entityTypeOf(type))
                }.sortedBy { it.text }
                .toList()
        }

    /** Literal lexical form, else `rdfs:label`, else the last IRI segment with `_` as space. */
    fun label(node: RDFNode): String =
        read { m ->
            if (node.isLiteral) {
                node.asLiteral().lexicalForm
            } else {
                m
                    .getProperty(node.asResource(), RDFS.label)
                    ?.`object`
                    ?.asLiteral()
                    ?.lexicalForm
                    ?: node
                        .toString()
                        .substringAfterLast('/')
                        .substringAfterLast('#')
                        .replace('_', ' ')
            }
        }

    /** Local-name rule: drop characters outside word/space/`-`, then collapse space/`-` runs to `_`. */
    internal fun createUri(text: String): String =
        text
            .replace(NON_URI_CHARS, "")
            .replace(SEPARATORS, "_")

    /** Entity type to RDF class. */
    fun mapEntityType(entityType: String): Resource =
        when (entityType) {
            "PERSON" -> FOAF.Person
            "ORG" -> FOAF.Organization
            "GPE" -> model.createResource(namespace + "Place")
            "DATE" -> model.createResource(namespace + "Date")
            "WORK_OF_ART" -> model.createResource(namespace + "CreativeWork")
            "EVENT" -> model.createResource(namespace + "Event")
            else -> model.createResource(namespace + "Entity")
        }

    /** RDF class back to the entity type used by [mapEntityType]; unknown classes become `ENTITY`. */
    fun entityTypeOf(type: Resource): String =
        when (type.uri) {
            FOAF.Person.uri -> "PERSON"
            FOAF.Organization.uri -> "ORG"
            namespace + "Place" -> "GPE"
            namespace + "Date" -> "DATE"
            namespace + "CreativeWork" -> "WORK_OF_ART"
            namespace + "Event" -> "EVENT"
            else -> "ENTITY"
        }

    fun buildFromExtractions(
        entities: List<Entity>,
        relations: List<Relation>,
    ) = write { m ->
        entities.forEach(::addEntity)
        relations.forEach(::addRelation)
        log.info("Knowledge Graph built with {} triples", m.size())
    }

    fun saveRdf(
        path: Path,
        format: String = Config.DEFAULT_RDF_FORMAT,
    ) = read { m ->
        path.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { RDFDataMgr.write(it, m, RDFLanguages.nameToLang(format)) }
        log.info("Knowledge Graph saved to {}", path)
    }

    fun loadRdf(
        path: Path,
        format: String = Config.DEFAULT_RDF_FORMAT,
    ) = write { m ->
        RDFDataMgr.read(m, path.toString(), RDFLanguages.nameToLang(format))
        log.info("Knowledge Graph loaded from {}", path)
    }

    fun getStatistics(): GraphStatistics =
        read { m ->
            val statements = m.listStatements().toList()
            GraphStatistics(
                totalTriples = statements.size,
                uniqueSubjects = statements.map { it.subject }.toSet().size,
                uniquePredicates = statements.map { it.predicate }.toSet().size,
                uniqueObjects = statements.map { it.`object` }.toSet().size,
            )
        }

    companion object {
        private val NON_URI_CHARS = Regex("(?U)[^\\w\\s-]")
        private val SEPARATORS = Regex("[\\s-]+")

        /** Prefixes declared on every graph and in every export. */
        fun prefixes(namespace: String): Map<String, String> =
            mapOf("kg" to namespace, "foaf" to FOAF.NS, "rdfs" to RDFS.uri)
    }
}

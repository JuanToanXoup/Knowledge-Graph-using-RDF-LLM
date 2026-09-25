package io.github.juantoanxoup.kg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.sparql.vocabulary.FOAF
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

/** Builds and manages the RDF knowledge graph with Apache Jena (graph_builder.py). */
class KnowledgeGraphBuilder(
    val namespace: String = Config.DEFAULT_NAMESPACE,
) {
    private val log = LoggerFactory.getLogger(KnowledgeGraphBuilder::class.java)
    val model: Model = ModelFactory.createDefaultModel()

    init {
        model.setNsPrefix("kg", namespace)
        model.setNsPrefix("foaf", FOAF.NS)
    }

    /** Adds `entity a Class ; rdfs:label "text"`. */
    fun addEntity(entity: Entity) {
        val resource = model.createResource(namespace + createUri(entity.text))
        resource.addProperty(RDF.type, mapEntityType(entity.type))
        resource.addProperty(RDFS.label, entity.text)
    }

    /** Adds `subject predicate object` with all three minted in the namespace. */
    fun addRelation(relation: Relation) {
        val subject = model.createResource(namespace + createUri(relation.subject))
        val predicate = model.createProperty(namespace + createUri(relation.predicate))
        val obj = model.createResource(namespace + createUri(relation.obj))
        model.add(subject, predicate, obj)
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

    fun buildFromExtractions(
        entities: List<Entity>,
        relations: List<Relation>,
    ) {
        entities.forEach(::addEntity)
        relations.forEach(::addRelation)
        log.info("Knowledge Graph built with {} triples", model.size())
    }

    fun saveRdf(
        path: Path,
        format: String = Config.DEFAULT_RDF_FORMAT,
    ) {
        path.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { RDFDataMgr.write(it, model, RDFLanguages.nameToLang(format)) }
        log.info("Knowledge Graph saved to {}", path)
    }

    fun loadRdf(
        path: Path,
        format: String = Config.DEFAULT_RDF_FORMAT,
    ) {
        RDFDataMgr.read(model, path.toString(), RDFLanguages.nameToLang(format))
        log.info("Knowledge Graph loaded from {}", path)
    }

    fun getStatistics(): GraphStatistics {
        val statements = model.listStatements().toList()
        return GraphStatistics(
            totalTriples = statements.size,
            uniqueSubjects = statements.map { it.subject }.toSet().size,
            uniquePredicates = statements.map { it.predicate }.toSet().size,
            uniqueObjects = statements.map { it.`object` }.toSet().size,
        )
    }

    private companion object {
        val NON_URI_CHARS = Regex("(?U)[^\\w\\s-]")
        val SEPARATORS = Regex("[\\s-]+")
    }
}

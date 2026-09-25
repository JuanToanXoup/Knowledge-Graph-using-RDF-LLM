package io.github.juantoanxoup.kg

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFList
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.system.Txn
import org.apache.jena.tdb2.TDB2Factory
import org.apache.jena.vocabulary.DCTerms
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.VOID
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories

/** Terms of the store's own bookkeeping, minted in the knowledge-graph namespace. */
class KgVocabulary(
    namespace: String,
) {
    val knowledgeGraph: Resource = ResourceFactory.createResource(namespace + "KnowledgeGraph")
    val chatMessage: Resource = ResourceFactory.createResource(namespace + "ChatMessage")
    val entityCount: Property = ResourceFactory.createProperty(namespace + "entityCount")
    val relationCount: Property = ResourceFactory.createProperty(namespace + "relationCount")
    val sequence: Property = ResourceFactory.createProperty(namespace + "sequence")
    val role: Property = ResourceFactory.createProperty(namespace + "role")
    val content: Property = ResourceFactory.createProperty(namespace + "content")
    val facts: Property = ResourceFactory.createProperty(namespace + "facts")
}

/**
 * Persistent home of every knowledge graph: one Apache Jena TDB2 dataset on disk.
 *
 * Each document's graph is the named graph `<namespace>graph/<id>`. Its description (source file, creation time,
 * counts) is kept in the metadata graph `<namespace>graphs` with Dublin Core and VoID terms, and its chat history
 * in the named graph `<namespace>chat/<id>`. Every access runs in a transaction, as TDB2 requires; nested calls
 * join the transaction already open on the thread.
 */
class GraphStore(
    location: Path,
    val namespace: String = Config.DEFAULT_NAMESPACE,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(GraphStore::class.java)
    private val vocab = KgVocabulary(namespace)
    private val metadataGraphIri = namespace + "graphs"
    val dataset: Dataset = TDB2Factory.connectDataset(location.createDirectories().toString())

    init {
        log.info("Graph store opened at {}", location.toAbsolutePath())
    }

    fun graphIri(graphId: String): String = "${namespace}graph/$graphId"

    fun chatIri(graphId: String): String = "${namespace}chat/$graphId"

    fun <T> read(block: () -> T): T = Txn.calculateRead(dataset, block)

    fun <T> write(block: () -> T): T = Txn.calculateWrite(dataset, block)

    /** Replaces the named graph of [metadata]'s id with [model] and records the metadata, in one transaction. */
    fun save(
        metadata: GraphMetadata,
        model: Model,
    ) = write {
        val iri = graphIri(metadata.graphId)
        dataset.replaceNamedModel(iri, model)
        // TDB2 keeps one prefix map per dataset (on the default graph), not one per named graph.
        dataset.defaultModel.setNsPrefixes(model.nsPrefixMap)
        val meta = dataset.getNamedModel(metadataGraphIri)
        val stats = metadata.statistics
        meta
            .getResource(iri)
            .removeProperties()
            .addProperty(RDF.type, vocab.knowledgeGraph)
            .addProperty(DCTerms.identifier, metadata.graphId)
            .addProperty(DCTerms.source, metadata.filename)
            .addProperty(DCTerms.created, meta.createTypedLiteral(metadata.createdAt, XSDDatatype.XSDdateTime))
            .addLiteral(vocab.entityCount, metadata.entitiesCount)
            .addLiteral(vocab.relationCount, metadata.relationsCount)
            .addLiteral(VOID.triples, stats.totalTriples)
            .addLiteral(VOID.distinctSubjects, stats.uniqueSubjects)
            .addLiteral(VOID.properties, stats.uniquePredicates)
            .addLiteral(VOID.distinctObjects, stats.uniqueObjects)
        log.info("Stored graph {} ({} triples)", iri, stats.totalTriples)
    }

    /** Every stored graph, oldest first. */
    fun list(): List<GraphMetadata> =
        read {
            dataset
                .getNamedModel(metadataGraphIri)
                .listSubjectsWithProperty(RDF.type, vocab.knowledgeGraph)
                .asSequence()
                .map { it.toMetadata() }
                .sortedBy { it.createdAt }
                .toList()
        }

    fun metadata(graphId: String): GraphMetadata? =
        read {
            dataset
                .getNamedModel(metadataGraphIri)
                .getResource(graphIri(graphId))
                .takeIf { it.hasProperty(RDF.type, vocab.knowledgeGraph) }
                ?.toMetadata()
        }

    fun contains(graphId: String): Boolean = metadata(graphId) != null

    /** A builder view over the stored graph; its reads and writes are transactional. */
    fun graph(graphId: String): KnowledgeGraphBuilder =
        KnowledgeGraphBuilder(namespace, read { dataset.getNamedModel(graphIri(graphId)) }, dataset)

    /** Removes the graph, its metadata and its chat history. Unknown ids are ignored. */
    fun delete(graphId: String) =
        write {
            dataset.removeNamedModel(graphIri(graphId))
            dataset.removeNamedModel(chatIri(graphId))
            dataset.getNamedModel(metadataGraphIri).getResource(graphIri(graphId)).removeProperties()
            log.info("Deleted graph {}", graphIri(graphId))
        }

    /** Serializes the stored graph with the dataset's prefixes; `format` is a Jena language name such as `TURTLE`. */
    fun export(
        graphId: String,
        out: OutputStream,
        format: String = Config.DEFAULT_RDF_FORMAT,
    ) = read {
        val export =
            ModelFactory
                .createDefaultModel()
                .setNsPrefixes(KnowledgeGraphBuilder.prefixes(namespace))
                .setNsPrefixes(dataset.defaultModel.nsPrefixMap)
                .add(dataset.getNamedModel(graphIri(graphId)))
        RDFDataMgr.write(out, export, RDFLanguages.nameToLang(format))
    }

    /** Chat messages of the graph in the order they were appended. */
    fun chatHistory(graphId: String): List<ChatHistoryMessage> =
        read {
            dataset
                .getNamedModel(chatIri(graphId))
                .listSubjectsWithProperty(RDF.type, vocab.chatMessage)
                .asSequence()
                .map { it.getProperty(vocab.sequence).int to it.toMessage() }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
        }

    /** Appends [message] to the graph's chat and returns the new message count. */
    fun appendChat(
        graphId: String,
        message: ChatHistoryMessage,
    ): Int =
        write {
            val chat = dataset.getNamedModel(chatIri(graphId))
            val next =
                chat
                    .listObjectsOfProperty(vocab.sequence)
                    .asSequence()
                    .maxOfOrNull { it.asLiteral().int }
                    ?.plus(1) ?: 1
            val entry =
                chat
                    .createResource("${chatIri(graphId)}/message/$next")
                    .addProperty(RDF.type, vocab.chatMessage)
                    .addLiteral(vocab.sequence, next)
                    .addProperty(vocab.role, message.role)
                    .addProperty(vocab.content, message.content)
                    .addProperty(DCTerms.date, message.timestamp)
            message.facts?.let { facts ->
                val literals: Iterator<RDFNode> = facts.map { chat.createLiteral(it) }.iterator()
                entry.addProperty(vocab.facts, chat.createList(literals))
            }
            next
        }

    fun clearChat(graphId: String) = write { dataset.removeNamedModel(chatIri(graphId)) }

    override fun close() = dataset.close()

    private fun Resource.toMetadata(): GraphMetadata =
        GraphMetadata(
            graphId = getProperty(DCTerms.identifier).string,
            filename = getProperty(DCTerms.source).string,
            createdAt = getProperty(DCTerms.created).literal.lexicalForm,
            entitiesCount = getProperty(vocab.entityCount).int,
            relationsCount = getProperty(vocab.relationCount).int,
            statistics =
                GraphStatistics(
                    totalTriples = getProperty(VOID.triples).int,
                    uniqueSubjects = getProperty(VOID.distinctSubjects).int,
                    uniquePredicates = getProperty(VOID.properties).int,
                    uniqueObjects = getProperty(VOID.distinctObjects).int,
                ),
        )

    private fun Resource.toMessage(): ChatHistoryMessage =
        ChatHistoryMessage(
            role = getProperty(vocab.role).string,
            content = getProperty(vocab.content).string,
            timestamp = getProperty(DCTerms.date).string,
            facts =
                getProperty(vocab.facts)
                    ?.resource
                    ?.`as`(RDFList::class.java)
                    ?.asJavaList()
                    ?.map { it.asLiteral().lexicalForm },
        )
}

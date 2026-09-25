package io.github.juantoanxoup.kg

import org.apache.jena.sparql.vocabulary.FOAF
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnowledgeGraphBuilderTest {
    private val builder = KnowledgeGraphBuilder()

    @Test
    fun `local names drop punctuation and join words with underscores`() {
        assertEquals("Albert_Einstein", builder.createUri("Albert Einstein!"))
        assertEquals("New_York_City", builder.createUri("New-York  City"))
        assertEquals("Zürich", builder.createUri("Zürich"))
        assertEquals("", builder.createUri("(...)"))
    }

    @Test
    fun `entity types map to FOAF and namespace classes`() {
        assertEquals(FOAF.Person, builder.mapEntityType("PERSON"))
        assertEquals(FOAF.Organization, builder.mapEntityType("ORG"))
        assertEquals(Config.DEFAULT_NAMESPACE + "Place", builder.mapEntityType("GPE").uri)
        assertEquals(Config.DEFAULT_NAMESPACE + "Date", builder.mapEntityType("DATE").uri)
        assertEquals(Config.DEFAULT_NAMESPACE + "CreativeWork", builder.mapEntityType("WORK_OF_ART").uri)
        assertEquals(Config.DEFAULT_NAMESPACE + "Event", builder.mapEntityType("EVENT").uri)
        assertEquals(Config.DEFAULT_NAMESPACE + "Entity", builder.mapEntityType("MISC").uri)
    }

    @Test
    fun `entities produce a type and a label triple and relations one triple`() {
        val kg = sampleGraph()
        // 4 entities x 2 triples + 2 relations
        assertEquals(10, kg.model.size())
        val einstein = kg.model.getResource(Config.DEFAULT_NAMESPACE + "Albert_Einstein")
        assertTrue(kg.model.contains(einstein, RDF.type, FOAF.Person))
        assertEquals("Albert Einstein", kg.model.getProperty(einstein, RDFS.label).string)
        val workedAt = kg.model.getProperty(Config.DEFAULT_NAMESPACE + "worked_at")
        assertTrue(
            kg.model.contains(
                einstein,
                workedAt,
                kg.model.getResource(
                    Config.DEFAULT_NAMESPACE + "Princeton_University",
                ),
            ),
        )
    }

    @Test
    fun `statistics count triples subjects predicates and objects`() {
        val stats = sampleGraph().getStatistics()
        assertEquals(10, stats.totalTriples)
        assertEquals(4, stats.uniqueSubjects)
        assertEquals(4, stats.uniquePredicates) // rdf:type, rdfs:label, worked_at, colleague_of
        assertTrue(stats.uniqueObjects >= 7)
    }

    @Test
    fun `turtle round trip preserves the graph`(
        @TempDir dir: Path,
    ) {
        val path = dir.resolve("kg.ttl")
        val kg = sampleGraph()
        kg.saveRdf(path)
        val loaded = KnowledgeGraphBuilder().apply { loadRdf(path) }
        assertTrue(loaded.model.isIsomorphicWith(kg.model))
        assertEquals(Config.DEFAULT_NAMESPACE, loaded.model.getNsPrefixURI("kg"))
    }
}

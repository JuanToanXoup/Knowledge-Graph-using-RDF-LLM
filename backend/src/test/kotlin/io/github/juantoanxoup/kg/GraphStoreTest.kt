package io.github.juantoanxoup.kg

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphStoreTest {
    private val id = "20250101_000000_abcdef01"

    private fun metadata(kg: KnowledgeGraphBuilder) =
        GraphMetadata(id, "sample.txt", "2025-01-01T00:00:00Z", 4, 2, kg.getStatistics())

    @Test
    fun `graphs and their metadata survive reopening the store`(
        @TempDir dir: Path,
    ) {
        val expected = metadata(sampleGraph())
        GraphStore(dir).use { it.save(expected, sampleGraph().model) }

        GraphStore(dir).use { store ->
            assertEquals(listOf(expected), store.list())
            assertEquals(expected, store.metadata(id))
            assertTrue(store.contains(id))
            assertNull(store.metadata("missing"))

            val kg = store.graph(id)
            assertEquals(10, kg.getStatistics().totalTriples)
            assertEquals(
                listOf("1879", "Albert Einstein", "Marie Curie", "Princeton University"),
                kg.entities().map { it.text },
            )
            assertEquals("PERSON", kg.entities().first { it.text == "Marie Curie" }.type)
            assertEquals("DATE", kg.entities().first { it.text == "1879" }.type)
            assertEquals(
                listOf(mapOf("o" to "Princeton University")),
                KnowledgeGraphQuerier(kg).query("SELECT ?o WHERE { ?s <${Config.DEFAULT_NAMESPACE}worked_at> ?o }"),
            )

            val turtle = ByteArrayOutputStream().also { store.export(id, it) }.toString()
            // Jena writes SPARQL-style `PREFIX` lines by default.
            assertTrue(turtle.contains("PREFIX kg:"), turtle)
            assertTrue(turtle.contains("kg:Albert_Einstein  a  foaf:Person"), turtle)
            assertTrue(turtle.contains("rdfs:label"), turtle)
        }
    }

    @Test
    fun `saving under an existing id replaces the graph`(
        @TempDir dir: Path,
    ) = GraphStore(dir).use { store ->
        store.save(metadata(sampleGraph()), sampleGraph().model)
        val smaller = KnowledgeGraphBuilder().apply { addEntity(Entity("Isaac Newton", "PERSON")) }
        store.save(metadata(smaller).copy(filename = "newton.txt"), smaller.model)

        assertEquals(1, store.list().size)
        assertEquals("newton.txt", store.metadata(id)?.filename)
        assertEquals(listOf("Isaac Newton"), store.graph(id).entities().map { it.text })
    }

    @Test
    fun `chat history keeps order and facts and disappears with its graph`(
        @TempDir dir: Path,
    ) = GraphStore(dir).use { store ->
        store.save(metadata(sampleGraph()), sampleGraph().model)
        assertEquals(1, store.appendChat(id, ChatHistoryMessage("user", "who?", "2025-01-01T00:00:00Z")))
        assertEquals(
            2,
            store.appendChat(
                id,
                ChatHistoryMessage("assistant", "Einstein", "2025-01-01T00:00:01Z", listOf("f1", "f2")),
            ),
        )

        val history = store.chatHistory(id)
        assertEquals(listOf("user", "assistant"), history.map { it.role })
        assertNull(history[0].facts)
        assertEquals(listOf("f1", "f2"), history[1].facts)

        store.clearChat(id)
        assertTrue(store.chatHistory(id).isEmpty())

        store.appendChat(id, ChatHistoryMessage("user", "again", "2025-01-01T00:00:02Z"))
        store.delete(id)
        assertFalse(store.contains(id))
        assertTrue(store.chatHistory(id).isEmpty())
        assertEquals(0, store.graph(id).getStatistics().totalTriples)
    }
}

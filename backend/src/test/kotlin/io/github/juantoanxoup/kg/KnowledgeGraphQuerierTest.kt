package io.github.juantoanxoup.kg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KnowledgeGraphQuerierTest {
    private val querier = KnowledgeGraphQuerier(sampleGraph())

    @Test
    fun `rows substitute labels for IRIs and keep literals`() {
        val rows =
            querier.query(
                """
                PREFIX kg: <${Config.DEFAULT_NAMESPACE}>
                SELECT ?s ?o WHERE { ?s kg:worked_at ?o }
                """.trimIndent(),
            )
        assertEquals(listOf(mapOf("s" to "Albert Einstein", "o" to "Princeton University")), rows)
    }

    @Test
    fun `unlabelled IRIs fall back to the IRI string`() {
        val rows = querier.query("SELECT ?type WHERE { <${Config.DEFAULT_NAMESPACE}Albert_Einstein> a ?type }")
        assertEquals(listOf(mapOf("type" to "http://xmlns.com/foaf/0.1/Person")), rows)
    }

    @Test
    fun `entity relations return predicate object and optional label`() {
        val rows = querier.findEntityRelations("Albert Einstein")
        // Predicates carry no rdfs:label, so they render as their IRI (the CLI shortens them for display).
        val workedAt = rows.single { it["predicate"] == Config.DEFAULT_NAMESPACE + "worked_at" }
        assertEquals("Princeton University", workedAt["object"])
        assertEquals("Princeton University", workedAt["objLabel"])
        val type = rows.single { it["predicate"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" }
        assertEquals("", type["objLabel"])
    }

    @Test
    fun `entity name is bound as a literal and cannot inject SPARQL`() {
        val rows = querier.findEntityRelations("\" } UNION { ?subject ?predicate ?object . FILTER(true) } #")
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `non select queries are rejected`() {
        assertFailsWith<IllegalArgumentException> { querier.query("ASK { ?s ?p ?o }") }
    }
}

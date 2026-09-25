package io.github.juantoanxoup.kg

import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.vocabulary.RDFS

/** SPARQL access to the knowledge graph (graph_querier.py). */
class KnowledgeGraphQuerier(
    private val kg: KnowledgeGraphBuilder,
) {
    /**
     * Executes a SELECT query. Literals become their lexical form; IRIs become their `rdfs:label`
     * when one exists, else the IRI string. Unbound variables become an empty string.
     */
    fun query(sparql: String): List<Map<String, String>> {
        val parsed = QueryFactory.create(sparql)
        require(parsed.isSelectType) { "Only SELECT queries are supported" }
        return QueryExecutionFactory.create(parsed, kg.model).use { exec ->
            val results = exec.execSelect()
            val vars = results.resultVars
            results.asSequence().map { row -> vars.associateWith { render(row[it]) } }.toList()
        }
    }

    /** All outgoing relations of the entity whose `rdfs:label` equals [entityName]. */
    fun findEntityRelations(entityName: String): List<Map<String, String>> {
        val query =
            ParameterizedSparqlString(
                """
                PREFIX kg: <${kg.namespace}>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                SELECT ?predicate ?object ?objLabel
                WHERE {
                    ?subject rdfs:label ?name .
                    ?subject ?predicate ?object .
                    OPTIONAL { ?object rdfs:label ?objLabel }
                }
                """.trimIndent(),
            )
        query.setLiteral("name", entityName)
        return query(query.toString())
    }

    private fun render(node: RDFNode?): String =
        when {
            node == null -> ""
            node.isLiteral -> node.asLiteral().lexicalForm
            else ->
                kg.model
                    .getProperty(node.asResource(), RDFS.label)
                    ?.`object`
                    ?.asLiteral()
                    ?.lexicalForm
                    ?: node.toString()
        }
}

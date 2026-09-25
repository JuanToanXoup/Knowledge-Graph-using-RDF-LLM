package io.github.juantoanxoup.kg

import kotlinx.serialization.Serializable
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS

// The dataset shape of the 3D cloud view (`:frontend:cloud`, after term-graph): nodes and links with display text.

@Serializable
data class CloudNode(
    val id: String,
    val label: String,
    val group: String,
    val summary: String,
    val description: String,
)

@Serializable
data class CloudLink(
    val source: String,
    val target: String,
    val kind: String,
)

@Serializable
data class CloudGraph(
    val title: String,
    val description: String,
    val nodes: List<CloudNode>,
    val links: List<CloudLink>,
)

/**
 * The graph as the cloud view wants it: one node per resource (typed entities and bare relation targets alike),
 * one link per relation triple, labels resolved, class and predicate names made readable.
 */
fun KnowledgeGraphBuilder.toCloudGraph(
    title: String,
    description: String,
): CloudGraph =
    read { model ->
        val statements = model.listStatements().toList()
        val relations =
            statements.filter { it.predicate != RDF.type && it.predicate != RDFS.label && it.`object`.isResource }
        val resources =
            (statements.map { it.subject } + relations.map { it.`object`.asResource() })
                .distinctBy { it.uri }
                .filter { it.isURIResource }

        val links =
            relations
                .map { CloudLink(localName(it.subject), localName(it.`object`.asResource()), readable(it.predicate)) }
                .distinct()
        val factsBySource = relations.groupBy { localName(it.subject) }
        val factsByTarget = relations.groupBy { localName(it.`object`.asResource()) }
        val nodes =
            resources
                .map { resource ->
                    val id = localName(resource)
                    val outgoing =
                        factsBySource[id].orEmpty().map {
                            "${label(it.subject)} ${readable(it.predicate)} ${label(it.`object`)}"
                        }
                    val incoming =
                        factsByTarget[id].orEmpty().map {
                            "${label(it.subject)} ${readable(it.predicate)} ${label(it.`object`)}"
                        }
                    val degree = (outgoing + incoming).distinct().size
                    CloudNode(
                        id = id,
                        label = label(resource),
                        group =
                            resource
                                .getProperty(
                                    RDF.type,
                                )?.`object`
                                ?.asResource()
                                ?.let { readable(it) } ?: "Entity",
                        summary = "$degree ${if (degree == 1) "connection" else "connections"}",
                        description = (outgoing + incoming).distinct().joinToString("\n"),
                    )
                }.sortedBy { it.label }
        CloudGraph(title, description, nodes, links)
    }

private fun localName(resource: Resource): String = resource.uri.substringAfterLast('/').substringAfterLast('#')

/** Class and predicate names as words: `worked_at` becomes `worked at`. */
private fun readable(resource: Resource): String = localName(resource).replace('_', ' ')

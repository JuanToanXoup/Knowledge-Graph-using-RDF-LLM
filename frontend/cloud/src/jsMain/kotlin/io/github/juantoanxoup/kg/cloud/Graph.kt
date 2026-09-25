package io.github.juantoanxoup.kg.cloud

import kotlinx.serialization.Serializable

/** One term in the cloud (term-graph's `TermNode`). */
@Serializable
data class TermNode(
    val id: String,
    val label: String,
    val group: String,
    val summary: String,
    val description: String,
    /** Drives sphere size when present; otherwise the node's degree does. */
    val weight: Double? = null,
    val kind: String? = null,
)

/** An undirected-for-display link between two terms; `kind` names the relation. */
@Serializable
data class TermLink(
    val source: String,
    val target: String,
    val kind: String? = null,
)

/** The dataset shape term-graph loads: `nodes`, `links`, optional backbone kinds and metadata. */
@Serializable
data class GraphData(
    val nodes: List<TermNode>,
    val links: List<TermLink>,
    val backboneKinds: List<String>? = null,
    val title: String? = null,
    val description: String? = null,
)

/** Neighbours of every node, both directions. */
fun neighborMap(g: GraphData): Map<String, Set<String>> {
    val m = g.nodes.associateTo(LinkedHashMap()) { it.id to LinkedHashSet<String>() }
    for (link in g.links) {
        m[link.source]?.add(link.target)
        m[link.target]?.add(link.source)
    }
    return m
}

fun degreeMap(g: GraphData): Map<String, Int> = neighborMap(g).mapValues { it.value.size }

/** A node's neighbours under one link kind; `kind == null` is the untyped "Connects to" bucket. */
data class NeighborGroup(
    val kind: String?,
    val nodes: List<TermNode>,
)

/** Neighbours bucketed by link kind, kinds in first-appearance order, nodes in dataset order. */
fun neighborGroups(
    g: GraphData,
    id: String,
): List<NeighborGroup> {
    val byKind = LinkedHashMap<String?, LinkedHashSet<String>>()
    for (link in g.links) {
        val other =
            when (id) {
                link.source -> link.target
                link.target -> link.source
                else -> continue
            }
        byKind.getOrPut(link.kind) { LinkedHashSet() }.add(other)
    }
    return byKind.map { (kind, ids) -> NeighborGroup(kind, g.nodes.filter { it.id in ids }) }
}

private const val BASE_RADIUS = 2.2
private const val WEIGHT_SCALE = 1.6
private const val DEGREE_SCALE = 6.5
private const val DEGREE_REFERENCE = 37.0

/** term-graph's log-degree size curve at native world scale. */
fun radiusOf(
    node: TermNode,
    degree: Int,
): Double =
    if (node.weight != null) {
        BASE_RADIUS + node.weight * WEIGHT_SCALE
    } else {
        BASE_RADIUS + (kotlin.math.ln(1.0 + degree) / kotlin.math.ln(1.0 + DEGREE_REFERENCE)) * DEGREE_SCALE
    }

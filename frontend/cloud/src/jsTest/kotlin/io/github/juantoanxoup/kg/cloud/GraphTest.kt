package io.github.juantoanxoup.kg.cloud

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphTest {
    private fun node(
        id: String,
        group: String = "Person",
    ) = TermNode(id, id.replaceFirstChar(Char::uppercase), group, "$id summary", "$id description")

    private val graph =
        GraphData(
            nodes = listOf(node("marie"), node("pierre"), node("paris", "Place"), node("radium", "Entity")),
            links =
                listOf(
                    TermLink("marie", "pierre", "married to"),
                    TermLink("marie", "paris", "moved to"),
                    TermLink("marie", "radium", "discovered"),
                    TermLink("pierre", "radium", "discovered"),
                ),
        )

    @Test
    fun neighbours_are_symmetric_and_degrees_count_them() {
        val n = neighborMap(graph)
        assertEquals(setOf("pierre", "paris", "radium"), n["marie"])
        assertEquals(setOf("marie", "pierre"), n["radium"])
        assertEquals(mapOf("marie" to 3, "pierre" to 2, "paris" to 1, "radium" to 2), degreeMap(graph))
    }

    @Test
    fun neighbour_groups_follow_link_kind_in_first_appearance_order() {
        val groups = neighborGroups(graph, "marie")
        assertEquals(listOf("married to", "moved to", "discovered"), groups.map { it.kind })
        assertEquals(listOf("Radium"), groups.last().nodes.map { it.label })
    }

    @Test
    fun radius_grows_with_degree_and_weight_overrides_it() {
        val n = node("x")
        assertTrue(radiusOf(n, 0) < radiusOf(n, 5))
        assertTrue(radiusOf(n, 5) < radiusOf(n, 37))
        assertEquals(2.2 + 3 * 1.6, radiusOf(n.copy(weight = 3.0), 0))
    }

    @Test
    fun seeded_random_and_easing_match_the_original_curves() {
        assertEquals(rand(1.0), rand(1.0))
        assertTrue(rand(1.0) in 0.0..1.0 && rand(2.0) in 0.0..1.0 && rand(1.0) != rand(2.0))
        assertEquals(0.0, easeOutExpo(0.0))
        assertEquals(1.0, easeOutExpo(1.0))
        assertTrue(easeOutExpo(0.5) > 0.96)
    }

    @Test
    fun the_settled_cloud_is_centred_finite_and_deterministic() {
        val cloud = buildCloud(graph, degreeMap(graph))
        assertEquals(4, cloud.nodes.size)
        assertEquals(4, cloud.linkCount)
        assertTrue(cloud.nodes.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        assertTrue(abs(cloud.nodes.sumOf { it.x }) < 1e-6 && abs(cloud.nodes.sumOf { it.y }) < 1e-6)
        assertTrue(cloud.cloudRadius > 0)
        assertEquals(
            cloud.nodes
                .map { it.x to it.y }
                .toSet()
                .size,
            4,
            "distinct positions",
        )
        val again = buildCloud(graph, degreeMap(graph))
        assertEquals(cloud.nodes.map { Triple(it.x, it.y, it.z) }, again.nodes.map { Triple(it.x, it.y, it.z) })
        assertEquals(setOf(1, 2, 3), cloud.neighborSets[0])
        // Every control point bulges 16% of its edge length.
        val a = cloud.nodes[cloud.pairs[0]]
        val b = cloud.nodes[cloud.pairs[1]]
        val len = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y) + (b.z - a.z) * (b.z - a.z))
        val o = cloud.controlOffsets
        assertTrue(abs(sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]) - 0.16 * len) < 1e-6)
    }

    @Test
    fun repack_hides_non_matches_and_clusters_matches_around_the_origin() {
        val cloud = buildCloud(graph, degreeMap(graph))
        val repack = Repack(cloud)
        repack.applyMatches(intArrayOf(0))
        assertTrue(repack.active)
        assertEquals(listOf(1.0, 0.0, 0.0, 0.0), repack.matchTarget.toList())
        repack.solveRepack(intArrayOf(0))
        val n = cloud.nodes[0]
        assertEquals(-n.x, repack.repackOffset[0])
        assertEquals(n.r + 16, repack.fitRadius)

        repack.applyMatches(intArrayOf(0, 1))
        repack.solveRepack(intArrayOf(0, 1))
        val cx = (cloud.nodes[0].x + repack.repackOffset[0] + cloud.nodes[1].x + repack.repackOffset[3]) / 2
        assertTrue(abs(cx) < 1e-6, "cluster centred")
        assertTrue(repack.fitRadius > 16)

        repack.applyMatches(null)
        assertTrue(!repack.active && repack.matchTarget.all { it == 1.0 })
    }
}

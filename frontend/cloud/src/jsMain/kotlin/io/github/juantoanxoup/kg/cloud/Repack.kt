package io.github.juantoanxoup.kg.cloud

import kotlin.js.json
import kotlin.math.max
import kotlin.math.sqrt

/**
 * term-graph's search repack: while a search is active, matched nodes are re-laid-out into their own tight
 * cluster around the origin with the same physics as the main layout, and everything else fades out.
 */
class Repack(
    private val cloud: Cloud,
) {
    /** Per-node visibility target: 1 shown, 0 search-filtered. */
    val matchTarget = DoubleArray(cloud.nodes.size) { 1.0 }

    /** Per-node xyz spring target while repacked, as an offset from the rest position. */
    val repackOffset = DoubleArray(cloud.nodes.size * 3)
    var active = false
        private set
    var fitRadius = 0.0
        private set

    /** Immediate on query change: set visibility targets; null resets. */
    fun applyMatches(indices: IntArray?) {
        if (indices == null) {
            matchTarget.fill(1.0)
            repackOffset.fill(0.0)
            active = false
            fitRadius = 0.0
            return
        }
        val keep = indices.toSet()
        for (i in matchTarget.indices) matchTarget[i] = if (i in keep) 1.0 else 0.0
        active = true
    }

    /** Debounced behind [applyMatches]: solve the matched cluster layout. */
    fun solveRepack(indices: IntArray) {
        val nodes = cloud.nodes
        val matched = indices.filter { it in nodes.indices }
        if (matched.isEmpty()) {
            fitRadius = 0.0
            return
        }
        if (matched.size == 1) {
            val n = nodes[matched[0]]
            repackOffset[n.index * 3] = -n.x
            repackOffset[n.index * 3 + 1] = -n.y
            repackOffset[n.index * 3 + 2] = -n.z
            fitRadius = n.r + FIT_MARGIN
            return
        }
        val cx = matched.sumOf { nodes[it].x } / matched.size
        val cy = matched.sumOf { nodes[it].y } / matched.size
        val cz = matched.sumOf { nodes[it].z } / matched.size
        val pack: Array<dynamic> =
            matched
                .map { mi ->
                    val n = nodes[mi]
                    json("r" to n.r, "x" to n.x - cx, "y" to n.y - cy, "z" to n.z - cz)
                }.toTypedArray()
        val packIndex = matched.withIndex().associate { (i, mi) -> mi to i }
        val pairs = ArrayList<Int>()
        for (li in 0 until cloud.linkCount) {
            val s = packIndex[cloud.pairs[li * 2]] ?: continue
            val t = packIndex[cloud.pairs[li * 2 + 1]] ?: continue
            pairs += s
            pairs += t
        }
        settle(pack, pairs.toIntArray())

        val ax = pack.sumOf { it.x as Double } / pack.size
        val ay = pack.sumOf { it.y as Double } / pack.size
        val az = pack.sumOf { it.z as Double } / pack.size
        var fit = 0.0
        for ((i, mi) in matched.withIndex()) {
            val n = nodes[mi]
            val x = (pack[i].x as Double) - ax
            val y = (pack[i].y as Double) - ay
            val z = (pack[i].z as Double) - az
            repackOffset[mi * 3] = x - n.x
            repackOffset[mi * 3 + 1] = y - n.y
            repackOffset[mi * 3 + 2] = z - n.z
            fit = max(fit, sqrt(x * x + y * y + z * z) + n.r)
        }
        fitRadius = fit + FIT_MARGIN
    }

    private companion object {
        const val FIT_MARGIN = 16.0
    }
}

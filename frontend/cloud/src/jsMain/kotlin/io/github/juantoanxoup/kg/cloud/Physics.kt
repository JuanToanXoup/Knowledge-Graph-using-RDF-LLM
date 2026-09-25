package io.github.juantoanxoup.kg.cloud

import io.github.juantoanxoup.kg.cloud.externals.Simulation
import io.github.juantoanxoup.kg.cloud.externals.forceCollide
import io.github.juantoanxoup.kg.cloud.externals.forceManyBody
import io.github.juantoanxoup.kg.cloud.externals.forceSimulation
import io.github.juantoanxoup.kg.cloud.externals.forceX
import io.github.juantoanxoup.kg.cloud.externals.forceY
import io.github.juantoanxoup.kg.cloud.externals.forceZ
import kotlin.js.json
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// term-graph's Graph.tsx physics, ported verbatim: charge -55, collide r+18 (4 iterations), X/Y/Z centering at
// 0.06, a custom position-space link spring (rest r1 + r2 + 40, stiffness 0.08), settled with 180 ticks.

/** A node with its rest position, live (animated) position and per-frame state. */
class SimNode(
    val node: TermNode,
    val index: Int,
    val r: Double,
) {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var px = 0.0
    var py = 0.0
    var pz = 0.0
    var reveal = 0.0
    var dimEase = 1.0
}

/** Per-node motion parameters, three per axis where noted (term-graph's `Motion`). */
class Motion(
    n: Int,
) {
    val amp = DoubleArray(3 * n)
    val freq = DoubleArray(3 * n)
    val phase = DoubleArray(3 * n)
    val jit = DoubleArray(3 * n)
    val off = DoubleArray(3 * n)
    val vel = DoubleArray(3 * n)
    val stiff = DoubleArray(n)
    val pull = DoubleArray(n)
    val delay = DoubleArray(n)
    val pulse = DoubleArray(n)
}

/** The settled layout plus everything the layers derive from it once per dataset. */
class Cloud(
    val nodes: List<SimNode>,
    /** Link endpoints as node indices, two per link. */
    val pairs: IntArray,
    val cloudRadius: Double,
    /** Curved-edge control offsets, three per link: bulge = 0.16 x length, radially outward. */
    val controlOffsets: DoubleArray,
    val motion: Motion,
    val neighborSets: List<Set<Int>>,
    /** Link indices touching each node. */
    val linksByNode: List<List<Int>>,
) {
    val linkCount: Int get() = pairs.size / 2
}

/** Deterministic per-node pseudo-random, like the original's seeded lookup. */
fun rand(k: Double): Double {
    val s = sin(k * 12.9898) * 43758.5453
    return s - floor(s)
}

fun easeOutExpo(e: Double): Double = if (e >= 1) 1.0 else 1 - 2.0.pow(-10 * e)

fun smoothstep(
    edge0: Double,
    edge1: Double,
    x: Double,
): Double {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private const val CHARGE = -55.0
private const val COLLIDE_PADDING = 18.0
private const val COLLIDE_ITERATIONS = 4
private const val CENTERING = 0.06
private const val LINK_REST_PADDING = 40.0
private const val LINK_STIFFNESS = 0.08
private const val SETTLE_TICKS = 180
private const val CONTROL_BULGE = 0.16

/** The original's link force: a plain spring applied symmetrically to both endpoints. */
private fun linkSpring(
    pairs: IntArray,
    nodes: Array<dynamic>,
): dynamic {
    val force: dynamic = { _: Double ->
        var i = 0
        while (i < pairs.size) {
            val a = nodes[pairs[i]]
            val b = nodes[pairs[i + 1]]
            val dx = (b.x as Double) - (a.x as Double)
            val dy = (b.y as Double) - (a.y as Double)
            val dz = (b.z as Double) - (a.z as Double)
            val dist = sqrt(dx * dx + dy * dy + dz * dz).takeIf { it > 0 } ?: 1.0
            val f = ((dist - ((a.r as Double) + (b.r as Double) + LINK_REST_PADDING)) / dist) * LINK_STIFFNESS
            a.x = (a.x as Double) + dx * f
            a.y = (a.y as Double) + dy * f
            a.z = (a.z as Double) + dz * f
            b.x = (b.x as Double) - dx * f
            b.y = (b.y as Double) - dy * f
            b.z = (b.z as Double) - dz * f
            i += 2
        }
    }
    force.initialize = { _: dynamic, _: dynamic -> }
    return force
}

/** Runs the shared physics to rest over [pack] (plain `{r}` objects; gains `x`, `y`, `z`). */
internal fun settle(
    pack: Array<dynamic>,
    pairs: IntArray,
) {
    val sim: Simulation =
        forceSimulation(pack, 3)
            .force("charge", forceManyBody().strength(CHARGE))
            .force(
                "collide",
                forceCollide({ d: dynamic -> (d.r as Double) + COLLIDE_PADDING }).iterations(COLLIDE_ITERATIONS),
            ).force("x", forceX(0.0).strength(CENTERING))
            .force("y", forceY(0.0).strength(CENTERING))
            .force("z", forceZ(0.0).strength(CENTERING))
    if (pairs.isNotEmpty()) sim.force("link", linkSpring(pairs, pack))
    sim.stop()
    sim.tick(SETTLE_TICKS)
}

fun buildCloud(
    data: GraphData,
    degrees: Map<String, Int>,
): Cloud {
    val nodes = data.nodes.mapIndexed { i, n -> SimNode(n, i, radiusOf(n, degrees[n.id] ?: 0)) }
    val indexOf = nodes.associate { it.node.id to it.index }
    val pairs =
        data.links
            .flatMap { l -> listOfNotNull(indexOf[l.source], indexOf[l.target]).takeIf { it.size == 2 } ?: emptyList() }
            .toIntArray()

    val pack: Array<dynamic> = nodes.map { json("r" to it.r) }.toTypedArray()
    settle(pack, pairs)
    for (n in nodes) {
        val p = pack[n.index]
        n.x = p.x as Double
        n.y = p.y as Double
        n.z = p.z as Double
    }

    // Re-center the settled cloud on the origin and measure it.
    val count = max(1, nodes.size)
    val cx = nodes.sumOf { it.x } / count
    val cy = nodes.sumOf { it.y } / count
    val cz = nodes.sumOf { it.z } / count
    var cloudRadius = 0.0
    for (n in nodes) {
        n.x -= cx
        n.y -= cy
        n.z -= cz
        cloudRadius = max(cloudRadius, sqrt(n.x * n.x + n.y * n.y + n.z * n.z) + n.r)
    }

    val controlOffsets = DoubleArray(pairs.size / 2 * 3)
    for (li in 0 until pairs.size / 2) {
        val a = nodes[pairs[li * 2]]
        val b = nodes[pairs[li * 2 + 1]]
        val mx = (a.x + b.x) / 2
        val my = (a.y + b.y) / 2
        val mz = (a.z + b.z) / 2
        val len = sqrt((b.x - a.x).pow(2) + (b.y - a.y).pow(2) + (b.z - a.z).pow(2))
        val ml = sqrt(mx * mx + my * my + mz * mz)
        val bulge = len * CONTROL_BULGE
        if (ml < 1) {
            controlOffsets[li * 3 + 1] = bulge
        } else {
            controlOffsets[li * 3] = mx / ml * bulge
            controlOffsets[li * 3 + 1] = my / ml * bulge
            controlOffsets[li * 3 + 2] = mz / ml * bulge
        }
    }

    // Per-node motion parameters, the original's distributions verbatim.
    val motion = Motion(nodes.size)
    for (i in nodes.indices) {
        val base = 6.283 * rand(i * 1.37)
        for (a in 0 until 3) {
            val k = 3 * i + a
            motion.phase[k] = base + 6.283 * rand(k.toDouble())
            motion.amp[k] = 3 * (0.45 + 1.1 * rand(k + 7.1))
            motion.freq[k] = 0.19 * (0.6 + 1.1 * rand(k + 13.3))
            motion.jit[k] = (rand(k + 21.1) - 0.5) * 8
        }
        motion.stiff[i] = 42 * (0.45 + 1.4 * rand(i + 0.5))
        motion.pull[i] = 0.24 * (0.55 + 0.95 * rand(i + 4.2))
        val n = nodes[i]
        motion.delay[i] = if (cloudRadius > 0) sqrt(n.x * n.x + n.y * n.y + n.z * n.z) / cloudRadius * 0.9 else 0.0
        motion.pulse[i] = 6.283 * rand(i + 9.9)
    }

    val neighborSets = List(nodes.size) { LinkedHashSet<Int>() }
    val linksByNode = List(nodes.size) { ArrayList<Int>() }
    for (li in 0 until pairs.size / 2) {
        neighborSets[pairs[li * 2]].add(pairs[li * 2 + 1])
        neighborSets[pairs[li * 2 + 1]].add(pairs[li * 2])
        linksByNode[pairs[li * 2]].add(li)
        linksByNode[pairs[li * 2 + 1]].add(li)
    }
    return Cloud(nodes, pairs, cloudRadius, controlOffsets, motion, neighborSets, linksByNode)
}

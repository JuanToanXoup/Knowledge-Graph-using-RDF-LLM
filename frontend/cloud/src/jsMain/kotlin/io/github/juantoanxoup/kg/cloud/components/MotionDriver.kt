package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.easeOutExpo
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import org.khronos.webgl.set
import react.FC
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val PRIORITY = -10
private const val REVEAL_SECONDS = 1.5
private const val MAX_STEP = 0.05
private const val DIM_TAU = 0.18
private const val DAMPING = 17.0
private const val PULSE_AMOUNT = 0.012
private const val PULSE_RATE = 0.5

/**
 * term-graph's updateNodeMotion (priority -10 so every layer reads fresh positions): reveal, dual-sine wobble,
 * spring targets (the repack offsets during a search, otherwise the active node pulling its neighbours in),
 * visibility eased toward the search target, all written as live positions plus animated radii.
 */
val MotionDriver =
    FC<RuntimeProps> { props ->
        val runtime = props.runtime
        useFrame({ state, delta ->
            val cloud = runtime.cloud
            val motion = cloud.motion
            val r = state.clock.elapsedTime
            val start = runtime.startTime ?: r.also { runtime.startTime = it }
            val h = r - start
            val c = min(delta, MAX_STEP)
            val u = 1 - exp(-c / DIM_TAU)

            val hov = runtime.hovIdx
            if (hov != null && (hov >= cloud.nodes.size || cloud.nodes[hov].dimEase < 0.5)) runtime.hovIdx = null
            val activeIdx = runtime.activeIdx
            val active = activeIdx?.let { cloud.nodes[it] }
            val activeNeighbors = activeIdx?.let { cloud.neighborSets[it] }
            val repacking = runtime.repack.active

            for (i in cloud.nodes.indices) {
                val n = cloud.nodes[i]
                val v = easeOutExpo(((h - motion.delay[i]) / REVEAL_SECONDS).coerceIn(0.0, 1.0))
                n.reveal = v
                n.dimEase += (runtime.repack.matchTarget[i] - n.dimEase) * u

                val pulled = !repacking && active != null && activeNeighbors?.contains(i) == true
                for (a in 0 until 3) {
                    val k = 3 * i + a
                    val rest =
                        if (a == 0) {
                            n.x
                        } else if (a == 1) {
                            n.y
                        } else {
                            n.z
                        }
                    val target =
                        when {
                            repacking -> runtime.repack.repackOffset[k]
                            pulled -> {
                                val activeRest =
                                    if (a == 0) {
                                        active.x
                                    } else if (a == 1) {
                                        active.y
                                    } else {
                                        active.z
                                    }
                                (activeRest - rest) * motion.pull[i] + motion.jit[k]
                            }
                            else -> 0.0
                        }
                    motion.vel[k] += ((target - motion.off[k]) * motion.stiff[i] - DAMPING * motion.vel[k]) * c
                    motion.off[k] += motion.vel[k] * c

                    val wob =
                        motion.amp[k] *
                            (
                                0.7 * sin(r * motion.freq[k] + motion.phase[k]) +
                                    0.3 * sin(r * motion.freq[k] * 2.3 + 1.7 * motion.phase[k])
                            )
                    val p = rest * v + wob * v + motion.off[k]
                    when (a) {
                        0 -> n.px = p
                        1 -> n.py = p
                        else -> n.pz = p
                    }
                }
                // xyz = live position, w = animated radius (reveal, pulse, search fade).
                val pulse = 1 + PULSE_AMOUNT * sin(PULSE_RATE * r + motion.pulse[i])
                val tex = runtime.nodeTexData
                tex[i * 4] = n.px.toFloat()
                tex[i * 4 + 1] = n.py.toFloat()
                tex[i * 4 + 2] = n.pz.toFloat()
                tex[i * 4 + 3] = max(1e-4, n.r * v * pulse * n.dimEase).toFloat()
            }
            runtime.nodePosTexture.needsUpdate = true
        }, PRIORITY)
    }

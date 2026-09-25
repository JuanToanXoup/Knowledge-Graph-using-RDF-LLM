package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.Palette
import io.github.juantoanxoup.kg.cloud.externals.DoubleSide
import io.github.juantoanxoup.kg.cloud.externals.Mesh
import io.github.juantoanxoup.kg.cloud.externals.MeshBasicMaterial
import io.github.juantoanxoup.kg.cloud.externals.RingGeometry
import io.github.juantoanxoup.kg.cloud.externals.primitive
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import react.FC
import react.useMemo
import kotlin.js.json
import kotlin.math.exp
import kotlin.math.max

private const val INNER = 1.32
private const val OUTER = 1.4
private const val SEGMENTS = 48
private const val RENDER_ORDER = 4
private const val TARGET_OPACITY = 0.95
private const val TAU_IN = 0.13
private const val TAU_OUT = 0.05

/** term-graph's FocusRing: one accent ring around the active node, eased in fast and out slower, tracking it. */
val FocusRing =
    FC<RuntimeProps> { props ->
        val runtime = props.runtime
        val ring =
            useMemo(runtime) {
                val material =
                    MeshBasicMaterial(
                        json(
                            "color" to Palette.ACCENT,
                            "transparent" to true,
                            "opacity" to 0,
                            "side" to DoubleSide,
                            "depthWrite" to false,
                        ),
                    )
                Ring(
                    Mesh(RingGeometry(INNER, OUTER, SEGMENTS), material).apply {
                        visible = false
                        renderOrder = RENDER_ORDER
                    },
                    material,
                )
            }

        useFrame({ state, delta ->
            val active = runtime.activeIdx
            if (active != null) ring.lastActive = active
            val target = if (active != null) TARGET_OPACITY else 0.0
            val tau = if (target > ring.opacity) TAU_IN else TAU_OUT
            ring.opacity += (target - ring.opacity) * (1 - exp(-delta / tau))
            if (ring.opacity < 0.003 && active == null) {
                ring.mesh.visible = false
                ring.lastActive = null
                return@useFrame
            }
            val idx = ring.lastActive
            val n = idx?.let { runtime.cloud.nodes.getOrNull(it) }
            if (n == null) {
                ring.mesh.visible = false
                return@useFrame
            }
            ring.mesh.visible = true
            ring.material.opacity = ring.opacity
            ring.mesh.position.set(n.px, n.py, n.pz)
            ring.mesh.scale.setScalar(n.r * max(1e-4, n.reveal))
            ring.mesh.quaternion.copy(state.camera.quaternion)
        })

        primitive { `object` = ring.mesh }
    }

private class Ring(
    val mesh: Mesh,
    val material: MeshBasicMaterial,
) {
    var opacity = 0.0
    var lastActive: Int? = null
}

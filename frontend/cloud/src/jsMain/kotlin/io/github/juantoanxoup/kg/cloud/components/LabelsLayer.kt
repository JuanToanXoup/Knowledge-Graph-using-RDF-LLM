package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.Palette
import io.github.juantoanxoup.kg.cloud.externals.Object3D
import io.github.juantoanxoup.kg.cloud.externals.Text
import io.github.juantoanxoup.kg.cloud.externals.group
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import io.github.juantoanxoup.kg.cloud.smoothstep
import react.FC
import react.Key
import react.RefCallback
import react.useMemo
import kotlin.math.pow
import kotlin.math.sqrt

external interface LabelsLayerProps : RuntimeProps {
    /** URL of the label font (term-graph uses JetBrains Mono Medium); null falls back to drei's default. */
    var labelFont: String?
}

private const val LABEL_LIMIT = 150
private const val FADE_NEAR_OFFSET = 35.0
private const val FADE_FAR_OFFSET = 200.0
private const val FADE_STRENGTH = 0.95
private const val SEARCH_CUT = 0.78
private const val SEARCH_NEAR = 90.0
private const val REVEAL_SECONDS = 2.4
private const val LABEL_GAP = 3.0
private const val DIMMED_OPACITY = 0.05
private const val DIMMED_SCALE = 0.84
private const val SDF_GLYPH_SIZE = 128
private const val RENDER_ORDER = 10

private class Label(
    val i: Int,
    val fontSize: Double,
    val baseOpacity: Double,
)

/**
 * term-graph's LabelsLayer: SDF text above each node (offset r + 3), billboarded to the camera each frame;
 * size and base opacity grow with the node's radius; the active label is accent at full ink, non-neighbours
 * drop to 5%, shrink and sink a line while a term is focused; all ride the depth fade and the intro reveal.
 */
val LabelsLayer =
    FC<LabelsLayerProps> { props ->
        val runtime = props.runtime
        val cloud = runtime.cloud

        val labels =
            useMemo(runtime) {
                var indices = cloud.nodes.indices.toList()
                if (indices.size > LABEL_LIMIT) {
                    indices = indices.sortedByDescending { cloud.nodes[it].r }.take(LABEL_LIMIT)
                }
                indices.map { i ->
                    val t = ((cloud.nodes[i].r - 2.2) / 6.5).coerceIn(0.0, 1.0)
                    Label(i, 2.1 + t * 2, 0.3 + t * 0.5)
                }
            }
        val refs = useMemo(labels) { LabelRefs(labels.size) }

        useFrame({ state, delta ->
            val cam = state.camera
            val camDist = cam.position.length()
            val w = 1 - 0.0009.pow(delta)
            refs.searchBlend += ((if (runtime.searchActive) 1.0 else 0.0) - refs.searchBlend) * w
            val blend = refs.searchBlend
            val near = camDist - (FADE_NEAR_OFFSET + SEARCH_NEAR * blend)
            val far = camDist + FADE_FAR_OFFSET
            val strength = FADE_STRENGTH * (1 - SEARCH_CUT * blend)
            val hi = runtime.hovIdx
            val selIdx = runtime.selIdx
            val active = selIdx ?: hi
            val elapsed = runtime.startTime?.let { state.clock.elapsedTime - it } ?: 0.0
            val reveal = smoothstep(0.62, 1.0, (elapsed / REVEAL_SECONDS).coerceAtMost(1.0))
            val neighbours = selIdx?.let { cloud.neighborSets[it] }

            for (li in labels.indices) {
                val label = labels[li]
                val n = cloud.nodes[label.i]
                val x = n.px
                val y = n.py + n.r + LABEL_GAP
                val z = n.pz
                val g = refs.groups[li]
                if (g != null) {
                    g.position.set(x, y, z)
                    g.quaternion.copy(cam.quaternion)
                }
                val text = refs.texts[li] ?: continue
                if (!refs.prepared[li]) {
                    text.material.depthWrite = false
                    refs.prepared[li] = true
                }
                val dx = x - cam.position.x
                val dy = y - cam.position.y
                val dz = z - cam.position.z
                val depthFade = 1 - smoothstep(near, far, sqrt(dx * dx + dy * dy + dz * dz)) * strength

                val isActive = label.i == active
                val hoverRestore = selIdx != null && !isActive && label.i == hi
                refs.hoverEase[li] += ((if (hoverRestore) 1.0 else 0.0) - refs.hoverEase[li]) * w
                val eased = refs.hoverEase[li]
                val dimmed = selIdx != null && !isActive && neighbours?.contains(label.i) != true

                var base = label.baseOpacity * depthFade
                if (dimmed) base *= DIMMED_OPACITY
                val opacity = (if (isActive) 1.0 else base + (1 - base) * eased) * reveal * n.dimEase
                text.fillOpacity = opacity
                text.outlineOpacity = opacity
                text.color = if (isActive) Palette.ACCENT else Palette.INK
                if (g != null) {
                    g.scale.setScalar(if (dimmed) DIMMED_SCALE + eased * (1 - DIMMED_SCALE) else 1.0)
                    if (dimmed) g.position.y = y - (1 - eased) * label.fontSize
                }
            }
        })

        for ((li, label) in labels.withIndex()) {
            group {
                key = Key(cloud.nodes[label.i].node.id)
                ref = RefCallback<Object3D> { refs.groups[li] = it }
                Text {
                    ref = RefCallback<Any> { refs.texts[li] = it.asDynamic() }
                    renderOrder = RENDER_ORDER
                    font = props.labelFont
                    fontSize = label.fontSize
                    sdfGlyphSize = SDF_GLYPH_SIZE
                    color = Palette.INK
                    anchorX = "center"
                    anchorY = "bottom"
                    letterSpacing = 0.0
                    fillOpacity = label.baseOpacity
                    outlineColor = Palette.PAPER
                    outlineWidth = "7%"
                    outlineOpacity = label.baseOpacity
                    raycast = { _: dynamic, _: dynamic -> null }
                    +cloud.nodes[label.i]
                        .node.label
                        .uppercase()
                }
            }
        }
    }

private class LabelRefs(
    size: Int,
) {
    val groups = arrayOfNulls<Object3D>(size)
    val texts = arrayOfNulls<dynamic>(size)
    val prepared = BooleanArray(size)
    val hoverEase = DoubleArray(size)
    var searchBlend = 0.0
}

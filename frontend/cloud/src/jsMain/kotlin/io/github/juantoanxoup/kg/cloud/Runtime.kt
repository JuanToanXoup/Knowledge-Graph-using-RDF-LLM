package io.github.juantoanxoup.kg.cloud

import io.github.juantoanxoup.kg.cloud.externals.DataTexture
import io.github.juantoanxoup.kg.cloud.externals.FloatType
import io.github.juantoanxoup.kg.cloud.externals.NearestFilter
import io.github.juantoanxoup.kg.cloud.externals.RGBAFormat
import org.khronos.webgl.Float32Array
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Mutable per-frame state shared by the scene layers (what term-graph keeps in refs): the active node,
 * search state, the intro clock, and the node data texture the motion driver writes and the shaders read
 * (xyz = live position, w = animated radius).
 */
class CloudRuntime(
    val cloud: Cloud,
) {
    val repack = Repack(cloud)
    var selIdx: Int? = null
    var hovIdx: Int? = null
    var searchActive = false
    var queryActive = false

    /** Scene clock time at which the current dataset was first drawn; null until the first frame. */
    var startTime: Double? = null

    val texWidth: Int = ceil(sqrt(cloud.nodes.size.toDouble())).toInt().coerceAtLeast(1)
    val texHeight: Int = ceil(cloud.nodes.size.toDouble() / texWidth).toInt().coerceAtLeast(1)
    val nodeTexData: Float32Array = Float32Array(texWidth * texHeight * 4)
    val nodePosTexture: DataTexture =
        DataTexture(nodeTexData, texWidth, texHeight, RGBAFormat, FloatType).apply {
            minFilter = NearestFilter
            magFilter = NearestFilter
            needsUpdate = true
        }

    /** Focused node, else hovered node. */
    val activeIdx: Int? get() = selIdx ?: hovIdx

    fun visible(i: Int): Boolean {
        val n = cloud.nodes[i]
        return n.dimEase >= 0.5 && n.reveal >= 0.5
    }
}

package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.Cloud
import io.github.juantoanxoup.kg.cloud.externals.BufferAttribute
import io.github.juantoanxoup.kg.cloud.externals.BufferGeometry
import io.github.juantoanxoup.kg.cloud.externals.Color
import io.github.juantoanxoup.kg.cloud.externals.Points
import io.github.juantoanxoup.kg.cloud.externals.ShaderMaterial
import io.github.juantoanxoup.kg.cloud.externals.Sphere
import io.github.juantoanxoup.kg.cloud.externals.Vector3
import io.github.juantoanxoup.kg.cloud.externals.primitive
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import kotlinx.coroutines.awaitCancellation
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import react.FC
import react.useEffect
import react.useMemo
import kotlin.js.json
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 320
private const val RENDER_ORDER = 3

// Ported verbatim from the original's EdgeParticles point shaders.
private const val PARTICLES_VERTEX_SHADER = """
  uniform float uPixelRatio;
  attribute float aSize;
  attribute float aBright;
  varying float vBright;
  void main() {
    vBright = aBright;
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = aSize * uPixelRatio * (300.0 / -mv.z);
  }
"""
private const val PARTICLES_FRAGMENT_SHADER = """
  precision highp float;
  uniform vec3 uColor;
  varying float vBright;
  void main() {
    if (vBright <= 0.001) discard;
    vec2 uv = gl_PointCoord - 0.5;
    if (length(uv) > 0.5) discard;
    float a = smoothstep(0.5, 0.32, length(uv)) * vBright;
    gl_FragColor = vec4(uColor, a);
  }
"""

private class Particle {
    var edge = 0
    var t = 0.0
    var speed = 0.0
    var reverse = false
    var on = false
}

/** term-graph's `activeRef` / `searchRef`. */
private class Tracked {
    var active: Int? = null
    var search = false
}

private class ParticleBuffers(
    val points: Points,
    val geometry: BufferGeometry,
    val material: ShaderMaterial,
    val pos: Float32Array,
    val size: Float32Array,
    val bright: Float32Array,
    val positionAttr: BufferAttribute,
    val sizeAttr: BufferAttribute,
    val brightAttr: BufferAttribute,
)

private fun evalEdge(
    cloud: Cloud,
    li: Int,
    t: Double,
    collapse: Double,
    out: DoubleArray,
) {
    val a = cloud.nodes[cloud.pairs[li * 2]]
    val b = cloud.nodes[cloud.pairs[li * 2 + 1]]
    val off = cloud.controlOffsets
    val mx = (a.px + b.px) / 2 + off[li * 3] * collapse
    val my = (a.py + b.py) / 2 + off[li * 3 + 1] * collapse
    val mz = (a.pz + b.pz) / 2 + off[li * 3 + 2] * collapse
    val m = 1 - t
    out[0] = m * m * a.px + m * 2 * t * mx + t * t * b.px
    out[1] = m * m * a.py + m * 2 * t * my + t * t * b.py
    out[2] = m * m * a.pz + m * 2 * t * mz + t * t * b.pz
}

/** A pool of accent dots flowing along the hovered/focused node's curved edges. */
val EdgeParticles =
    FC<RuntimeProps> { props ->
        val runtime = props.runtime
        val tracked = useMemo { Tracked() }
        val pool = useMemo(runtime) { Array(PARTICLE_COUNT) { Particle() } }

        val buffers =
            useMemo {
                val pos = Float32Array(PARTICLE_COUNT * 3)
                val size = Float32Array(PARTICLE_COUNT)
                size.asDynamic().fill(5)
                val bright = Float32Array(PARTICLE_COUNT)
                val geometry = BufferGeometry()
                val positionAttr = BufferAttribute(pos, 3)
                val sizeAttr = BufferAttribute(size, 1)
                val brightAttr = BufferAttribute(bright, 1)
                geometry.setAttribute("position", positionAttr)
                geometry.setAttribute("aSize", sizeAttr)
                geometry.setAttribute("aBright", brightAttr)
                geometry.boundingSphere = Sphere(Vector3(), 1e6)
                val material =
                    ShaderMaterial(
                        json(
                            "uniforms" to
                                json(
                                    "uPixelRatio" to json("value" to 1),
                                    "uColor" to json("value" to Color("#0a0a0a")),
                                ),
                            "vertexShader" to PARTICLES_VERTEX_SHADER,
                            "fragmentShader" to PARTICLES_FRAGMENT_SHADER,
                            "transparent" to true,
                            "depthWrite" to false,
                        ),
                    )
                val points =
                    Points(geometry, material).apply {
                        frustumCulled = false
                        renderOrder = RENDER_ORDER
                    }
                ParticleBuffers(points, geometry, material, pos, size, bright, positionAttr, sizeAttr, brightAttr)
            }
        useEffect(buffers) {
            try {
                awaitCancellation()
            } finally {
                buffers.geometry.dispose()
                buffers.material.dispose()
            }
        }
        // A new dataset invalidates every pooled edge index — park the whole pool,
        // it repopulates on the next hover/focus.
        useEffect(runtime, buffers) {
            tracked.active = null
            for (p in pool) p.on = false
            buffers.bright.asDynamic().fill(0)
        }

        val scratch = useMemo { DoubleArray(3) }

        useFrame({ state, delta ->
            val cloud = runtime.cloud
            val nodes = cloud.nodes
            val pairs = cloud.pairs
            val size = buffers.size
            val bright = buffers.bright
            val pos = buffers.pos
            val c = min(delta, 0.05)
            val activeIdx = runtime.activeIdx
            val searchActive = runtime.searchActive

            if (activeIdx != tracked.active || searchActive != tracked.search) {
                var edges = if (activeIdx != null) cloud.linksByNode[activeIdx] else emptyList()
                if (edges.isNotEmpty() && searchActive && activeIdx != null) {
                    edges =
                        edges.filter { li ->
                            val s = pairs[li * 2]
                            val t = pairs[li * 2 + 1]
                            val other = if (s == activeIdx) t else s
                            nodes[other].dimEase > 0.5
                        }
                }
                val want = if (edges.isNotEmpty()) min(PARTICLE_COUNT, edges.size * 8) else 0
                for (p in 0 until PARTICLE_COUNT) {
                    val part = pool[p]
                    if (p < want) {
                        val li = edges[p % edges.size]
                        part.edge = li
                        part.reverse = pairs[li * 2] != activeIdx
                        part.t = Random.nextDouble()
                        part.speed = 0.1 + Random.nextDouble() * 0.1
                        part.on = true
                        size[p] = (2.4 + Random.nextDouble() * 1.4).toFloat()
                    } else {
                        part.on = false
                        bright[p] = 0f
                    }
                }
                buffers.sizeAttr.needsUpdate = true
                tracked.active = activeIdx
                tracked.search = searchActive
            }

            // Straight paths while searching, and as the map morphs in. The cloud view has no map, so the
            // original's `(1 - mapBlend)` factor is always 1.
            val collapse = if (searchActive) 0.0 else 1.0
            for (p in 0 until PARTICLE_COUNT) {
                val part = pool[p]
                // The edge bound also covers the frame between a dataset swap and the
                // pool-reset effect, when part.edge may index the previous dataset.
                if (!part.on || part.edge >= cloud.linkCount) {
                    bright[p] = 0f
                    continue
                }
                part.t += part.speed * c
                if (part.t >= 1) part.t = 0.0
                evalEdge(cloud, part.edge, if (part.reverse) 1 - part.t else part.t, collapse, scratch)
                pos[p * 3] = scratch[0].toFloat()
                pos[p * 3 + 1] = scratch[1].toFloat()
                pos[p * 3 + 2] = scratch[2].toFloat()
                bright[p] = sin(part.t * PI).toFloat()
            }
            buffers.positionAttr.needsUpdate = true
            buffers.brightAttr.needsUpdate = true
            val pixelRatio = buffers.material.uniforms.uPixelRatio
            if (pixelRatio != null) {
                pixelRatio.value = min((state.gl.getPixelRatio() as Number).toDouble(), 2.0)
            }
        })

        primitive { `object` = buffers.points }
    }

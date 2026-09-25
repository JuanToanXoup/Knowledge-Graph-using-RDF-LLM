package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.externals.Color
import io.github.juantoanxoup.kg.cloud.externals.InstancedBufferAttribute
import io.github.juantoanxoup.kg.cloud.externals.InstancedMesh
import io.github.juantoanxoup.kg.cloud.externals.PlaneGeometry
import io.github.juantoanxoup.kg.cloud.externals.ShaderMaterial
import io.github.juantoanxoup.kg.cloud.externals.Sphere
import io.github.juantoanxoup.kg.cloud.externals.Vector2
import io.github.juantoanxoup.kg.cloud.externals.Vector3
import io.github.juantoanxoup.kg.cloud.externals.primitive
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import io.github.juantoanxoup.kg.cloud.externals.useThree
import kotlinx.coroutines.awaitCancellation
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import react.FC
import react.useEffect
import react.useMemo
import web.animations.requestAnimationFrame
import web.dom.document
import kotlin.js.json
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.tan

private val NEUTRAL_INK = Color("#1a1a19")

// The original's node fade tuning (FADE_DEFAULTS).
private const val FADE_NEAR_OFFSET = 35.0
private const val FADE_FAR_OFFSET = 200.0
private const val FADE_STRENGTH_NODES = 0.82
private const val FADE_SEARCH_CUT = 0.78
private const val FADE_SEARCH_NEAR = 90.0

private const val RENDER_ORDER = 2
private const val CLICK_SLOP = 6.0

// Ported verbatim from the original's NodesLayer: instanced billboard quads,
// positions + animated radii from the data texture, flat discs with accent
// tint for the active state and paper-mix dimming/depth fades.
private const val NODES_VERTEX_SHADER = """
  uniform sampler2D uNodePos;
  uniform vec2 uTexSize;
  attribute float aIndex;
  attribute vec3 aColor;
  attribute float aState;
  attribute float aShape;
  varying vec3 vColor;
  varying float vState;
  varying float vDepth;
  varying vec2 vUv;
  varying float vShape;

  vec4 nodeTexel(float idx) {
    float x = mod(idx, uTexSize.x);
    float y = floor(idx / uTexSize.x);
    vec2 uv = (vec2(x, y) + 0.5) / uTexSize;
    return texture2D(uNodePos, uv);
  }

  void main() {
    vColor = aColor;
    vState = aState;
    vShape = aShape;
    vUv = position.xy; // plane corners in [-0.5, 0.5]

    vec4 nd = nodeTexel(aIndex);
    vec3 center = nd.xyz;
    float radius = nd.w;

    // billboard: offset the quad corners in VIEW space so it always faces camera
    vec4 centerView = modelViewMatrix * vec4(center, 1.0);
    centerView.xy += position.xy * radius * 2.0;
    vDepth = -centerView.z;
    gl_Position = projectionMatrix * centerView;
  }
"""

private const val NODES_FRAGMENT_SHADER = """
  precision highp float;
  uniform vec3 uAccent;
  uniform vec3 uPaper;
  uniform float uFadeNear;
  uniform float uFadeFar;
  uniform float uFadeStrength;
  uniform float uFocalDepth;
  varying vec3 vColor;
  varying float vState;
  varying float vDepth;
  varying vec2 vUv;
  varying float vShape;

  void main() {
    // flat shape, hard edge (MSAA handles AA), no shading:
    // shape 0 = circle (pages/terms), shape 1 = diamond (kind: "action")
    if (vShape > 0.5) {
      if (abs(vUv.x) + abs(vUv.y) > 0.5) discard;
    } else {
      if (length(vUv) > 0.5) discard;
    }

    vec3 col = vColor;
    float hot = clamp(vState - 1.0, 0.0, 1.0);
    col = mix(col, uAccent, hot);

    // depth-tint: front-half tone ramp so overlapping same-colored discs
    // separate. 0 at the near rim of the cloud → 1 by the focal plane; nearer
    // ink stays solid, ink behind lifts a hair toward paper.
    float sep = clamp(
      (vDepth - (uFocalDepth - 160.0)) / 200.0,
      0.0, 1.0
    );
    col = mix(col, uPaper, sep * 0.220);

    // always-on depth fade: far recedes into paper, front reads clearest
    float depthFade = 1.0 - smoothstep(uFadeNear, uFadeFar, vDepth) * uFadeStrength;
    float dim = clamp(vState, 0.0, 1.0);
    float fade = depthFade * mix(0.2, 1.0, dim);
    col = mix(uPaper, col, fade);

    gl_FragColor = vec4(col, 1.0);
  }
"""

external interface NodesLayerProps : RuntimeProps {
    var onSelect: (String?) -> Unit
}

/**
 * term-graph's NodesLayer: one instanced billboard quad per node, positioned and sized on the GPU from the
 * node data texture, with the original's state table (active, neighbours, rest) eased per frame and CPU
 * picking on the canvas for hover and click.
 */
val NodesLayer =
    FC<NodesLayerProps> { props ->
        val runtime = props.runtime
        val three = useThree()
        val gl = three.gl
        val camera = three.camera
        val pickVec = useMemo { Vector3() }
        val searchBlend = useMemo { SearchBlend() }

        val layer =
            useMemo(runtime) {
                val simNodes = runtime.cloud.nodes
                val n = simNodes.size
                val quadGeometry = PlaneGeometry(1.0, 1.0)
                val colors = Float32Array(n * 3)
                val indices = Float32Array(n)
                val shapes = Float32Array(n)
                for (i in 0 until n) {
                    colors[i * 3] = NEUTRAL_INK.r.toFloat()
                    colors[i * 3 + 1] = NEUTRAL_INK.g.toFloat()
                    colors[i * 3 + 2] = NEUTRAL_INK.b.toFloat()
                    indices[i] = i.toFloat()
                    shapes[i] = if (simNodes[i].node.kind == "action") 1f else 0f
                }
                quadGeometry.setAttribute("aColor", InstancedBufferAttribute(colors, 3))
                quadGeometry.setAttribute("aIndex", InstancedBufferAttribute(indices, 1))
                quadGeometry.setAttribute("aShape", InstancedBufferAttribute(shapes, 1))
                val stateValues = Float32Array(n).apply { asDynamic().fill(1) }
                val stateAttr = InstancedBufferAttribute(Float32Array(n).apply { asDynamic().fill(1) }, 1)
                quadGeometry.setAttribute("aState", stateAttr)
                // GPU-positioned: keep it always drawn.
                quadGeometry.boundingSphere = Sphere(Vector3(), 1e6)

                val material =
                    ShaderMaterial(
                        json(
                            "uniforms" to
                                json(
                                    "uNodePos" to json("value" to runtime.nodePosTexture),
                                    "uTexSize" to
                                        json(
                                            "value" to
                                                Vector2(runtime.texWidth.toDouble(), runtime.texHeight.toDouble()),
                                        ),
                                    "uAccent" to json("value" to Color("#0a0a0a")),
                                    "uPaper" to json("value" to Color("#f2f2f0")),
                                    "uFadeNear" to json("value" to 300),
                                    "uFadeFar" to json("value" to 560),
                                    "uFadeStrength" to json("value" to FADE_STRENGTH_NODES),
                                    "uFocalDepth" to json("value" to 600),
                                ),
                            "vertexShader" to NODES_VERTEX_SHADER,
                            "fragmentShader" to NODES_FRAGMENT_SHADER,
                        ),
                    )
                val mesh =
                    InstancedMesh(quadGeometry, material, n).apply {
                        frustumCulled = false
                        renderOrder = RENDER_ORDER
                    }
                // Positions come from the texture (instance matrices stay identity), so fiber's raycaster
                // would hit the wrong places; picking is done on the CPU below instead.
                mesh.asDynamic().raycast = { _: dynamic, _: dynamic -> }
                Layer(mesh, quadGeometry, material, stateAttr, stateValues)
            }
        useEffect(layer) {
            try {
                awaitCancellation()
            } finally {
                layer.geometry.dispose()
                layer.material.dispose()
            }
        }

        // The original's CPU picking: project each visible node, hit if the pointer
        // is within 1.3x its projected radius; a press that moved <= 6px is a click.
        val onSelect = props.onSelect
        useEffect(gl, camera, runtime, onSelect, pickVec) {
            val canvas: dynamic = gl.domElement
            val simNodes = runtime.cloud.nodes
            val pickAt = { clientX: Double, clientY: Double ->
                val rect: dynamic = canvas.getBoundingClientRect()
                val px = clientX - (rect.left as Double)
                val py = clientY - (rect.top as Double)
                val width = rect.width as Double
                val height = rect.height as Double
                val halfH = height / 2
                val fov = (camera.asDynamic().fov as? Double) ?: 50.0
                val tanHalfFov = tan((fov * 0.5 * PI) / 180)
                var best: Int? = null
                var bestDist = Double.POSITIVE_INFINITY
                for (i in simNodes.indices) {
                    val n = simNodes[i]
                    if (n.dimEase < 0.5 || n.reveal < 0.5) continue
                    pickVec.set(n.px, n.py, n.pz)
                    val worldDist = camera.position.distanceTo(pickVec)
                    pickVec.project(camera)
                    if (pickVec.z > 1) continue
                    val sx = (pickVec.x * 0.5 + 0.5) * width
                    val sy = (-(pickVec.y * 0.5) + 0.5) * height
                    val projRadius = (n.r * halfH) / (worldDist * tanHalfFov)
                    val d = hypot(px - sx, py - sy)
                    if (d < projRadius * 1.3 && d < bestDist) {
                        bestDist = d
                        best = i
                    }
                }
                best
            }
            var downX = 0.0
            var downY = 0.0
            var moveX = 0.0
            var moveY = 0.0
            var rafPending = false
            val onPointerMove = { e: dynamic ->
                moveX = e.clientX as Double
                moveY = e.clientY as Double
                if (!rafPending) {
                    rafPending = true
                    requestAnimationFrame {
                        rafPending = false
                        val i = pickAt(moveX, moveY)
                        document.body.style
                            .asDynamic()
                            .cursor = if (i != null) "pointer" else "auto"
                        runtime.hovIdx = i
                    }
                }
            }
            val onPointerDown = { e: dynamic ->
                downX = e.clientX as Double
                downY = e.clientY as Double
            }
            val onPointerUp = handler@{ e: dynamic ->
                if (hypot((e.clientX as Double) - downX, (e.clientY as Double) - downY) > CLICK_SLOP) return@handler
                val i = pickAt(e.clientX as Double, e.clientY as Double)
                onSelect(if (i != null) simNodes[i].node.id else null)
            }
            val onPointerLeave = { _: dynamic ->
                runtime.hovIdx = null
                document.body.style
                    .asDynamic()
                    .cursor = "auto"
            }
            canvas.addEventListener("pointermove", onPointerMove)
            canvas.addEventListener("pointerdown", onPointerDown)
            canvas.addEventListener("pointerup", onPointerUp)
            canvas.addEventListener("pointerleave", onPointerLeave)
            try {
                awaitCancellation()
            } finally {
                canvas.removeEventListener("pointermove", onPointerMove)
                canvas.removeEventListener("pointerdown", onPointerDown)
                canvas.removeEventListener("pointerup", onPointerUp)
                canvas.removeEventListener("pointerleave", onPointerLeave)
            }
        }

        useFrame({ state, delta ->
            val uniforms = layer.material.uniforms
            val camDistance = state.camera.position.length()
            val prev = searchBlend.value
            searchBlend.value = prev + ((if (runtime.searchActive) 1 else 0) - prev) * (1 - 0.0009.pow(delta))
            val blend = searchBlend.value
            uniforms.uFadeNear.value = camDistance - (FADE_NEAR_OFFSET + FADE_SEARCH_NEAR * blend)
            uniforms.uFadeFar.value = camDistance + FADE_FAR_OFFSET
            uniforms.uFadeStrength.value = FADE_STRENGTH_NODES * (1 - FADE_SEARCH_CUT * blend)
            uniforms.uFocalDepth.value = camDistance

            // The original's state table: active node 2, its neighbors 1.3, the rest
            // 0.035 while focused / 0.12 while merely hovered, 1 idle.
            val selIdx = runtime.selIdx
            val activeIdx = runtime.activeIdx
            val activeNeighbors = activeIdx?.let { runtime.cloud.neighborSets[it] }
            val riseRate = 1 - 0.16.pow(delta)
            val fallRate = 1 - 0.012.pow(delta)
            val arr = layer.stateAttr.array.unsafeCast<Float32Array>()
            val stateValues = layer.stateValues
            var changed = false
            for (i in runtime.cloud.nodes.indices) {
                var target = 1.0
                if (activeIdx != null) {
                    target =
                        if (i == activeIdx) {
                            2.0
                        } else if (activeNeighbors?.contains(i) == true) {
                            1.3
                        } else if (selIdx != null) {
                            0.035
                        } else {
                            0.12
                        }
                }
                val prevState = stateValues[i].toDouble()
                val next = prevState + (target - prevState) * (if (target > prevState) riseRate else fallRate)
                stateValues[i] = next.toFloat()
                // Skip the GPU upload once every value has settled (same epsilon guard
                // as EdgesLayer) — the attribute copy only moves on meaningful change.
                if (abs(next - arr[i]) > 0.0005) {
                    arr[i] = next.toFloat()
                    changed = true
                }
            }
            if (changed) layer.stateAttr.needsUpdate = true
        })

        primitive { `object` = layer.mesh }
    }

private class Layer(
    val mesh: InstancedMesh,
    val geometry: PlaneGeometry,
    val material: ShaderMaterial,
    val stateAttr: InstancedBufferAttribute,
    val stateValues: Float32Array,
)

private class SearchBlend {
    var value = 0.0
}

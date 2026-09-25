package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.CloudRuntime
import io.github.juantoanxoup.kg.cloud.Palette
import io.github.juantoanxoup.kg.cloud.SimNode
import io.github.juantoanxoup.kg.cloud.externals.BufferAttribute
import io.github.juantoanxoup.kg.cloud.externals.BufferGeometry
import io.github.juantoanxoup.kg.cloud.externals.Color
import io.github.juantoanxoup.kg.cloud.externals.DoubleSide
import io.github.juantoanxoup.kg.cloud.externals.Mesh
import io.github.juantoanxoup.kg.cloud.externals.ShaderMaterial
import io.github.juantoanxoup.kg.cloud.externals.Sphere
import io.github.juantoanxoup.kg.cloud.externals.Vector2
import io.github.juantoanxoup.kg.cloud.externals.Vector3
import io.github.juantoanxoup.kg.cloud.externals.primitive
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import kotlinx.coroutines.awaitCancellation
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import react.FC
import react.useEffect
import react.useMemo
import kotlin.js.json
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Ported verbatim from the original's EdgesLayer: curved triangle-strip
// ribbons evaluated on the GPU from a node-position data texture, with a
// damped travelling shockwave launched along the focused node's edges.
private const val EDGES_VERTEX_SHADER = """
  uniform sampler2D uNodePos;
  uniform vec2 uTexSize;
  uniform float uActiveIndex;
  uniform float uPulseTime;
  uniform float uLineWidth;
  uniform float uControlScale; // 1 normally; 0 during search → straight links
  const float PI = 3.14159265;
  const float PI2 = 6.2831853;
  attribute float aSrcIndex;
  attribute float aTgtIndex;
  attribute float aT;
  attribute vec3 aControlOffset;
  attribute vec3 aColor;
  attribute float aBright;
  attribute float aSide;  // ∓1 — which rim of the ribbon this vertex is
  attribute float aWidth; // per-edge thickness multiplier (0 = collapsed)
  varying vec3 vColor;
  varying float vBright;
  varying float vDepth;
  varying float vEdgeT;     // 0 = at the active node, 1 = far end
  varying float vConnected; // 1 if this edge touches the active node

  vec3 nodePos(float idx) {
    float x = mod(idx, uTexSize.x);
    float y = floor(idx / uTexSize.x);
    vec2 uv = (vec2(x, y) + 0.5) / uTexSize;
    return texture2D(uNodePos, uv).xyz;
  }

  void main() {
    vColor = aColor;
    vBright = aBright;
    // orient the bezier param so the wave always emanates from the active node,
    // whichever end of the edge it sits on
    bool srcActive = abs(aSrcIndex - uActiveIndex) < 0.5;
    bool tgtActive = abs(aTgtIndex - uActiveIndex) < 0.5;
    vConnected = (srcActive || tgtActive) ? 1.0 : 0.0;
    vEdgeT = srcActive ? aT : (tgtActive ? 1.0 - aT : aT);
    vec3 s = nodePos(aSrcIndex);
    vec3 t = nodePos(aTgtIndex);
    vec3 c = mix(s, t, 0.5) + aControlOffset * uControlScale;
    float u = aT;
    float mu = 1.0 - u;
    vec3 p = mu * mu * s + 2.0 * mu * u * c + u * u * t;
    vec4 mv = modelViewMatrix * vec4(p, 1.0);

    // ── shockwave ── physically vibrate the connected lines: a damped travelling
    // wave that launches from the active node and rings down over a few seconds.
    // Displace each point sideways (perpendicular to the line on screen) so the
    // whole web shivers like plucked strings. Pinned at both endpoints.
    vec3 tangent = 2.0 * mu * (c - s) + 2.0 * u * (t - c);
    vec3 tanView = (modelViewMatrix * vec4(tangent, 0.0)).xyz;
    vec2 perp = normalize(vec2(-tanView.y, tanView.x) + 1e-5);
    // stable per-edge random so each line shivers out of sync (both verts of an
    // edge share src/tgt indices → same seed → consistent along the whole line)
    float seed = fract(sin(aSrcIndex * 12.9898 + aTgtIndex * 78.233) * 43758.5453);
    float env = exp(-uPulseTime * (1.50000 + seed * 0.80000));
    float wf = uPulseTime * (2.60000 + seed * 1.20000);
    float front = 1.0 - smoothstep(wf, wf + 0.5, vEdgeT);
    float ends = sin(vEdgeT * PI);                        // 0 at both endpoints → pinned
    float freq = 0.80000 + seed * 0.80000;
    float wave = sin((vEdgeT * freq - uPulseTime * 0.60000 + seed) * PI2);
    float len = distance(s, t);
    float amp = vConnected * env * front * ends * wave * len * (0.02200 + seed * 0.01200);
    mv.xy += perp * amp;

    // ── ribbon width ── push the two rim verts apart along the SAME screen-space
    // perpendicular, so the stroke reads as constant thickness and always faces
    // the camera. World-unit width → distant edges thin out naturally (matches
    // the depth fade). aWidth=0 collapses overlapping-disc edges to nothing.
    mv.xy += perp * (aSide * 0.5 * uLineWidth * aWidth);

    vDepth = -mv.z;
    gl_Position = projectionMatrix * mv;
  }
"""

private const val EDGES_FRAGMENT_SHADER = """
  precision highp float;
  uniform float uFadeNear;
  uniform float uFadeFar;
  uniform float uFadeStrength;
  varying vec3 vColor;
  varying float vBright;
  varying float vDepth;
  void main() {
    // inky line on paper — alpha carries the brightness, NormalBlending.
    // far segments fade so the front web reads clearest.
    float depthFade = 1.0 - smoothstep(uFadeNear, uFadeFar, vDepth) * uFadeStrength;
    // square it → steeper than the nodes' fade, so mid/back lines drop out fast
    // and the web reads calm instead of a dense tangle across the whole ball
    depthFade *= depthFade;
    gl_FragColor = vec4(vColor, vBright * depthFade);
  }
"""

/** The original's fade tuning (`FADE_DEFAULTS`). */
object FadeDefaults {
    const val NEAR_OFFSET = 35.0
    const val FAR_OFFSET = 200.0
    const val STRENGTH_EDGES = 0.98
}

private val INK = Color(Palette.INK)
private const val SAMPLES = 16
private const val ARC_STEPS = 24
private const val INITIAL_BRIGHT = 0.14
private const val WIDTH_BASE = 0.75
private const val WIDTH_DEGREE_SPAN = 12.0
private const val WIDTH_DEGREE_GAIN = 0.45
private const val EASE_UP = 0.16
private const val EASE_DOWN = 0.012
private const val REVEAL_SECONDS = 2.4
private const val REVEAL_DELAY = 0.45
private const val REVEAL_SPAN = 0.55
private const val UPLOAD_EPSILON = 0.0005
private const val LINE_WIDTH = 0.15
private const val RENDER_ORDER = 1

/**
 * Per-link brightness table, the original's exact numbers: idle 0.08; while a
 * node is hovered/focused its edges 0.6, everything else 0.022; during search,
 * edges between visible nodes 0.28 (0.6 when touching the active node), edges
 * to filtered-out nodes 0. The map view raises the idle floor — straightened
 * process edges must stay readable or their direction arrows look detached —
 * while `whisper` (a cone-view link outside the spanning-tree skeleton) drops
 * it to a trace instead; hover, selection and search still light those links
 * fully. Shared with ArrowsLayer so every arrow tracks its own line.
 *
 * The cloud view has no map morph or cone skeleton, so [mapBlend] and [whisper] default to their
 * constant values there (0 and false).
 */
@Suppress("LongParameterList")
fun linkBrightness(
    src: SimNode,
    tgt: SimNode,
    srcIdx: Int,
    tgtIdx: Int,
    active: Int?,
    queryActive: Boolean,
    mapBlend: Double = 0.0,
    whisper: Boolean = false,
): Double {
    if (queryActive) {
        if (src.dimEase <= 0.5 || tgt.dimEase <= 0.5) return 0.0
        return if (active != null && (srcIdx == active || tgtIdx == active)) 0.6 else 0.28
    }
    if (active != null) return if (srcIdx == active || tgtIdx == active) 0.6 else 0.022
    return 0.08 + (if (whisper) -0.03 else 0.18) * mapBlend
}

/**
 * Arc-length parameterize the rest-pose bezier and trim it at both node
 * radii; 16 evenly spaced t values, or null when the curve is swallowed.
 */
@Suppress("LongParameterList")
private fun trimmedTs(
    s: DoubleArray,
    c: DoubleArray,
    t: DoubleArray,
    srcR: Double,
    tgtR: Double,
): DoubleArray? {
    val pts =
        Array(ARC_STEPS + 1) { i ->
            val u = i.toDouble() / ARC_STEPS
            val m = 1 - u
            DoubleArray(3) { k -> m * m * s[k] + m * 2 * u * c[k] + u * u * t[k] }
        }
    val cum = DoubleArray(ARC_STEPS + 1)
    for (i in 0 until ARC_STEPS) {
        val a = pts[i]
        val b = pts[i + 1]
        cum[i + 1] = cum[i] + hypot3(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    }
    val end = cum[ARC_STEPS] - tgtR
    if (end <= srcR) return null
    val toT = { d: Double ->
        var i = 0
        while (i < ARC_STEPS && cum[i + 1] < d) i++
        val span = (cum[i + 1] - cum[i]).takeIf { it != 0.0 && !it.isNaN() } ?: 1.0
        (i + (d - cum[i]) / span) / ARC_STEPS
    }
    return DoubleArray(SAMPLES) { i -> toT(srcR + ((end - srcR) * i) / (SAMPLES - 1)) }
}

/** `Math.hypot` for three components. */
private fun hypot3(
    x: Double,
    y: Double,
    z: Double,
): Double = sqrt(x * x + y * y + z * z)

/**
 * term-graph's EdgesLayer: one indexed mesh of camera-facing ribbons, sixteen samples per link, positioned in
 * the vertex shader from the node data texture, with per-link brightness eased on the CPU.
 */
val EdgesLayer =
    FC<RuntimeProps> { props ->
        val runtime = props.runtime
        val built = useMemo(runtime) { buildEdges(runtime) }
        useEffect(built) {
            try {
                awaitCancellation()
            } finally {
                built.geometry.dispose()
                built.material.dispose()
            }
        }

        useFrame({ state, delta ->
            val cloud = runtime.cloud
            val selectedIdx = runtime.selIdx
            // The original compares `undefined !== null` while nothing is selected, so it re-arms every frame.
            if (selectedIdx == null || selectedIdx != built.lastFocus) {
                built.lastFocus = selectedIdx
                built.focusAt = state.clock.elapsedTime
            }
            val uniforms = built.material.uniforms
            val camDist = state.camera.position.length()
            uniforms.uFadeNear.value = camDist - FadeDefaults.NEAR_OFFSET * 0.5
            uniforms.uFadeFar.value = camDist + FadeDefaults.FAR_OFFSET * 0.5
            uniforms.uPulseTime.value = state.clock.elapsedTime - built.focusAt
            uniforms.uActiveIndex.value = selectedIdx ?: -1
            // Straight links while searching, and as the map morphs in.
            uniforms.uControlScale.value = if (runtime.queryActive) 0 else 1

            val active = runtime.activeIdx
            val up = 1 - EASE_UP.pow(delta)
            val down = 1 - EASE_DOWN.pow(delta)
            val start = runtime.startTime
            val h = if (start == null) 0.0 else state.clock.elapsedTime - start
            val revealT = min(1.0, h / REVEAL_SECONDS)
            val y = if (revealT >= 1) 1.0 else max(0.0, min(1.0, (revealT - REVEAL_DELAY) / REVEAL_SPAN))
            val arr = built.bright
            var changed = revealT < 1
            for (e in 0 until cloud.linkCount) {
                val si = cloud.pairs[e * 2]
                val ti = cloud.pairs[e * 2 + 1]
                val target = linkBrightness(cloud.nodes[si], cloud.nodes[ti], si, ti, active, runtime.queryActive)
                val cur = built.brightCurrent[e].toDouble()
                val next = cur + (target - cur) * (if (target > cur) up else down)
                built.brightCurrent[e] = next.toFloat()
                // `* (linkBackbone[e] ? 1 : 0.6)` in the original: every link is backbone in the cloud view (the
                // dataset declares no backboneKinds), so the factor is always 1.
                val v = next * y
                val rangeStart = e * SAMPLES * 2
                if (abs(v - arr[rangeStart].toDouble()) > UPLOAD_EPSILON) changed = true
                val f = v.toFloat()
                for (k in rangeStart until rangeStart + SAMPLES * 2) arr[k] = f
            }
            if (changed) built.brightAttr.needsUpdate = true
        })

        primitive { `object` = built.mesh }
    }

private class EdgesBuilt(
    val mesh: Mesh,
    val geometry: BufferGeometry,
    val material: ShaderMaterial,
    val brightAttr: BufferAttribute,
    val bright: Float32Array,
    val brightCurrent: Float32Array,
) {
    var lastFocus: Int? = null
    var focusAt = 0.0
}

private fun buildEdges(runtime: CloudRuntime): EdgesBuilt {
    val cloud = runtime.cloud
    val links = cloud.linkCount
    val count = links * SAMPLES * 2
    val srcIdx = Float32Array(count)
    val tgtIdx = Float32Array(count)
    val tArr = Float32Array(count)
    val ctrl = Float32Array(count * 3)
    val color = Float32Array(count * 3)
    val side = Float32Array(count)
    val width = Float32Array(count)
    val bright = Float32Array(count)
    bright.asDynamic().fill(INITIAL_BRIGHT)
    val index = Uint32Array(links * (SAMPLES - 1) * 6)
    var vi = 0
    var ii = 0
    for (li in 0 until links) {
        val si = cloud.pairs[li * 2]
        val ti = cloud.pairs[li * 2 + 1]
        val a = cloud.nodes[si]
        val b = cloud.nodes[ti]
        val off =
            doubleArrayOf(
                cloud.controlOffsets[li * 3],
                cloud.controlOffsets[li * 3 + 1],
                cloud.controlOffsets[
                    li *
                        3 +
                        2,
                ],
            )
        val mid =
            doubleArrayOf(
                (a.x + b.x) / 2 + off[0],
                (a.y + b.y) / 2 + off[1],
                (a.z + b.z) / 2 + off[2],
            )
        val ts =
            trimmedTs(doubleArrayOf(a.x, a.y, a.z), mid, doubleArrayOf(b.x, b.y, b.z), a.r, b.r)
                ?: DoubleArray(SAMPLES)
        val collapsed = ts.all { it == 0.0 }
        val degMax = max(cloud.neighborSets[si].size, cloud.neighborSets[ti].size)
        val w = if (collapsed) 0.0 else WIDTH_BASE + min(1.0, degMax / WIDTH_DEGREE_SPAN) * WIDTH_DEGREE_GAIN
        val start = vi
        for (sIdx in 0 until SAMPLES) {
            for (rim in intArrayOf(-1, 1)) {
                srcIdx[vi] = si.toFloat()
                tgtIdx[vi] = ti.toFloat()
                tArr[vi] = ts[sIdx].toFloat()
                ctrl[vi * 3] = off[0].toFloat()
                ctrl[vi * 3 + 1] = off[1].toFloat()
                ctrl[vi * 3 + 2] = off[2].toFloat()
                color[vi * 3] = INK.r.toFloat()
                color[vi * 3 + 1] = INK.g.toFloat()
                color[vi * 3 + 2] = INK.b.toFloat()
                side[vi] = rim.toFloat()
                width[vi] = w.toFloat()
                vi++
            }
        }
        for (q in 0 until SAMPLES - 1) {
            val v0 = start + q * 2
            val v1 = v0 + 1
            val v2 = start + (q + 1) * 2
            val v3 = v2 + 1
            index.asDynamic()[ii++] = v0
            index.asDynamic()[ii++] = v2
            index.asDynamic()[ii++] = v1
            index.asDynamic()[ii++] = v1
            index.asDynamic()[ii++] = v2
            index.asDynamic()[ii++] = v3
        }
    }
    val geometry = BufferGeometry()
    geometry.setAttribute("position", BufferAttribute(Float32Array(count * 3), 3))
    geometry.setAttribute("aSrcIndex", BufferAttribute(srcIdx, 1))
    geometry.setAttribute("aTgtIndex", BufferAttribute(tgtIdx, 1))
    geometry.setAttribute("aT", BufferAttribute(tArr, 1))
    geometry.setAttribute("aControlOffset", BufferAttribute(ctrl, 3))
    geometry.setAttribute("aColor", BufferAttribute(color, 3))
    geometry.setAttribute("aSide", BufferAttribute(side, 1))
    geometry.setAttribute("aWidth", BufferAttribute(width, 1))
    val brightAttr = BufferAttribute(bright, 1)
    geometry.setAttribute("aBright", brightAttr)
    geometry.asDynamic().setIndex(BufferAttribute(index, 1))
    // GPU-positioned: keep it always drawn.
    geometry.boundingSphere = Sphere(Vector3(), 1e6)

    val material =
        ShaderMaterial(
            json(
                "uniforms" to
                    json(
                        "uNodePos" to json("value" to runtime.nodePosTexture),
                        "uTexSize" to
                            json("value" to Vector2(runtime.texWidth.toDouble(), runtime.texHeight.toDouble())),
                        "uFadeNear" to json("value" to 300),
                        "uFadeFar" to json("value" to 560),
                        "uFadeStrength" to json("value" to FadeDefaults.STRENGTH_EDGES),
                        "uActiveIndex" to json("value" to -1),
                        "uPulseTime" to json("value" to 0),
                        "uLineWidth" to json("value" to LINE_WIDTH),
                        "uControlScale" to json("value" to 1),
                    ),
                "vertexShader" to EDGES_VERTEX_SHADER,
                "fragmentShader" to EDGES_FRAGMENT_SHADER,
                "transparent" to true,
                "depthWrite" to false,
                "side" to DoubleSide,
            ),
        )
    val mesh =
        Mesh(geometry, material).apply {
            frustumCulled = false
            renderOrder = RENDER_ORDER
        }
    val brightCurrent = Float32Array(links)
    brightCurrent.asDynamic().fill(INITIAL_BRIGHT)
    return EdgesBuilt(mesh, geometry, material, brightAttr, bright, brightCurrent)
}

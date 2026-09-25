package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.CloudRuntime
import io.github.juantoanxoup.kg.cloud.ReleaseInertia
import io.github.juantoanxoup.kg.cloud.externals.Box3
import io.github.juantoanxoup.kg.cloud.externals.CameraControlsImpl
import io.github.juantoanxoup.kg.cloud.externals.Vector3
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import io.github.juantoanxoup.kg.cloud.externals.useThree
import kotlinx.coroutines.awaitCancellation
import react.FC
import react.Props
import react.useEffect
import react.useMemo
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

external interface CameraRigProps : Props {
    var runtime: CloudRuntime
    var selectedId: String?
    var searchActive: Boolean

    /** Bumped when the repacked search cluster changes, so the camera refits it. */
    var repackTick: Int
}

private val HALF_FOV = 25.0 * PI / 180
private const val HOME_X = 130.0
private const val HOME_Y = 100.0
private const val HOME_Z = 540.0
private const val BOUNDARY = 240.0
private const val HOLD_MS = 1100.0
private const val INTRO_AZIMUTH = 6.6
private const val INTRO_POLAR = 0.6
private const val INTRO_MS = 4200.0
private const val IDLE_MS = 500.0
private const val IDLE_RAMP_MS = 1600.0
private const val DRIFT_RATE = 0.045
private const val SMOOTH_SELECT = 0.5
private const val SMOOTH_IDLE = 0.1

private class RigState {
    var dragging = false
    var spinActive = false
    var spinAz = 0.0
    var spinPolar = 0.0
    var spinStart: Double? = null
    var lastInteraction = 0.0
    var holdUntil = 0.0
    var booted = false
    var prevSearchActive = false
    val inertia = ReleaseInertia()
}

private fun now(): Double = js("performance.now()") as Double

/**
 * term-graph's CameraRig for the cloud view: rotate-only controls inside a bounded box, the intro spin-in,
 * framing of the selected node radially outward (home when nothing is selected), a refit to the repacked
 * cluster while searching, release inertia after a drag, and the slow idle drift.
 */
val CameraRig =
    FC<CameraRigProps> { props ->
        val three = useThree()
        val controls = three.controls?.unsafeCast<CameraControlsImpl>()
        val size = three.size
        val runtime = props.runtime
        val rig = useMemo(runtime) { RigState() }

        val isPortrait = size.height >= size.width

        fun overviewDist(): Double {
            val halfH = atan((size.width / max(1.0, size.height)) * tan(HALF_FOV))
            return (runtime.cloud.cloudRadius / sin(min(HALF_FOV, halfH))).coerceIn(320.0, 1200.0) *
                (if (isPortrait) 0.52 else 0.85)
        }

        fun searchFitDistance(fitRadius: Double): Double {
            val pad = max(40.0, fitRadius) + 30
            val halfH = atan((size.width / max(1.0, size.height)) * tan(HALF_FOV))
            return (pad / sin(min(HALF_FOV, halfH))).coerceIn(120.0, 1200.0) * (if (isPortrait) 1.12 else 1.0)
        }

        fun setCameraRadius(
            c: CameraControlsImpl,
            radius: Double,
            animate: Boolean,
        ) {
            val p = c.getPosition(Vector3())
            val k = radius / (sqrt(p.x * p.x + p.y * p.y + p.z * p.z).takeIf { it > 0 } ?: 1.0)
            c.setLookAt(p.x * k, p.y * k, p.z * k, 0.0, 0.0, 0.0, animate)
        }

        // Effects are coroutines; cancellation on cleanup unwinds the `finally`.
        useEffect(controls) {
            val c = controls ?: return@useEffect
            c.mouseButtons.right = 0
            c.mouseButtons.middle = 0
            c.touches.two = 0
            c.touches.three = 0
            c.setBoundary(Box3(Vector3(-BOUNDARY, -BOUNDARY, -BOUNDARY), Vector3(BOUNDARY, BOUNDARY, BOUNDARY)))
            val start: (dynamic) -> Unit = { rig.dragging = true }
            val end: (dynamic) -> Unit = {
                rig.dragging = false
                rig.lastInteraction = now()
            }
            c.addEventListener("controlstart", start)
            c.addEventListener("controlend", end)
            try {
                awaitCancellation()
            } finally {
                c.removeEventListener("controlstart", start)
                c.removeEventListener("controlend", end)
            }
        }

        // Re-aim on selection: swing outward through the node, or home; mid-search, frame the cluster.
        useEffect(controls, props.selectedId, size.width, size.height, runtime) {
            val c = controls ?: return@useEffect
            val animate = rig.booted
            rig.booted = true
            c.smoothTime = SMOOTH_SELECT
            if (animate) {
                rig.holdUntil = now() + HOLD_MS
                rig.inertia.reset()
                c.normalizeRotations()
            }

            fun startIntroSpin() {
                rig.spinAz = c.azimuthAngle
                rig.spinPolar = c.polarAngle
                rig.spinStart = null
                rig.spinActive = true
                c.rotateTo(rig.spinAz - INTRO_AZIMUTH, rig.spinPolar + INTRO_POLAR, false)
            }
            val sel = runtime.selIdx?.let { runtime.cloud.nodes[it] }
            if (sel == null) {
                if (animate && props.searchActive) {
                    val fit = runtime.repack.fitRadius
                    if (fit > 0) setCameraRadius(c, searchFitDistance(fit), true)
                    return@useEffect
                }
                val dist = overviewDist()
                val len = sqrt(HOME_X * HOME_X + HOME_Y * HOME_Y + HOME_Z * HOME_Z)
                c.setLookAt(HOME_X / len * dist, HOME_Y / len * dist, HOME_Z / len * dist, 0.0, 0.0, 0.0, animate)
                if (!animate) startIntroSpin()
                return@useEffect
            }
            if (props.searchActive) {
                val rp = runtime.repack
                val sx = sel.x + rp.repackOffset[sel.index * 3]
                val sy = sel.y + rp.repackOffset[sel.index * 3 + 1]
                val sz = sel.z + rp.repackOffset[sel.index * 3 + 2]
                val dist = (125 + sel.r * 8) * (if (isPortrait) 1.12 else 1.0)
                val dir = Vector3(sx, sy, sz)
                if (dir.length() < 1) dir.set(0.3, 0.4, 1.0)
                dir.normalize().multiplyScalar(dist)
                c.setLookAt(sx + dir.x, sy + dir.y, sz + dir.z, sx, sy, sz, animate)
                return@useEffect
            }
            var dist = min(1100.0, (230 + 9 * sel.r) * (if (isPortrait) 1.05 else 1.0))
            dist *= 1 - 0.28 * (1 - min(1.0, sqrt(sel.x * sel.x + sel.y * sel.y + sel.z * sel.z) / 210))
            val dir = Vector3(sel.x, sel.y, sel.z)
            if (dir.length() < 1) dir.set(0.3, 0.4, 1.0)
            dir.normalize().multiplyScalar(dist)
            c.setLookAt(sel.x + dir.x, sel.y + dir.y, sel.z + dir.z, sel.x, sel.y, sel.z, animate)
            if (!animate) startIntroSpin()
        }

        // Repack tick: while nothing is focused, refit to the cluster as matches change; swing home when cleared.
        useEffect(props.repackTick, size.width, size.height) {
            val c = controls ?: return@useEffect
            if (props.selectedId != null) {
                rig.prevSearchActive = props.searchActive
                return@useEffect
            }
            if (!props.searchActive && !rig.prevSearchActive) return@useEffect
            rig.prevSearchActive = props.searchActive
            val dist = if (props.searchActive) searchFitDistance(runtime.repack.fitRadius) else overviewDist()
            c.smoothTime = SMOOTH_SELECT
            rig.holdUntil = now() + HOLD_MS
            setCameraRadius(c, dist, true)
        }

        useFrame({ _, dt ->
            val c = controls ?: return@useFrame
            val az = c.azimuthAngle
            val polar = c.polarAngle
            if (rig.spinActive) {
                if (rig.dragging) {
                    rig.spinActive = false
                } else {
                    val start = rig.spinStart ?: now().also { rig.spinStart = it }
                    val e = min(1.0, (now() - start) / INTRO_MS)
                    val t = if (e >= 1) 1.0 else 1 - 2.0.pow(-10 * e)
                    c.rotateTo(rig.spinAz - INTRO_AZIMUTH * (1 - t), rig.spinPolar + INTRO_POLAR * (1 - t), false)
                    rig.inertia.sync(c.azimuthAngle, c.polarAngle)
                    rig.lastInteraction = now()
                    if (e >= 1) rig.spinActive = false
                    return@useFrame
                }
            }
            // Measure the drag's angular velocity for release inertia.
            rig.inertia.track(az, polar, dt, rig.dragging)
            // Nothing ambient while focused, hovering a node, dragging, searching, or during a selection transition.
            if (props.selectedId != null ||
                runtime.hovIdx != null ||
                rig.dragging ||
                props.searchActive ||
                now() < rig.holdUntil
            ) {
                return@useFrame
            }
            c.smoothTime = SMOOTH_IDLE
            // Inertia: keep spinning after release until the velocity decays away.
            if (rig.inertia.active) {
                val (dAzimuth, dPolar) = rig.inertia.step(dt)
                c.rotate(dAzimuth, dPolar, true)
                rig.lastInteraction = now()
                return@useFrame
            }
            // Idle drift: after 500ms idle, ease a slow azimuth spin in over 1.6s.
            val k = ((now() - rig.lastInteraction - IDLE_MS) / IDLE_RAMP_MS).coerceIn(0.0, 1.0)
            if (k > 0) c.rotate(DRIFT_RATE * dt * (k * k * (3 - 2 * k)), 0.0, true)
        })
    }

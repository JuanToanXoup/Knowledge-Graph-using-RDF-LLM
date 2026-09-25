package io.github.juantoanxoup.kg.cloud

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

// term-graph's keyboard handling (App.tsx) and camera release inertia (Scene.tsx CameraRig), kept as pure state so
// the components stay thin and both behaviours can be unit tested.

/** What a key press asks of the view. */
enum class KeyAction { CLEAR_SELECTION, STEP_PREV, STEP_NEXT }

/**
 * term-graph's window `keydown` handler: Escape clears the focus; with a term focused, the arrow keys step through
 * the terms. Keys typed into a text field belong to the field.
 */
fun keyAction(
    key: String,
    inTextField: Boolean,
    hasSelection: Boolean,
): KeyAction? =
    when {
        inTextField -> null
        key == "Escape" -> KeyAction.CLEAR_SELECTION
        !hasSelection -> null
        key == "ArrowLeft" -> KeyAction.STEP_PREV
        key == "ArrowRight" -> KeyAction.STEP_NEXT
        else -> null
    }

/** An orbit rotation in radians. */
data class Rotation(
    val azimuth: Double,
    val polar: Double,
)

private const val MAX_RATE = 2.4
private const val MIN_STEP = 1.0 / 120
private const val REST_RATE = 0.05
private const val TAU_S = 0.6

/**
 * Release inertia for the orbit camera: while the user drags, the angular velocity is measured frame to frame
 * (clamped to ±2.4 rad/s); after release the camera keeps turning at that velocity, decaying with a 0.6 s time
 * constant until it drops under 0.05 rad/s.
 */
class ReleaseInertia {
    var azimuthRate = 0.0
        private set
    var polarRate = 0.0
        private set
    private var prevAzimuth = 0.0
    private var prevPolar = 0.0

    /** Whether enough velocity is left to keep the camera moving. */
    val active: Boolean
        get() = abs(azimuthRate) > REST_RATE || abs(polarRate) > REST_RATE

    /** Records this frame's camera angles; while [dragging], measures the velocity since the last frame. */
    fun track(
        azimuth: Double,
        polar: Double,
        dt: Double,
        dragging: Boolean,
    ) {
        if (dragging) {
            val step = max(MIN_STEP, dt)
            azimuthRate = ((azimuth - prevAzimuth) / step).coerceIn(-MAX_RATE, MAX_RATE)
            polarRate = ((polar - prevPolar) / step).coerceIn(-MAX_RATE, MAX_RATE)
        }
        prevAzimuth = azimuth
        prevPolar = polar
    }

    /** Records the camera angles without measuring, for frames where the camera was moved programmatically. */
    fun sync(
        azimuth: Double,
        polar: Double,
    ) {
        prevAzimuth = azimuth
        prevPolar = polar
    }

    /** The rotation to apply over [dt] seconds; decays the velocity afterwards. */
    fun step(dt: Double): Rotation {
        val rotation = Rotation(azimuthRate * dt, polarRate * dt)
        val decay = exp(-dt / TAU_S)
        azimuthRate *= decay
        polarRate *= decay
        return rotation
    }

    fun reset() {
        azimuthRate = 0.0
        polarRate = 0.0
    }
}

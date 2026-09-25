package io.github.juantoanxoup.kg.cloud

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionTest {
    @Test
    fun escape_clears_the_focus_with_or_without_a_selection() {
        assertEquals(KeyAction.CLEAR_SELECTION, keyAction("Escape", inTextField = false, hasSelection = true))
        assertEquals(KeyAction.CLEAR_SELECTION, keyAction("Escape", inTextField = false, hasSelection = false))
    }

    @Test
    fun arrows_step_only_while_a_term_is_focused() {
        assertEquals(KeyAction.STEP_PREV, keyAction("ArrowLeft", inTextField = false, hasSelection = true))
        assertEquals(KeyAction.STEP_NEXT, keyAction("ArrowRight", inTextField = false, hasSelection = true))
        assertNull(keyAction("ArrowLeft", inTextField = false, hasSelection = false))
        assertNull(keyAction("ArrowRight", inTextField = false, hasSelection = false))
        assertNull(keyAction("ArrowUp", inTextField = false, hasSelection = true))
        assertNull(keyAction("a", inTextField = false, hasSelection = true))
    }

    @Test
    fun keys_typed_into_a_text_field_are_left_to_the_field() {
        assertNull(keyAction("Escape", inTextField = true, hasSelection = true))
        assertNull(keyAction("ArrowLeft", inTextField = true, hasSelection = true))
    }

    @Test
    fun a_drag_measures_the_angular_velocity_and_clamps_it() {
        val inertia = ReleaseInertia()
        assertFalse(inertia.active)
        inertia.sync(0.0, 1.0)
        inertia.track(0.02, 1.01, 1.0 / 60, dragging = true)
        assertTrue(abs(inertia.azimuthRate - 1.2) < 1e-9)
        assertTrue(abs(inertia.polarRate - 0.6) < 1e-9)
        assertTrue(inertia.active)
        // A whole radian in one frame clamps to ±2.4 rad/s.
        inertia.track(-0.98, 1.01, 1.0 / 60, dragging = true)
        assertEquals(-2.4, inertia.azimuthRate)
        assertEquals(0.0, inertia.polarRate)
        // Frames shorter than 1/120 s measure as if they were 1/120 s.
        inertia.sync(0.0, 0.0)
        inertia.track(0.01, 0.0, 0.001, dragging = true)
        assertTrue(abs(inertia.azimuthRate - 1.2) < 1e-9)
    }

    @Test
    fun without_a_drag_the_angles_are_recorded_but_the_velocity_is_kept() {
        val inertia = ReleaseInertia()
        inertia.sync(0.0, 0.0)
        inertia.track(0.02, 0.0, 1.0 / 60, dragging = true)
        val measured = inertia.azimuthRate
        inertia.track(0.5, 0.5, 1.0 / 60, dragging = false)
        assertEquals(measured, inertia.azimuthRate)
        // The next drag frame measures from the recorded angles, not from the drag's last frame.
        inertia.track(0.5, 0.5, 1.0 / 60, dragging = true)
        assertEquals(0.0, inertia.azimuthRate)
    }

    @Test
    fun release_keeps_turning_and_decays_to_rest() {
        val inertia = ReleaseInertia()
        inertia.sync(0.0, 0.0)
        inertia.track(0.02, -0.01, 1.0 / 60, dragging = true)
        val dt = 1.0 / 60
        val (azimuth, polar) = inertia.step(dt)
        assertTrue(abs(azimuth - 1.2 * dt) < 1e-9)
        assertTrue(abs(polar + 0.6 * dt) < 1e-9)
        assertTrue(abs(inertia.azimuthRate - 1.2 * exp(-dt / 0.6)) < 1e-9)
        var frames = 1
        while (inertia.active) {
            inertia.step(dt)
            frames++
        }
        // ln(1.2 / 0.05) * 0.6 s ≈ 1.9 s at 60 fps.
        assertTrue(frames in 110..120, "came to rest after $frames frames")
        inertia.track(0.02, 0.0, dt, dragging = true)
        inertia.reset()
        assertFalse(inertia.active)
        assertEquals(Rotation(0.0, 0.0), inertia.step(dt))
    }
}

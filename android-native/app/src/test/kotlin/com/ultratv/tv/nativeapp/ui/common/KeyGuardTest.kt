package com.ultratv.tv.nativeapp.ui.common

import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyGuardTest {
    @Test fun `second down without up is dropped`() {
        val g = KeyGuard()
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1000, 1000))
        assertTrue(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1040, 1040))
    }

    @Test fun `normal presses pass`() {
        val g = KeyGuard()
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1000, 1000))
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, false, 0, 1060, 1060))
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1120, 1120))
    }

    @Test fun `long press repeats pass after the hold delay`() {
        val g = KeyGuard()
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1000, 1000))
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 1, 1750, 1760, downTime = 1000))
    }

    @Test fun `early repeats of a short press are dropped`() {
        val g = KeyGuard()
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1000, 1000))
        assertTrue(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 1, 1500, 1510, downTime = 1000))
    }

    @Test fun `stale queued repeats are dropped`() {
        val g = KeyGuard()
        assertTrue(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 5, 2000, 2400, downTime = 1000))
    }

    @Test fun `lost up does not block a later press`() {
        val g = KeyGuard()
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1000, 1000))
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1400, 1400))
    }

    @Test fun `other keys and other directions are untouched`() {
        val g = KeyGuard()
        assertFalse(g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 1000, 1000))
        assertFalse(g.shouldDrop(KEYCODE_DPAD_UP, true, 0, 1010, 1010))
        assertFalse(g.shouldDrop(KEYCODE_VOLUME_UP, true, 0, 1000, 1000))
        assertFalse(g.shouldDrop(KEYCODE_VOLUME_UP, true, 0, 1010, 1010))
    }

    @Test fun `reports duplicates and lag`() {
        val logs = mutableListOf<String>()
        val g = KeyGuard { logs += it }
        g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 0, 0)
        g.shouldDrop(KEYCODE_DPAD_DOWN, true, 0, 50, 40_050)
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains("dup=1"))
        assertTrue(logs[0].contains("lag=1"))
    }
}

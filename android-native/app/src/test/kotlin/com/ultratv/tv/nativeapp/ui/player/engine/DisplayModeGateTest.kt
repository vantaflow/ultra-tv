package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayModeGateTest {
    @Test fun `bascule seulement en plein écran, réévaluée à l'entrée`() {
        var calls = 0
        val l: () -> Unit = { calls++ }
        DisplayModeGate.addListener(l)
        try {
            DisplayModeGate.exit(android.content.ContextWrapper(null))
            assertFalse(DisplayModeGate.fullscreen)
            DisplayModeGate.enter()
            assertTrue(DisplayModeGate.fullscreen)
            assertEquals(1, calls)
            DisplayModeGate.exit(android.content.ContextWrapper(null))
            assertFalse(DisplayModeGate.fullscreen)
        } finally { DisplayModeGate.removeListener(l) }
    }
}

class DisplayModeSettleTest {
    @Test fun `noir HDMI juste après une bascule`() {
        DisplayModeGate.noteSwitch(1_000)
        assertTrue(DisplayModeGate.settling(1_000 + DisplayModeGate.SETTLE_MS - 1))
        assertFalse(DisplayModeGate.settling(1_000 + DisplayModeGate.SETTLE_MS))
    }
}

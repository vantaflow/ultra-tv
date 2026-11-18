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

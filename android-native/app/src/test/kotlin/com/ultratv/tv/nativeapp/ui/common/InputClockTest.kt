package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class InputClockTest {
    @Test fun `silence deja etabli - aucune attente`() {
        assertEquals(0L, InputClock.quietRemaining(nowMs = 10_000, lastKeyMs = 8_000, quietMs = 1_200))
        assertEquals(0L, InputClock.quietRemaining(nowMs = 10_000, lastKeyMs = 0, quietMs = 1_200))
    }

    @Test fun `touche recente - attend le reste du silence`() {
        assertEquals(1_000L, InputClock.quietRemaining(nowMs = 10_000, lastKeyMs = 9_800, quietMs = 1_200))
        assertEquals(1_200L, InputClock.quietRemaining(nowMs = 10_000, lastKeyMs = 10_000, quietMs = 1_200))
    }
}

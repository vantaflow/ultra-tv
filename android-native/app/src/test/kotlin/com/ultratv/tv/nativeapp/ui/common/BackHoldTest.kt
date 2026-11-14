package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackHoldTest {
    @Test fun appuiCourt_neFermePas_appuiLong_ferme() {
        assertFalse(BackHold.isLong(eventTime = 1_100, downTime = 1_000))
        assertFalse(BackHold.isLong(eventTime = 1_799, downTime = 1_000))
        assertTrue(BackHold.isLong(eventTime = 1_800, downTime = 1_000))
    }
}

package com.ultratv.tv.nativeapp.ui.player.zap

import org.junit.Assert.assertEquals
import org.junit.Test

class ZapTuneTest {
    @Test fun appuiIsoleImmediatRafaleAttend() {
        assertEquals(0L, ZapTune.delayFor(10_000, 0))              // premier zap
        assertEquals(0L, ZapTune.delayFor(10_000, 7_000))          // isolé (3 s après)
        assertEquals(ZapTune.SETTLE_MS, ZapTune.delayFor(10_000, 9_830))   // répétition de touche maintenue
        assertEquals(ZapTune.SETTLE_MS, ZapTune.delayFor(10_000, 8_600))   // appuis rapides
    }
}

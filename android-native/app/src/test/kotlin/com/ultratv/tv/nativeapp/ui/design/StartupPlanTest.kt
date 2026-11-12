package com.ultratv.tv.nativeapp.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan de l'animation d'ouverture (700 ms) : modes, durée et images clés. Pur. */
class StartupPlanTest {
    @Test fun modes() {
        assertEquals(StartupMode.NONE, StartupPlan.mode(animationsOff = true, lowRam = false))
        assertEquals(StartupMode.NONE, StartupPlan.mode(animationsOff = true, lowRam = true))
        assertEquals(StartupMode.FADE, StartupPlan.mode(animationsOff = false, lowRam = true))
        assertEquals(StartupMode.FULL, StartupPlan.mode(animationsOff = false, lowRam = false))
    }

    @Test fun durees() {
        assertEquals(700, StartupPlan.durationMs(StartupMode.FULL))
        assertEquals(200, StartupPlan.durationMs(StartupMode.FADE))
        assertEquals(0, StartupPlan.durationMs(StartupMode.NONE))
    }

    @Test fun echelleDuSysteme() {
        assertTrue(StartupPlan.systemAnimationsOff(0f))
        assertTrue(!StartupPlan.systemAnimationsOff(1f))
        assertTrue(!StartupPlan.systemAnimationsOff(0.5f))
    }

    @Test fun apparition_0a250ms() {
        val a = StartupPlan.frame(StartupMode.FULL, 0f)
        assertEquals(0f, a.logoAlpha, 0.001f); assertEquals(0.92f, a.scale, 0.001f); assertEquals(1f, a.bgAlpha, 0.001f)
        val b = StartupPlan.frame(StartupMode.FULL, 250f)
        assertEquals(1f, b.logoAlpha, 0.001f); assertEquals(0.92f + 0.08f, b.scale, 0.001f)
        assertEquals(0f, b.triangle, 0.001f)
    }

    @Test fun eclatDuPlay_250a500ms() {
        val mid = StartupPlan.frame(StartupMode.FULL, 375f)
        assertEquals(0.5f, mid.triangle, 0.001f)
        assertTrue(mid.haloPx in 1f..18f)
        assertEquals(0.18f, mid.haloAlpha, 0.001f)   // sommet du halo
        assertEquals(0f, StartupPlan.frame(StartupMode.FULL, 499.99f).haloAlpha, 0.01f)
    }

    @Test fun ouverture_500a700ms_revelelApplication() {
        val start = StartupPlan.frame(StartupMode.FULL, 500f)
        assertEquals(1f, start.bgAlpha, 0.001f)
        val end = StartupPlan.frame(StartupMode.FULL, 700f)
        assertEquals(0f, end.bgAlpha, 0.001f); assertEquals(0f, end.logoAlpha, 0.001f)
        assertEquals(1f, end.triangle, 0.001f)
    }

    @Test fun fondu_simple200ms() {
        assertEquals(1f, StartupPlan.frame(StartupMode.FADE, 0f).bgAlpha, 0.001f)
        assertEquals(0.5f, StartupPlan.frame(StartupMode.FADE, 100f).bgAlpha, 0.001f)
        assertEquals(0f, StartupPlan.frame(StartupMode.FADE, 200f).bgAlpha, 0.001f)
        assertEquals(0f, StartupPlan.frame(StartupMode.FADE, 100f).haloAlpha, 0.001f)
    }

    @Test fun aucuneAnimation_toutTransparent() {
        val f = StartupPlan.frame(StartupMode.NONE, 0f)
        assertEquals(0f, f.bgAlpha, 0.001f); assertEquals(0f, f.logoAlpha, 0.001f)
    }
}

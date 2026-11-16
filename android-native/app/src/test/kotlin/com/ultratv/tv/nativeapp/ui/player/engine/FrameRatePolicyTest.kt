package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRatePolicyTest {
    // ── Arrondi aux cadences usuelles ──
    @Test fun snap_cadencesExactes() {
        for (f in FrameRatePolicy.STANDARD) assertEquals(f, FrameRatePolicy.snap(f)!!, 0f)
    }
    @Test fun snap_arrondisProches() {
        assertEquals(23.976f, FrameRatePolicy.snap(23.98f)!!, 0f)
        assertEquals(23.976f, FrameRatePolicy.snap(23.9f)!!, 0f)
        assertEquals(29.97f, FrameRatePolicy.snap(29.9701f)!!, 0f)
        assertEquals(59.94f, FrameRatePolicy.snap(59.9f)!!, 0f)
        assertEquals(25f, FrameRatePolicy.snap(25.04f)!!, 0f)
        assertEquals(50f, FrameRatePolicy.snap(49.95f)!!, 0f)
    }
    @Test fun snap_inconnueOuAberrante_null() {
        assertNull(FrameRatePolicy.snap(null)); assertNull(FrameRatePolicy.snap(0f)); assertNull(FrameRatePolicy.snap(-1f))
        assertNull(FrameRatePolicy.snap(Float.NaN)); assertNull(FrameRatePolicy.snap(Float.POSITIVE_INFINITY))
        assertNull(FrameRatePolicy.snap(15f)); assertNull(FrameRatePolicy.snap(24.5f)); assertNull(FrameRatePolicy.snap(1000f))
    }

    // ── Compatibilité fréquence d'écran / cadence ──
    @Test fun compatible_multiplesEntiers() {
        assertTrue(FrameRatePolicy.compatible(50f, 25f))
        assertTrue(FrameRatePolicy.compatible(60f, 30f))
        assertTrue(FrameRatePolicy.compatible(60f, 29.97f))
        assertTrue(FrameRatePolicy.compatible(24f, 23.976f))
        assertTrue(FrameRatePolicy.compatible(120f, 24f))
        assertTrue(FrameRatePolicy.compatible(59.94f, 59.94f))
    }
    @Test fun compatible_pulldown_refuse() {
        assertFalse(FrameRatePolicy.compatible(60f, 24f))      // 3:2 pulldown
        assertFalse(FrameRatePolicy.compatible(60f, 23.976f))
        assertFalse(FrameRatePolicy.compatible(50f, 24f))
        assertFalse(FrameRatePolicy.compatible(60f, 25f))
        assertFalse(FrameRatePolicy.compatible(0f, 25f)); assertFalse(FrameRatePolicy.compatible(60f, 0f))
    }

    // ── Choix du mode ──
    private fun m(id: Int, hz: Float, w: Int = 3840, h: Int = 2160) = DisplayModeInfo(id, hz, w, h)
    private val modes = listOf(m(1, 23.976f), m(2, 24f), m(3, 25f), m(4, 50f), m(5, 59.94f), m(6, 60f), m(7, 60f, 1920, 1080), m(8, 24f, 1920, 1080), m(9, 30f))

    @Test fun mode_cadenceInconnue_neChangeRien() {
        assertNull(FrameRatePolicy.chooseMode(null, m(6, 60f), modes))
        assertNull(FrameRatePolicy.chooseMode(0f, m(6, 60f), modes))
    }
    @Test fun mode_frequenceDejaCompatible_neChangeRien() {
        assertNull(FrameRatePolicy.chooseMode(25f, m(4, 50f), modes))
        assertNull(FrameRatePolicy.chooseMode(29.97f, m(6, 60f), modes))
        assertNull(FrameRatePolicy.chooseMode(23.976f, m(2, 24f), modes))
    }
    @Test fun mode_cinema_choisitLeModeExact() {
        assertEquals(1, FrameRatePolicy.chooseMode(23.976f, m(6, 60f), modes))
        assertEquals(2, FrameRatePolicy.chooseMode(24f, m(6, 60f), modes))
    }
    @Test fun mode_25ips_depuis60Hz_prendLe25Hz() = assertEquals(3, FrameRatePolicy.chooseMode(25f, m(6, 60f), modes))
    @Test fun mode_resteALaResolutionActuelle() {
        // En 1080p : seul le mode 24 Hz 1080p est candidat, jamais un mode 4K.
        assertEquals(8, FrameRatePolicy.chooseMode(24f, m(7, 60f, 1920, 1080), modes))
        assertNull(FrameRatePolicy.chooseMode(25f, m(7, 60f, 1920, 1080), modes))   // pas de 25/50 Hz en 1080p : on ne change pas de résolution
    }
    @Test fun mode_plusPetitMultipleD_abord() {
        val l = listOf(m(1, 48f), m(2, 24f), m(3, 72f))
        assertEquals(2, FrameRatePolicy.chooseMode(24f, m(9, 60f), l))
    }
    @Test fun mode_aucunModeCompatible_null() = assertNull(FrameRatePolicy.chooseMode(24f, m(6, 60f), listOf(m(6, 60f), m(4, 50f))))

    // ── Mesure sur les images rendues ──
    private fun feed(e: FrameRateEstimator, fps: Double, frames: Int, startUs: Long = 0L) {
        for (i in 0 until frames) e.add(startUs + Math.round(i * 1_000_000.0 / fps))
    }
    @Test fun estimateur_pasAssezD_images_null() {
        val e = FrameRateEstimator(); feed(e, 25.0, 20); assertNull(e.estimate())
    }
    @Test fun estimateur_mesureLesCadencesUsuelles() {
        for (f in listOf(23.976, 25.0, 29.97, 50.0, 59.94)) {
            val e = FrameRateEstimator(); feed(e, f, 60)
            assertEquals(f.toFloat(), FrameRatePolicy.snap(e.estimate())!!, 0.001f)
        }
    }
    @Test fun estimateur_ignoreImagesEnDoubleEtGrosTrou() {
        val e = FrameRateEstimator()
        feed(e, 25.0, 10); e.add(9 * 40_000L)   // doublon : intervalle nul, mesure neuve
        feed(e, 25.0, 60, startUs = 10_000_000L)
        assertNotNull(e.estimate()); assertEquals(25f, FrameRatePolicy.snap(e.estimate())!!, 0f)
    }
    @Test fun estimateur_medianeResisteAuxIrregularites() {
        val e = FrameRateEstimator(); var t = 0L
        for (i in 0 until 60) { e.add(t); t += if (i % 10 == 9) 120_000L else 40_000L }   // quelques images en retard
        assertEquals(25f, FrameRatePolicy.snap(e.estimate())!!, 0f)
    }
    @Test fun estimateur_reset() {
        val e = FrameRateEstimator(); feed(e, 25.0, 60); e.reset(); assertNull(e.estimate())
    }
}

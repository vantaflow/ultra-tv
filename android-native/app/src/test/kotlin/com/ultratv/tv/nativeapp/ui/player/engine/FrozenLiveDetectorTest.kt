package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrozenLiveDetectorTest {
    @Test fun lectureNormale_jamaisFigee() {
        val d = FrozenLiveDetector()
        for (i in 0..60) assertFalse(d.feed(i * 2_000L, true, i * 2_000L, i * 50L, true))
    }

    @Test fun positionBloquee_figeeApres10s() {
        val d = FrozenLiveDetector()
        assertFalse(d.feed(0, true, 5_000, 100, true))
        assertFalse(d.feed(4_000, true, 5_000, 100, true))
        assertFalse(d.feed(8_000, true, 5_000, 100, true))
        assertTrue(d.feed(10_000, true, 5_000, 100, true))
    }

    @Test fun imageGeleeAvecSon_figeeApres10s() {
        val d = FrozenLiveDetector()
        assertFalse(d.feed(0, true, 0, 100, true))
        assertFalse(d.feed(6_000, true, 6_000, 100, true))
        assertTrue(d.feed(10_000, true, 10_000, 100, true))
    }

    @Test fun pauseOuChargement_neCompteNiNeDeclenche() {
        val d = FrozenLiveDetector()
        assertFalse(d.feed(0, true, 1_000, 10, true))
        for (t in 2_000L..60_000L step 2_000L) assertFalse(d.feed(t, false, 1_000, 10, true))
        // Reprise : le délai repart de zéro.
        assertFalse(d.feed(62_000, true, 1_000, 10, true))
        assertFalse(d.feed(70_000, true, 1_000, 10, true))
        assertTrue(d.feed(72_000, true, 1_000, 10, true))
    }

    @Test fun sansCompteurDImages_seuleLaPositionCompte() {
        val d = FrozenLiveDetector()
        for (i in 0..20) assertFalse(d.feed(i * 2_000L, true, i * 2_000L, -1, true))
        val d2 = FrozenLiveDetector()
        for (i in 0..20) assertFalse(d2.feed(i * 2_000L, true, i * 2_000L, 7, false))
    }
}

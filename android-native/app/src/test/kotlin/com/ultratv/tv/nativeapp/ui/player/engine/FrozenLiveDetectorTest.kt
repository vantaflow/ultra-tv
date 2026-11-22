package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
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
        assertEquals("frames", d.lastReason)
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

    // Retour d'horodatage du flux (box 1.3.73-rc7) : Media3 garde sa position tant que le nouveau minutage n'a pas
    // rattrapé l'ancien, mais le décodeur vidéo continue de sortir des images. Ce n'est pas un gel : pas de reconnexion.
    @Test fun positionBloqueeMaisImagesQuiAvancent_pasFigee() {
        val d = FrozenLiveDetector()
        for (i in 0..15) assertFalse(d.feed(i * 2_000L, true, 85_000, 1_000L + i * 50L, true))
    }

    @Test fun sansImagesControlables_sautDHorodatageRecent_pasFige() {
        val d = FrozenLiveDetector()
        assertFalse(d.feed(0, true, 85_000, -1, false, bytes = 0, jumps = 0))
        assertFalse(d.feed(2_000, true, 85_000, -1, false, bytes = 0, jumps = 1))   // saut signalé
        for (t in 4_000L..10_000L step 2_000L) assertFalse(d.feed(t, true, 85_000, -1, false, bytes = 0, jumps = 1))
        // 10 s après le dernier saut, toujours rien qui bouge (octets compris) : vrai gel.
        assertTrue(d.feed(12_000, true, 85_000, -1, false, bytes = 0, jumps = 1))
        assertEquals("position", d.lastReason)
    }

    @Test fun sansImagesControlables_octetsQuiArrivent_pasFige() {
        val d = FrozenLiveDetector()
        for (i in 0..8) assertFalse(d.feed(i * 2_000L, true, 85_000, -1, false, bytes = i * 100_000L, jumps = 0))
        // Les octets s'arrêtent aussi : gel 10 s plus tard.
        assertFalse(d.feed(18_000, true, 85_000, -1, false, bytes = 800_000, jumps = 0))
        assertFalse(d.feed(24_000, true, 85_000, -1, false, bytes = 800_000, jumps = 0))
        assertTrue(d.feed(26_000, true, 85_000, -1, false, bytes = 800_000, jumps = 0))
    }

    @Test fun positionEtImagesBloquees_figeMemeAvecOctets() {
        val d = FrozenLiveDetector()
        for (i in 0..4) assertFalse(d.feed(i * 2_000L, true, 5_000, 100, true, bytes = i * 1_000L, jumps = 0))
        assertTrue(d.feed(10_000, true, 5_000, 100, true, bytes = 9_000, jumps = 0))
    }
}

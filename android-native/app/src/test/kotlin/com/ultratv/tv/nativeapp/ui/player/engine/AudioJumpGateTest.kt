package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flux du fournisseur dont les horodatages repartent en arrière (journal de la box : « expected 1000024808333,
 * got 1000000019000 ») : la position audio recule APRÈS le signal de discontinuité ; le recul doit être annoncé au
 * moteur à ce moment-là, sinon Media3 garde l'ancienne position (max) et la lecture paraît figée 10 à 25 s.
 */
class AudioJumpGateTest {
    private val unset = Long.MIN_VALUE

    @Test fun lectureNormale_aucunRecul() {
        val g = AudioJumpGate()
        for (i in 0..100) assertFalse(g.onPosition(i * 20_000L))
    }

    @Test fun reculApresSignal_annonceUneFois() {
        val g = AudioJumpGate()
        assertFalse(g.onPosition(24_700_000))
        g.onDiscontinuity()                        // le tampon décalé est écrit…
        assertFalse(g.onPosition(24_780_000))      // …mais la sortie joue encore l'ancien son
        assertFalse(g.onPosition(24_800_000))
        assertTrue(g.onPosition(19_000))           // la sortie atteint le point de reprise : recul à annoncer
        assertFalse(g.onPosition(39_000))
        assertFalse(g.onPosition(10_000))          // déjà annoncé : rien de plus sans nouveau signal
    }

    @Test fun reculSansSignal_ignore() {
        val g = AudioJumpGate()
        assertFalse(g.onPosition(5_000_000))
        assertFalse(g.onPosition(1_000_000))
    }

    @Test fun petiteGigue_ignoree() {
        val g = AudioJumpGate()
        g.onPosition(5_000_000); g.onDiscontinuity()
        assertFalse(g.onPosition(4_950_000))       // 50 ms : bruit de mesure, pas un saut
    }

    @Test fun sautEnAvant_signalExpire() {
        val g = AudioJumpGate()
        g.onPosition(5_000_000); g.onDiscontinuity()
        for (t in 5_000_000L..12_000_000L step 500_000L) assertFalse(g.onPosition(t))
        assertFalse(g.onPosition(1_000_000))       // signal trop ancien : plus d'annonce
    }

    @Test fun journalDuSaut() {
        org.junit.Assert.assertEquals(
            "live timestamp jump back=24.7s n=1 engine=EXO -> keep playing",
            TimestampJumpLog.line(1_000_024_808_333, 1_000_000_019_000, 1, live = true),
        )
        org.junit.Assert.assertEquals(
            "timestamp jump forward=2.0s n=3 engine=EXO -> keep playing",
            TimestampJumpLog.line(1_000_000_000_000, 1_000_002_000_000, 3, live = false),
        )
    }

    @Test fun positionInconnueOuRemiseAZero() {
        val g = AudioJumpGate()
        g.onPosition(5_000_000); g.onDiscontinuity()
        assertFalse(g.onPosition(unset))
        g.reset()                                  // vidage du lecteur : tout repart
        assertFalse(g.onPosition(100_000))
    }
}

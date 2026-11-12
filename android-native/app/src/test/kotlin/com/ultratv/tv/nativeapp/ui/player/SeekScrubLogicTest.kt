package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.SeekScrubLogic.Action
import com.ultratv.tv.nativeapp.ui.player.SeekScrubLogic.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekScrubLogicTest {
    private val dur = 7200.0   // 2 h : plafond 2 % = 144 s, donc jamais atteint

    @Test fun pas_selonLaDureeDAppui() {
        assertEquals(10, SeekScrubLogic.stepSeconds(0, dur))
        assertEquals(10, SeekScrubLogic.stepSeconds(999, dur))
        assertEquals(30, SeekScrubLogic.stepSeconds(1000, dur))
        assertEquals(30, SeekScrubLogic.stepSeconds(2499, dur))
        assertEquals(60, SeekScrubLogic.stepSeconds(2500, dur))
        assertEquals(60, SeekScrubLogic.stepSeconds(3999, dur))
        assertEquals(120, SeekScrubLogic.stepSeconds(4000, dur))
    }

    @Test fun pas_plafonneADeuxPourCentDeLaDuree() {
        assertEquals(24, SeekScrubLogic.stepSeconds(5000, 1200.0))   // 2 % de 20 min
        assertEquals(10, SeekScrubLogic.stepSeconds(5000, 300.0))    // plancher 10 s
        assertEquals(120, SeekScrubLogic.stepSeconds(5000, 0.0))     // durée inconnue : pas de plafond
    }

    @Test fun unAppui_dixSecondes_etBornes() {
        val s = SeekScrubLogic.press(null, 100.0, dur, true, false, 0)
        assertEquals(100.0, s.origin, 0.0); assertEquals(110.0, s.target, 0.0)
        assertEquals(0.0, SeekScrubLogic.press(null, 4.0, dur, false, false, 0).target, 0.0)
        assertEquals(dur - 1, SeekScrubLogic.press(null, dur - 3, dur, true, false, 0).target, 0.0)
    }

    @Test fun appuisRapides_cumulent_lentsRepartentDeLaPosition() {
        var s = SeekScrubLogic.press(null, 100.0, dur, true, false, 0)
        s = SeekScrubLogic.press(s, 101.0, dur, true, false, 300)
        s = SeekScrubLogic.press(s, 102.0, dur, true, false, 599 + 300)
        assertEquals(100.0, s.origin, 0.0); assertEquals(130.0, s.target, 0.0)
        // Plus de 600 ms : nouveau défilement depuis la position courante.
        val n = SeekScrubLogic.press(s, 200.0, dur, false, false, 599 + 300 + 600)
        assertEquals(200.0, n.origin, 0.0); assertEquals(190.0, n.target, 0.0)
    }

    @Test fun nouvelAppuiRapproche_remetLaDureeDAppuiAZero() {
        var s = SeekScrubLogic.press(null, 0.0, dur, true, false, 0)
        s = SeekScrubLogic.press(s, 0.0, dur, true, false, 500)
        assertEquals(500L, s.pressStartAt)
    }

    @Test fun toucheMaintenue_accelere_auPlusUnPasParCadence() {
        var s = SeekScrubLogic.press(null, 0.0, dur, true, false, 0)       // +10
        s = SeekScrubLogic.press(s, 0.0, dur, true, true, 100)             // trop tôt : rien
        assertEquals(10.0, s.target, 0.0)
        s = SeekScrubLogic.press(s, 0.0, dur, true, true, 500)             // 500 ms : +10
        assertEquals(20.0, s.target, 0.0)
        s = SeekScrubLogic.press(s, 0.0, dur, true, true, 1000)            // 1 s : +30
        assertEquals(50.0, s.target, 0.0)
        s = SeekScrubLogic.press(s, 0.0, dur, true, true, 2500)            // +60
        assertEquals(110.0, s.target, 0.0)
        s = SeekScrubLogic.press(s, 0.0, dur, true, true, 4000)            // +120
        assertEquals(230.0, s.target, 0.0)
        assertEquals(0L, s.pressStartAt)
    }

    @Test fun repetitionTropRapprochee_prolongeLaValidation() {
        val s = SeekScrubLogic.press(null, 0.0, dur, true, false, 0)
        val r = SeekScrubLogic.press(s, 0.0, dur, true, true, 100)
        assertEquals(s.target, r.target, 0.0)
        assertFalse(SeekScrubLogic.shouldCommit(r, 100 + 699))
        assertTrue(SeekScrubLogic.shouldCommit(r, 100 + 700))
    }

    @Test fun validation_700ms_ouFiletDe1500msToucheEnfoncee_etRelachement() {
        val s = SeekScrubLogic.press(null, 0.0, dur, true, false, 0)
        assertFalse(SeekScrubLogic.shouldCommit(s, 699)); assertTrue(SeekScrubLogic.shouldCommit(s, 700))
        assertFalse(SeekScrubLogic.shouldCommit(s, 1499, keyDown = true)); assertTrue(SeekScrubLogic.shouldCommit(s, 1500, keyDown = true))
        val r = SeekScrubLogic.release(s, 3000)
        assertFalse(SeekScrubLogic.shouldCommit(r, 3699)); assertTrue(SeekScrubLogic.shouldCommit(r, 3700))
    }

    @Test fun annulation_reviendraALOrigine() {
        val s = SeekScrubLogic.press(null, 321.0, dur, true, false, 0)
        assertEquals(321.0, SeekScrubLogic.cancelPosition(s), 0.0)
    }

    @Test fun ecartAffiche() {
        assertEquals("+2:30", SeekScrubLogic.fmtDelta(150.0))
        assertEquals("−0:40", SeekScrubLogic.fmtDelta(-40.0))
        assertEquals("+1:05:00", SeekScrubLogic.fmtDelta(3900.0))
        assertEquals("−1:00:10", SeekScrubLogic.fmtDelta(-3610.0))
        assertEquals("+0:00", SeekScrubLogic.fmtDelta(0.0))
    }

    @Test fun decision_commandesMasquees_afficheEtFocusSurLaBarre() {
        assertEquals(Action.Scrub(false, true), SeekScrubLogic.decide(Key.BACK, false, hidden = true, barFocused = false, scrubbing = false))
        assertEquals(Action.Scrub(true, true), SeekScrubLogic.decide(Key.FORWARD, false, hidden = true, barFocused = false, scrubbing = false))
    }

    @Test fun decision_surLaBarre_defileSansChangerLeFocus_surLesBoutons_circule() {
        assertEquals(Action.Scrub(true, false), SeekScrubLogic.decide(Key.FORWARD, false, hidden = false, barFocused = true, scrubbing = false))
        assertEquals(Action.Pass, SeekScrubLogic.decide(Key.FORWARD, false, hidden = false, barFocused = false, scrubbing = false))
        // Touches média : défilent quel que soit le focus.
        assertEquals(Action.Scrub(false, false), SeekScrubLogic.decide(Key.BACK, true, hidden = false, barFocused = false, scrubbing = false))
    }

    @Test fun decision_OK_valideUnDefilementEnCours_sinonPasse() {
        assertEquals(Action.CommitNow, SeekScrubLogic.decide(Key.OK, false, hidden = false, barFocused = true, scrubbing = true))
        assertEquals(Action.Pass, SeekScrubLogic.decide(Key.OK, false, hidden = false, barFocused = true, scrubbing = false))
    }
}

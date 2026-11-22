package com.ultratv.tv.nativeapp.ui.player.engine

import com.ultratv.tv.nativeapp.ui.player.engine.LiveRecoveryPolicy.Cause
import com.ultratv.tv.nativeapp.ui.player.engine.LiveRecoveryPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRecoveryPolicyTest {
    private fun exo(p: LiveRecoveryPolicy, now: Long, cause: Cause = Cause.FROZEN, vlc: Boolean = true) =
        p.onFailure(now, EngineKind.EXO, cause, vlcAllowed = vlc)

    // Boucle relevée sur la box : gel 22:29:18 → reconnexion #1, rechargement 22:29:54 → encore « #1 » (compteur remis
    // à zéro après 30 s). Désormais les délais croissent dans la fenêtre et la 3e panne Exo bascule sur VLC.
    @Test fun gelsRepetesSurExo_delaisCroissantsPuisVlc() {
        val p = LiveRecoveryPolicy()
        assertEquals(Decision.Reconnect(500, 1), exo(p, 0))
        assertEquals(Decision.Reconnect(1_000, 2), exo(p, 36_000, Cause.STALL))
        assertEquals(Decision.SwitchToVlc, exo(p, 70_000, Cause.ERROR))
    }

    @Test fun pannesEspacees_pasDeVlc() {
        val p = LiveRecoveryPolicy()
        assertEquals(Decision.Reconnect(500, 1), exo(p, 0))
        assertEquals(Decision.Reconnect(500, 1), exo(p, 200_000))       // hors fenêtre de 2 min : repart de zéro
        assertEquals(Decision.Reconnect(1_000, 2), exo(p, 260_000))
    }

    @Test fun finDeSessionServeur_neCompteJamaisPourVlc() {
        val p = LiveRecoveryPolicy()
        repeat(5) { i -> assertTrue(p.onFailure(i * 20_000L, EngineKind.EXO, Cause.ENDED, vlcAllowed = true) is Decision.Reconnect) }
    }

    @Test fun vlcInterdit_resteSurExoAvecPlafond() {
        val p = LiveRecoveryPolicy()
        val d = (0 until 8).map { exo(p, it * 1_000L, vlc = false) }
        assertTrue(d.all { it is Decision.Reconnect })
        assertEquals(listOf(500L, 1_000L, 2_000L, 3_000L, 5_000L, 8_000L, 10_000L, 10_000L), d.map { (it as Decision.Reconnect).delayMs })
        assertEquals(Decision.GiveUp, exo(p, 9_000, vlc = false))
    }

    @Test fun surVlc_plafondPuisAbandon() {
        val p = LiveRecoveryPolicy()
        repeat(8) { i -> assertTrue(p.onFailure(i * 5_000L, EngineKind.VLC, Cause.ERROR, vlcAllowed = true) is Decision.Reconnect) }
        assertEquals(Decision.GiveUp, p.onFailure(45_000, EngineKind.VLC, Cause.ERROR, vlcAllowed = true))
    }

    // Boucle lente (une coupure toutes les ~35 s) : la fenêtre courte ne se remplit jamais, la longue l'arrête.
    @Test fun boucleLente_plafondSurDixMinutes() {
        val p = LiveRecoveryPolicy()
        var t = 0L
        var last: Decision? = null
        var n = 0
        while (n < 40) { last = p.onFailure(t, EngineKind.VLC, Cause.ERROR, vlcAllowed = false); n++; if (last == Decision.GiveUp) break; t += 35_000 }
        assertEquals(Decision.GiveUp, last)
        assertTrue("abandon après ~16 reconnexions, pas avant 9 min", n in 16..18 && t >= 9 * 60_000L - 35_000)
    }

    @Test fun serveurQuiFermeChaqueMinute_jamaisDAbandon() {
        val p = LiveRecoveryPolicy()
        repeat(60) { i -> assertTrue(p.onFailure(i * 60_000L, EngineKind.EXO, Cause.ENDED, vlcAllowed = true) is Decision.Reconnect) }
    }

    @Test fun remiseAZero() {
        val p = LiveRecoveryPolicy()
        exo(p, 0); exo(p, 1_000)
        p.reset()
        assertEquals(Decision.Reconnect(500, 1), exo(p, 2_000))
    }

    @Test fun memoireDeSession_parChaine() {
        val m = LiveEngineMemo()
        assertFalse(m.prefersVlc("1:a"))
        m.preferVlc("1:a")
        assertTrue(m.prefersVlc("1:a"))
        assertFalse(m.prefersVlc("1:b"))
        m.clear()
        assertFalse(m.prefersVlc("1:a"))
    }
}

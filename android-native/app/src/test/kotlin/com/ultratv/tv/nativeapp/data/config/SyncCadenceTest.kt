package com.ultratv.tv.nativeapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCadenceTest {
    private val min = 60_000L

    @Test fun apresUnChangement_deuxMinutesPendantDixMinutes() {
        val c = SyncCadence(start = 0)
        c.noteChange(100 * min)
        assertEquals(2 * min, c.intervalMs(100 * min))
        assertEquals(2 * min, c.intervalMs(109 * min))
    }

    @Test fun sansChangement_dixMinutesApresLaFenetre() {
        val c = SyncCadence(start = 0)
        c.noteChange(100 * min)
        assertEquals(10 * min, c.intervalMs(110 * min))
        assertEquals(10 * min, c.intervalMs(500 * min))
    }

    @Test fun unNouveauChangementRamenePourDeuxMinutes() {
        val c = SyncCadence(start = 0)
        assertEquals(10 * min, c.intervalMs(300 * min))
        c.noteChange(300 * min) // changement reçu d'un autre appareil
        assertEquals(2 * min, c.intervalMs(301 * min))
    }

    @Test fun demarrage_compteCommeUnChangement() {
        val c = SyncCadence(start = 1_000_000)
        assertEquals(2 * min, c.intervalMs(1_000_000 + 5 * min))
        assertEquals(10 * min, c.intervalMs(1_000_000 + 11 * min))
    }

    @Test fun synchroPeriodique_dueSeulementQuandLeDelaiEstEcoule() {
        val c = SyncCadence(start = 0)
        c.noteChange(100 * min)
        assertFalse(c.periodicDue(101 * min, lastRun = 100 * min))
        assertTrue(c.periodicDue(102 * min, lastRun = 100 * min))
        // Après la fenêtre active : 10 min.
        assertFalse(c.periodicDue(115 * min, lastRun = 112 * min))
        assertTrue(c.periodicDue(122 * min, lastRun = 112 * min))
    }

    @Test fun retourAuPremierPlan_auPlusUneParMinute() {
        val c = SyncCadence(start = 0)
        assertTrue(c.foregroundDue(100 * min, lastRun = 0))
        assertFalse(c.foregroundDue(100 * min + 59_000, lastRun = 100 * min))
        assertTrue(c.foregroundDue(101 * min, lastRun = 100 * min))
    }

    // ── Empreintes : ne pousser que ce qui a changé ──

    @Test fun contenuInchange_pasRenvoyeAvantLeRafraichissement() {
        val g = SyncPushGate()
        val p = SyncPushGate.print("{\"fav\":[1,2]}")
        assertTrue(g.shouldExchange("state:a", p, 0))        // jamais envoyé
        g.markSent("state:a", p, 0)
        assertFalse(g.shouldExchange("state:a", p, 2 * min))  // inchangé : rien à pousser
        assertFalse(g.shouldExchange("state:a", p, 9 * min))
        assertTrue(g.shouldExchange("state:a", p, 10 * min))  // rafraîchissement : reçoit les changements des autres appareils
    }

    @Test fun contenuModifie_renvoyeAussitot() {
        val g = SyncPushGate()
        g.markSent("profiles", SyncPushGate.print("a"), 0)
        assertTrue(g.shouldExchange("profiles", SyncPushGate.print("b"), 1_000))
    }

    @Test fun cleesIndependantes_etReset() {
        val g = SyncPushGate()
        val p = SyncPushGate.print("x")
        g.markSent("state:a", p, 0)
        assertTrue(g.shouldExchange("state:b", p, 1_000))
        assertFalse(g.shouldExchange("state:a", p, 1_000))
        g.reset()
        assertTrue(g.shouldExchange("state:a", p, 1_000))
    }

    @Test fun empreinte_stableEtDistincte() {
        assertEquals(SyncPushGate.print("abc"), SyncPushGate.print("abc"))
        assertNotEquals(SyncPushGate.print("abc"), SyncPushGate.print("abd"))
        assertEquals(32, SyncPushGate.print("abc").length)
    }
}

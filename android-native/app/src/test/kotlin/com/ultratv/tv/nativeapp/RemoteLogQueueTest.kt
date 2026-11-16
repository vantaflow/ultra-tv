package com.ultratv.tv.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLogQueueTest {
    private val min = 60_000L
    private fun line(level: String = "info", msg: String = "m", ts: Long = 0) = LogQueue.Line(ts, level, "t", msg)

    @Test fun fileVide_aucunEnvoiProgramme() {
        assertNull(LogQueue().nextFlushDelay(0))
    }

    @Test fun ligneInfo_envoiDansCinqMinutes() {
        val q = LogQueue()
        assertFalse(q.add(line("info"), 1_000))
        assertEquals(5 * min, q.nextFlushDelay(1_000))
        assertEquals(min, q.nextFlushDelay(1_000 + 4 * min))
    }

    @Test fun erreur_envoiDansLaMinute_memeSiUneInfoAttend() {
        val q = LogQueue()
        q.add(line("info"), 0)
        q.add(line("error"), 10_000)
        assertEquals(50_000L, q.nextFlushDelay(10_000)) // 60 s après l'erreur
        // Une info ultérieure ne repousse pas l'échéance.
        q.add(line("info"), 20_000)
        assertEquals(40_000L, q.nextFlushDelay(20_000))
    }

    @Test fun delaiJamaisNegatif() {
        val q = LogQueue()
        q.add(line("error"), 0)
        assertEquals(0L, q.nextFlushDelay(10 * min))
    }

    @Test fun cinquanteLignes_envoiImmediat() {
        val q = LogQueue()
        for (i in 1..49) assertFalse(q.add(line(msg = "$i"), i.toLong()))
        assertTrue(q.add(line(msg = "50"), 50))
    }

    @Test fun debordement_ecarteLesPlusAnciennes() {
        val q = LogQueue()
        for (i in 1..60) q.add(line(msg = "$i"), i.toLong())
        assertEquals(50, q.size)
        val out = q.drain()
        assertEquals("11", out.first().message)
        assertEquals("60", out.last().message)
    }

    @Test fun drain_viderEtOrdreChronologique() {
        val q = LogQueue()
        q.add(line(msg = "a"), 0); q.add(line(msg = "b"), 1)
        assertEquals(listOf("a", "b"), q.drain().map { it.message })
        assertEquals(0, q.size)
        assertNull(q.nextFlushDelay(2))
    }

    @Test fun echecEnvoi_lignesReviennentEnTete_etNouvelEssaiPlusTard() {
        val q = LogQueue()
        q.add(line(msg = "a"), 0)
        val failed = q.drain()
        q.add(line(msg = "b"), 1_000)
        q.restore(failed, 2_000)
        assertEquals(listOf("a", "b"), q.drain().map { it.message })
        // Avec une erreur en attente, nouvel essai dans 60 s, pas en boucle serrée.
        q.restore(listOf(line("error", "e")), 100_000)
        assertEquals(60_000L, q.nextFlushDelay(100_000))
    }

    @Test fun restauration_resteBorneeACinquante() {
        val q = LogQueue()
        val failed = (1..50).map { line(msg = "old$it") }
        for (i in 1..10) q.add(line(msg = "new$i"), 0)
        q.restore(failed, 0)
        assertEquals(50, q.size)
        assertEquals("new10", q.drain().last().message)
    }

    @Test fun avertissementsEtErreurs_toujoursGardes() {
        val q = LogQueue(rand = { 0.999 })
        for (l in listOf("warn", "warning", "error", "fatal", "ERROR")) assertTrue(l, q.accept(l))
    }

    @Test fun lignesInfo_unSurDix() {
        // rand < 0,1 : gardée ; sinon écartée.
        assertTrue(LogQueue(rand = { 0.05 }).accept("info"))
        assertFalse(LogQueue(rand = { 0.10 }).accept("info"))
        assertFalse(LogQueue(rand = { 0.9 }).accept("debug"))
        // Sur beaucoup de tirages, environ 10 %.
        val r = java.util.Random(42)
        val q = LogQueue(rand = { r.nextDouble() })
        val kept = (1..10_000).count { q.accept("info") }
        assertTrue("kept=$kept", kept in 800..1200)
    }

    @Test fun decoupage_respecteLaTailleEtLeNombreDeLignes() {
        val many = (1..120).map { line(msg = "x") }
        val c = LogQueue.chunks(many)
        assertEquals(listOf(50, 50, 20), c.map { it.size })
        val big = (1..40).map { line(msg = "y".repeat(1000)) }
        val bigChunks = LogQueue.chunks(big)
        assertTrue(bigChunks.size > 1)
        assertEquals(40, bigChunks.sumOf { it.size })
        assertTrue(bigChunks.all { ch -> ch.sumOf { it.message.length + 130 } < 32_000 })
        assertTrue(LogQueue.chunks(emptyList()).isEmpty())
    }
}

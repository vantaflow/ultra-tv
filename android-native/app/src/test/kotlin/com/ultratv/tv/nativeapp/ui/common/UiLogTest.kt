package com.ultratv.tv.nativeapp.ui.common

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class UiLogTest {
    @After fun reset() { UiLog.sink = null }

    @Test fun ligne_ecranActionCategorieCause_sansSecret() {
        val lines = mutableListOf<Pair<String, String>>(); UiLog.sink = { t, m -> lines += t to m }
        UiLog.failure("home", "ma-liste", java.net.UnknownHostException("x.invalid"), url = "http://h.tv:8080/movie/jean/s3cret/12.mkv?token=abc")
        val (tag, msg) = lines.single()
        assertEquals("ui.home", tag)
        assertTrue(msg, msg.startsWith("ma-liste · HOST_NOT_FOUND · UnknownHostException"))
        assertFalse(msg.contains("jean")); assertFalse(msg.contains("s3cret")); assertFalse(msg.contains("abc"))
        assertTrue(msg, msg.contains("h.tv:8080"))
    }

    @Test fun causeQuiPorteUneAdresse_estMasquee() {
        val lines = mutableListOf<String>(); UiLog.sink = { _, m -> lines += m }
        UiLog.failure("series", "episodes", java.io.IOException("echec http://h:8080/series/jean/s3cret/1.mkv?password=zzz"))
        val msg = lines.single()
        assertFalse(msg, msg.contains("jean")); assertFalse(msg, msg.contains("s3cret")); assertFalse(msg, msg.contains("zzz"))
    }

    @Test fun annulation_relancee_etJamaisJournalisee() {
        val lines = mutableListOf<String>(); UiLog.sink = { _, m -> lines += m }
        assertThrows(CancellationException::class.java) { runBlocking { uiAttempt("home", "x") { throw CancellationException() } } }
        assertTrue(lines.isEmpty())
    }

    @Test fun uiAttempt_succes_rendLaValeur_echec_journaliseEtRendFailure() = runBlocking {
        val lines = mutableListOf<String>(); UiLog.sink = { _, m -> lines += m }
        assertEquals(7, uiAttempt("home", "ok") { 7 }.getOrNull())
        assertTrue(lines.isEmpty())
        val r = uiAttempt("home", "ko") { error("boum") }
        assertTrue(r.isFailure)
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("ko · OTHER · IllegalStateException: boum"))
    }

    @Test fun images_uneSeuleLigneParVisite_hotesMasques() {
        val lines = mutableListOf<Pair<String, String>>(); UiLog.sink = { t, m -> lines += t to m }
        val t = ImageFailureTally("movie")
        repeat(3) { t.note("http://jean:s3cret@img.a.tv:8080/movie/jean/s3cret/$it.jpg?token=abc") }
        repeat(2) { t.note("https://cdn.b.tv/p/$it.png") }
        t.flush()
        val (tag, msg) = lines.single()
        assertEquals("ui.movie", tag)
        assertTrue(msg, msg.contains("5"))
        assertTrue(msg, msg.contains("img.a.tv:8080") && msg.contains("cdn.b.tv"))
        assertFalse(msg.contains("jean")); assertFalse(msg.contains("s3cret")); assertFalse(msg.contains("abc"))
    }

    @Test fun images_sansEchec_aucuneLigne_etPasDeDoublon() {
        val lines = mutableListOf<String>(); UiLog.sink = { _, m -> lines += m }
        val t = ImageFailureTally("movie")
        t.flush()
        assertTrue(lines.isEmpty())
        t.note("http://a.tv/x.jpg"); t.flush(); t.flush()
        assertEquals(1, lines.size)
    }

    @Test fun images_troisHotesAuPlus() {
        val lines = mutableListOf<String>(); UiLog.sink = { _, m -> lines += m }
        val t = ImageFailureTally("movie")
        listOf("a", "b", "c", "d", "e").forEach { t.note("http://$it.tv/x.jpg") }
        t.flush()
        val msg = lines.single()
        assertTrue(msg, msg.contains("a.tv") && msg.contains("c.tv"))
        assertFalse(msg, msg.contains("d.tv")); assertFalse(msg, msg.contains("e.tv"))
    }
}

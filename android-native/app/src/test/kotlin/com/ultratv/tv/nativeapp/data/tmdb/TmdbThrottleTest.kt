package com.ultratv.tv.nativeapp.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbThrottleTest {
    @Test fun retryAfter_secondesBornees_etDefautSansEntete() {
        assertEquals(30L, TmdbRateLimit.parseRetryAfter("30"))
        assertEquals(30L, TmdbRateLimit.parseRetryAfter(" 30 "))
        assertEquals(TmdbRateLimit.DEFAULT_SEC, TmdbRateLimit.parseRetryAfter(null))
        assertEquals(TmdbRateLimit.DEFAULT_SEC, TmdbRateLimit.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(TmdbRateLimit.MIN_SEC, TmdbRateLimit.parseRetryAfter("0"))
        assertEquals(TmdbRateLimit.MAX_SEC, TmdbRateLimit.parseRetryAfter("999999"))
    }

    @Test fun apres429_pauseJusquAuTermeDeRetryAfter() {
        var now = 1_000_000L
        val l = TmdbRateLimit { now }
        assertEquals(0L, l.remainingMs())
        assertEquals(20L, l.onRateLimited("20"))
        assertEquals(20_000L, l.remainingMs())
        now += 19_999; assertEquals(1L, l.remainingMs())
        now += 1; assertEquals(0L, l.remainingMs())
    }

    @Test fun unSecond429_neRaccourcitPasLaPause() {
        val l = TmdbRateLimit { 0L }
        l.onRateLimited("100")
        l.onRateLimited("10")
        assertEquals(100_000L, l.remainingMs())
    }

    // ── Filtre de titres ──

    @Test fun titresValides() {
        for (t in listOf("Inception", "Le Parrain", "Se7en", "12 Years a Slave", "Amélie", "Matrix 2", "Dune: Part Two", "Мастер и Маргарита", "千と千尋の神隠し"))
            assertTrue(t, TmdbTitleFilter.looksLikeTitle(t))
    }

    @Test fun moinsDeTroisLettres_rejete() {
        for (t in listOf("", "  ", "A", "ab", "A1", "2 B", "!!"))
            assertFalse("«$t»", TmdbTitleFilter.looksLikeTitle(t))
    }

    @Test fun queDesChiffres_rejete() {
        for (t in listOf("2024", "123456", "1 2 3", "007")) assertFalse(t, TmdbTitleFilter.looksLikeTitle(t))
    }

    @Test fun dates_rejetees() {
        for (t in listOf("2026-11-15", "15/11/2026", "15.11.2026", "15 novembre 2026", "Nov 15", "3rd Mar, 2026", "15 Nov", "1er janvier 2026"))
            assertFalse(t, TmdbTitleFilter.looksLikeTitle(t))
    }

    @Test fun moisSansNombre_resteUnTitrePossible() {
        // « May », « Mars » peuvent être des films : seule une vraie date (avec un nombre) est écartée.
        assertTrue(TmdbTitleFilter.looksLikeTitle("May"))
        assertTrue(TmdbTitleFilter.looksLikeTitle("Mars"))
    }
}

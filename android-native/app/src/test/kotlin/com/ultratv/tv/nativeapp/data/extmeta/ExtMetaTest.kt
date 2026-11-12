package com.ultratv.tv.nativeapp.data.extmeta

import com.ultratv.tv.nativeapp.data.tmdb.ExternalIds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val H = 3_600_000L
private const val D = 24 * H

/** Faux Worker : réponses par chemin, compteur d'appels, panne réseau à la demande. */
private class FakeExtApi(var enabled: Boolean = true, var fail: Boolean = false, val answer: (String) -> ExtResponse) : ExtMetaApi {
    val calls = mutableListOf<String>()
    override fun isEnabled() = enabled
    override suspend fun get(pathAndQuery: String): ExtResponse? {
        calls += pathAndQuery
        if (fail) throw java.io.IOException("réseau")
        return answer(pathAndQuery)
    }
}

class ExtMetaParserTest {
    @Test fun fanart_httpsSeulement_champsNulsAbsents() {
        val a = ExtMetaParser.fanart("""{"logo":"https://img.example/l.png","background":"http://img.example/b.jpg","thumb":null}""")!!
        assertEquals("https://img.example/l.png", a.logo); assertNull(a.background); assertNull(a.thumb)
        assertTrue(ExtMetaParser.fanart("""{"logo":null,"background":null,"thumb":null}""")!!.isEmpty)
        assertNull(ExtMetaParser.fanart("pas du json"))
    }

    @Test fun tvShow_episodesPrecedentEtProchain() {
        val s = ExtMetaParser.tvShow(
            """{"id":82,"status":"Running","network":"Canal Fictif","previous":{"season":2,"number":4,"name":"Brouillard","airAt":1700000000000,"image":null,"summary":"Résumé"},"next":{"season":2,"number":5,"name":null,"airAt":null}}""",
        )!!
        assertEquals(82, s.id); assertEquals("Canal Fictif", s.network); assertFalse(s.ended)
        assertEquals(TvEpisode(2, 4, "Brouillard", 1_700_000_000_000L, null, "Résumé"), s.previous)
        assertEquals(TvEpisode(2, 5, null, null), s.next)
    }

    @Test fun tvShow_inconnue_idNul_etTermineeReconnue() {
        val s = ExtMetaParser.tvShow("""{"id":null,"status":null,"network":null,"previous":null,"next":null}""")!!
        assertNull(s.id); assertNull(s.previous); assertNull(s.next)
        assertTrue(ExtMetaParser.tvShow("""{"id":1,"status":"Ended","previous":null,"next":null}""")!!.ended)
    }

    @Test fun team_connueInconnueOuIllisible() {
        val t = ExtMetaParser.team("""{"team":{"name":"Club Fictif","short":"CFI","badge":"https://img.example/b.png","league":"Ligue Fictive","sport":"Soccer"}}""")!!
        assertEquals("https://img.example/b.png", t.team!!.badge); assertEquals("Club Fictif", t.team!!.name)
        assertNull(ExtMetaParser.team("""{"team":null}""")!!.team)
        assertNull(ExtMetaParser.team("""{"error":"upstream"}"""))
        assertNull(ExtMetaParser.team("<html>"))
    }
}

class TvMazeLogicTest {
    private val now = 100 * D
    private fun ep(n: Int, at: Long?) = TvEpisode(1, n, "E$n", at)
    private fun show(prev: Long? = null, next: Long? = null, status: String = "Running") =
        TvShowInfo(1, status, null, prev?.let { ep(1, it) }, next?.let { ep(2, it) })

    @Test fun lineOf_prochainPuisTermineePuisDernier() {
        assertTrue(TvMazeLogic.lineOf(show(prev = now - D, next = now + D)) is EpisodeLine.Next)
        assertEquals(EpisodeLine.Ended, TvMazeLogic.lineOf(show(prev = now - D, status = "Ended")))
        assertEquals(EpisodeLine.Last(ep(1, now - D)), TvMazeLogic.lineOf(show(prev = now - D)))
        assertNull(TvMazeLogic.lineOf(show()))
        assertNull(TvMazeLogic.lineOf(null))
    }

    @Test fun candidates_enCoursPuisFavoris_sansDoublon_bornes() {
        assertEquals(listOf("b", "a", "c"), TvMazeLogic.candidates(listOf("b", "a"), listOf("a", "c", " ")))
        assertEquals(20, TvMazeLogic.candidates((1..15).map { "p$it" }, (1..15).map { "f$it" }).size)
    }

    @Test fun pickOf_fenetreDeSeptJours() {
        assertEquals(true, TvMazeLogic.pickOf(show(prev = now - 6 * D), now)!!.aired)
        assertNull(TvMazeLogic.pickOf(show(prev = now - 8 * D), now))
        assertEquals(false, TvMazeLogic.pickOf(show(prev = now - 30 * D, next = now + 3 * D), now)!!.aired)
        assertNull(TvMazeLogic.pickOf(show(next = now + 8 * D), now))
        // Date inconnue : rien.
        assertNull(TvMazeLogic.pickOf(TvShowInfo(1, "Running", null, ep(1, null), ep(2, null)), now))
    }

    @Test fun select_diffusesRecentsDabordPuisAVenir_parDate() {
        val out = TvMazeLogic.select(
            listOf(
                "loin" to show(next = now + 5 * D),
                "vieux" to show(prev = now - 20 * D),
                "hier" to show(prev = now - D),
                "demain" to show(next = now + D),
                "avantHier" to show(prev = now - 2 * D),
                "inconnue" to null,
            ),
            now,
        )
        assertEquals(listOf("hier", "avantHier", "demain", "loin"), out.map { it.first })
    }
}

class MemoCacheTest {
    @Test fun valeurNulleMemorisee_ioExceptionNon() = runBlocking {
        val c = MemoCache<String>(max = 10, ttlMs = H)
        var n = 0
        assertNull(c.getOrLoad("k", 0) { n++; null })
        assertNull(c.getOrLoad("k", 10) { n++; "x" })
        assertEquals(1, n)
        assertNull(c.getOrLoad("e", 0) { n++; throw SkipCache() })
        assertEquals("ok", c.getOrLoad("e", 0) { n++; "ok" })
        assertEquals(3, n)
    }

    @Test fun perime_recharge_etLruBorne() = runBlocking {
        val c = MemoCache<String>(max = 2, ttlMs = H)
        c.put("a", 0, "1"); c.put("b", 0, "2"); c.put("c", 0, "3")
        assertNull(c.peek("a", 0))
        assertEquals("2", c.peek("b", 0)!!.value)
        assertNull(c.peek("b", H))
    }

    @Test fun demandesSimultanees_unSeulChargement() = runBlocking {
        val c = MemoCache<String>(max = 10, ttlMs = H)
        var n = 0
        val r = (1..5).map { async { c.getOrLoad("k", 0) { n++; delay(20); "v" } } }.awaitAll()
        assertEquals(List(5) { "v" }, r)
        assertEquals(1, n)
    }
}

class FanartRepositoryTest {
    @Test fun film_logoEtFond_puisCache() = runBlocking {
        val api = FakeExtApi { ExtResponse(200, """{"logo":"https://img.example/l.png","background":"https://img.example/b.jpg","thumb":null}""") }
        val r = FanartRepository(api)
        assertEquals("https://img.example/l.png", r.movie(27205, "fr", now = 0)!!.logo)
        r.movie(27205, "fr", now = 1)
        assertEquals(listOf("fanart/movie/27205?lang=fr"), api.calls)
    }

    @Test fun nonConfigure503_rienPuisPlusDAppelPendant30Min() = runBlocking {
        val api = FakeExtApi { ExtResponse(503, null) }
        val r = FanartRepository(api)
        assertNull(r.tv(121361, "fr", now = 0))
        assertNull(r.tv(999, "fr", now = 60_000))
        assertEquals(1, api.calls.size)
        r.tv(999, "fr", now = 31 * 60_000L)
        assertEquals(2, api.calls.size)
    }

    @Test fun nonAppaire_ouPanne_rien_sansMemoriser() = runBlocking {
        val api = FakeExtApi(enabled = false) { ExtResponse(200, """{"logo":"https://img.example/l.png"}""") }
        val r = FanartRepository(api)
        assertNull(r.movie(1, "fr", now = 0)); assertTrue(api.calls.isEmpty())
        api.enabled = true; api.fail = true
        assertNull(r.movie(1, "fr", now = 0))
        api.fail = false
        assertEquals("https://img.example/l.png", r.movie(1, "fr", now = 0)!!.logo)
    }

    @Test fun langueInvalide_anglais() = runBlocking {
        val api = FakeExtApi { ExtResponse(404, null) }
        FanartRepository(api).movie(5, "x", now = 0)
        assertEquals(listOf("fanart/movie/5?lang=en"), api.calls)
    }
}

class TvMazeRepositoryTest {
    private val body = """{"id":5,"status":"Running","network":null,"previous":null,"next":{"season":3,"number":1,"name":"Retour","airAt":1800000000000}}"""

    @Test fun imdbDabord_sinonTvdb_sinonRien() = runBlocking {
        val api = FakeExtApi { ExtResponse(200, body) }
        val r = TvMazeRepository(api)
        assertEquals(5, r.show(ExternalIds("tt0944947", 121361), now = 0)!!.id)
        r.show(ExternalIds(null, 121361), now = 0)
        assertNull(r.show(ExternalIds(null, null), now = 0))
        assertEquals(listOf("tvmaze/show?imdb=tt0944947", "tvmaze/show?tvdb=121361"), api.calls)
    }

    @Test fun cacheSixHeures_puisNouvelAppel() = runBlocking {
        val api = FakeExtApi { ExtResponse(200, body) }
        val r = TvMazeRepository(api)
        r.show(ExternalIds("tt0944947", null), now = 0)
        r.show(ExternalIds("tt0944947", null), now = 5 * H)
        assertEquals(1, api.calls.size)
        r.show(ExternalIds("tt0944947", null), now = 7 * H)
        assertEquals(2, api.calls.size)
    }

    @Test fun serieInconnue_memorisee_erreurServeurNon() = runBlocking {
        var code = 200
        val api = FakeExtApi { ExtResponse(code, if (code == 200) """{"id":null,"status":null,"network":null,"previous":null,"next":null}""" else null) }
        val r = TvMazeRepository(api)
        assertNull(r.show(ExternalIds("tt0000001", null), now = 0))
        assertNull(r.show(ExternalIds("tt0000001", null), now = 1))
        assertEquals(1, api.calls.size)
        code = 502
        assertNull(r.show(ExternalIds("tt0000002", null), now = 0))
        assertNull(r.show(ExternalIds("tt0000002", null), now = 1))
        assertEquals(3, api.calls.size)
    }
}

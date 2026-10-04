package com.ultratv.tv.nativeapp.data.tmdb

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val D = 24L * 3_600_000

private class PDao : TmdbDao {
    val rows = mutableMapOf<Triple<String, Long, String>, TmdbInfoEntity>()
    override suspend fun get(kind: String, pid: Long, rid: String) = rows[Triple(kind, pid, rid)]
    override suspend fun upsert(row: TmdbInfoEntity) { rows[Triple(row.kind, row.providerId, row.remoteId)] = row }
    override suspend fun deleteOlderThan(cutoffMs: Long) {}
}

private class PApi(var enabled: Boolean = true, val answers: (TmdbQuery) -> String? = { "/x.jpg" }) : TmdbApi {
    val calls = mutableListOf<TmdbQuery>(); val running = AtomicInteger(); var maxRunning = 0; var fail = false; var slow = false
    override fun isEnabled() = enabled
    override suspend fun searchId(kind: TmdbKind, query: TmdbQuery, lang: String): Int? = null
    override suspend fun details(kind: TmdbKind, id: Int, lang: String): TmdbDetails? = null
    override suspend fun searchPoster(kind: TmdbKind, query: TmdbQuery, lang: String): String? {
        synchronized(calls) { calls += query }
        val n = running.incrementAndGet(); synchronized(this) { maxRunning = maxOf(maxRunning, n) }
        try { if (slow) delay(30); if (fail) throw java.io.IOException("x"); return answers(query) } finally { running.decrementAndGet() }
    }
}

class PosterResolverTest {
    private var now = 0L
    private fun resolver(api: TmdbApi, dao: TmdbDao) = PosterResolver(api, dao, { "fr-FR" }, { now })

    @Test fun parser_posterPath_anneeProcheSinonPremierAvecAffiche() {
        val j = """{"results":[{"id":1,"release_date":"1999-01-01"},{"id":2,"poster_path":"/a.jpg","release_date":"1999-01-01"},{"id":3,"poster_path":"/b.jpg","release_date":"2010-01-01"}]}"""
        assertEquals("/b.jpg", TmdbParser.searchBestPoster(j, 2010)); assertEquals("/a.jpg", TmdbParser.searchBestPoster(j, null))
        assertNull(TmdbParser.searchBestPoster("""{"results":[{"id":1,"poster_path":null}]}""", null)); assertNull(TmdbParser.searchBestPoster("nope", null))
    }
    @Test fun nonAppaire_aucuneRequete() = runBlocking {
        val api = PApi(enabled = false); val dao = PDao()
        assertNull(resolver(api, dao).resolve(TmdbKind.MOVIE, "Inception", 2010)); assertTrue(api.calls.isEmpty() && dao.rows.isEmpty())
    }
    @Test fun trouve_urlW342_titreNettoye_puisCacheSansReseau() = runBlocking {
        val api = PApi(); val dao = PDao(); val r = resolver(api, dao)
        assertEquals("https://image.tmdb.org/t/p/w342/x.jpg", r.resolve(TmdbKind.MOVIE, "FR - Inception (2010) HD", null))
        assertEquals("Inception", api.calls[0].title); assertEquals(2010, api.calls[0].year)
        // nouveau résolveur (mémoire vide) : lu depuis le cache persistant
        assertEquals("https://image.tmdb.org/t/p/w342/x.jpg", resolver(api, dao).resolve(TmdbKind.MOVIE, "Inception", 2010)); assertEquals(1, api.calls.size)
    }
    @Test fun replisSansAnnee_quandRienAvecAnnee() = runBlocking {
        val api = PApi { if (it.year != null) null else "/y.jpg" }
        assertEquals("https://image.tmdb.org/t/p/w342/y.jpg", resolver(api, PDao()).resolve(TmdbKind.TV, "Serie (2020)", null))
        assertEquals(listOf(2020, null), api.calls.map { it.year })
    }
    @Test fun rienTrouve_estMemorise30Jours() = runBlocking {
        val api = PApi { null }; val dao = PDao()
        assertNull(resolver(api, dao).resolve(TmdbKind.MOVIE, "Inconnu", null)); assertEquals(1, api.calls.size)
        now = 29 * D; assertNull(resolver(api, dao).resolve(TmdbKind.MOVIE, "Inconnu", null)); assertEquals(1, api.calls.size)
        now = 31 * D; resolver(api, dao).resolve(TmdbKind.MOVIE, "Inconnu", null); assertEquals(2, api.calls.size)
    }
    @Test fun erreurReseau_nEstPasMemorisee() = runBlocking {
        val api = PApi().also { it.fail = true }; val dao = PDao()
        assertNull(resolver(api, dao).resolve(TmdbKind.MOVIE, "Titre", null)); assertTrue(dao.rows.isEmpty())
    }
    @Test fun maxTroisRequetesSimultanees() = runBlocking {
        val api = PApi().also { it.slow = true }; val r = resolver(api, PDao())
        (1..12).map { i -> async(kotlinx.coroutines.Dispatchers.Default) { r.resolve(TmdbKind.MOVIE, "Film numero $i", null) } }.awaitAll()
        assertTrue("max=${api.maxRunning}", api.maxRunning in 1..3); assertEquals(12, api.calls.size)
    }
}

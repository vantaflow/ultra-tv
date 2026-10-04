package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.VodInfoEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val DAY = 24L * 3_600_000

private val SEARCH = """{"results":[{"id":11,"release_date":"1999-03-01"},{"id":22,"release_date":"2010-07-16"},{"id":33}]}"""
private val DETAIL = """{"id":22,"overview":"Un rêve dans un rêve.","poster_path":"/p.jpg","backdrop_path":"/b.jpg","vote_average":8.4,
 "original_language":"en",
 "credits":{"cast":[{"name":"Actrice A"},{"name":"Acteur B"}]},
 "videos":{"results":[{"site":"Vimeo","key":"vvvvvvvvvvv","type":"Trailer"},{"site":"YouTube","key":"teaser00000","type":"Teaser"},{"site":"YouTube","key":"trailer0000","type":"Trailer","official":true}]}}"""

class TmdbParserTest {
    @Test fun searchBest_anneeDonnee_choisitLeBonResultat() = assertEquals(22, TmdbParser.searchBest(SEARCH, 2010))
    @Test fun searchBest_sansAnnee_premierResultat() = assertEquals(11, TmdbParser.searchBest(SEARCH, null))
    @Test fun searchBest_aucunResultat_ouJsonInvalide_null() {
        assertNull(TmdbParser.searchBest("""{"results":[]}""", 2010)); assertNull(TmdbParser.searchBest("pas du json", null))
    }
    @Test fun details_extraitTout_etPrefereLaBandeAnnonceOfficielleYouTube() {
        val d = TmdbParser.details(DETAIL)!!
        assertEquals(22, d.tmdbId); assertEquals("trailer0000", d.trailerKey); assertEquals("en", d.originalLanguage)
        assertEquals(listOf("Actrice A", "Acteur B"), d.cast); assertEquals(8.4, d.rating!!, 0.001)
        assertEquals("/p.jpg", d.posterPath)
    }
    @Test fun details_sansId_null() = assertNull(TmdbParser.details("""{"overview":"x"}"""))
    @Test fun details_champsVides_sontNuls() {
        val d = TmdbParser.details("""{"id":5,"overview":"","poster_path":null,"vote_average":0}""")!!
        assertNull(d.overview); assertNull(d.posterPath); assertNull(d.rating); assertNull(d.trailerKey)
    }
}

class TmdbLogicTest {
    @Test fun query_nettoieLeTitreEtGardeLAnnee() {
        val q = tmdbQueryFor("FR - Inception (2010) HD", null)
        assertEquals("Inception", q.title); assertEquals(2010, q.year)
    }
    @Test fun query_anneeConnueDeLaSource_prioritaire() = assertEquals(2011, tmdbQueryFor("Titre (2010)", 2011).year)
    @Test fun sourceId_valide_seulementEntierPositif() {
        assertEquals(603, parseSourceTmdbId(" 603 ")); assertNull(parseSourceTmdbId("0")); assertNull(parseSourceTmdbId("abc")); assertNull(parseSourceTmdbId(null))
    }
    @Test fun isFresh_ttl30Jours_etNegatifPlusCourt_etChangementDeLangue() {
        val ok = TmdbInfoEntity("movie", 1, "a", tmdbId = 1, lang = "fr-FR", fetchedAt = 0)
        assertTrue(isFresh(ok, "fr-FR", 29 * DAY)); assertFalse(isFresh(ok, "fr-FR", 31 * DAY)); assertFalse(isFresh(ok, "en-US", DAY))
        val neg = ok.copy(tmdbId = null)
        assertTrue(isFresh(neg, "fr-FR", 2 * DAY)); assertFalse(isFresh(neg, "fr-FR", 4 * DAY))
    }
    @Test fun trailerKey_extraiteDesUrls() {
        assertEquals("trailer0000", TmdbTrailer.keyOf("https://www.youtube.com/watch?v=trailer0000"))
        assertEquals("trailer0000", TmdbTrailer.keyOf("https://youtu.be/trailer0000"))
        assertEquals("trailer0000", TmdbTrailer.keyOf("trailer0000"))
        assertNull(TmdbTrailer.keyOf("https://exemple.test/film.mp4"))
    }
    private val movie = MovieEntity(providerId = 1, remoteId = "r", name = "N", poster = null, categoryId = null, streamUrl = "u", container = null, year = null, rating = 0.0, plot = " ")
    private val t = TmdbInfoEntity("movie", 1, "r", tmdbId = 22, overview = "Synopsis", posterPath = "/p.jpg", backdropPath = "/b.jpg", rating = 8.0, cast = "A, B", trailerKey = "trailer0000")
    @Test fun mergeMovie_comblePosterSynopsisDistributionNote() {
        val m = mergeMovie(movie, t)
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", m.poster); assertEquals("Synopsis", m.plot); assertEquals("A, B", m.cast); assertEquals(8.0, m.rating!!, 0.0)
    }
    @Test fun mergeMovie_laSourceResteProritaire() {
        assertEquals("Source", mergeMovie(movie.copy(plot = "Source", poster = "http://p"), t).plot)
        assertEquals("http://p", mergeMovie(movie.copy(poster = "http://p"), t).poster)
    }
    @Test fun merge_sansTmdb_ouIntrouvable_inchange() {
        assertEquals(movie, mergeMovie(movie, null)); assertEquals(movie, mergeMovie(movie, t.copy(tmdbId = null)))
    }
    @Test fun mergeVodInfo_ajouteBandeAnnonceEtBackdrop_sansEcraserLaSource() {
        val src = VodInfoEntity(1, "r", plot = "Source")
        val v = mergeVodInfo(src, movie, t)!!
        assertEquals("Source", v.plot); assertEquals("https://www.youtube.com/watch?v=trailer0000", v.trailer); assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", v.backdrop)
        assertEquals("22", v.tmdbId)
    }
    @Test fun mergeSeries_comblePoster() {
        val s = SeriesEntity(providerId = 1, remoteId = "s", name = "S", poster = null, categoryId = null, year = null, rating = null, plot = null)
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", mergeSeries(s, t).poster)
    }
}

private class FakeDao : TmdbDao {
    val rows = mutableMapOf<Triple<String, Long, String>, TmdbInfoEntity>()
    override suspend fun get(kind: String, pid: Long, rid: String) = rows[Triple(kind, pid, rid)]
    override suspend fun upsert(row: TmdbInfoEntity) { rows[Triple(row.kind, row.providerId, row.remoteId)] = row }
    override suspend fun deleteOlderThan(cutoffMs: Long) {}
}

private class FakeApi(var enabled: Boolean = true, var fail: Boolean = false) : TmdbApi {
    var searches = 0; var detailCalls = 0; var lastId: Int? = null
    override fun isEnabled() = enabled
    override suspend fun searchId(kind: TmdbKind, query: TmdbQuery, lang: String): Int? { searches++; if (fail) throw java.io.IOException("x"); return 22 }
    override suspend fun details(kind: TmdbKind, id: Int, lang: String): TmdbDetails? {
        detailCalls++; lastId = id; if (fail) throw java.io.IOException("x")
        return TmdbDetails(id, "Synopsis", "/p.jpg", "/b.jpg", 8.0, listOf("A"), "trailer0000", "ja")
    }
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class TmdbRepositoryTest {
    private val movie = MovieEntity(providerId = 1, remoteId = "r", name = "Inception (2010)", poster = null, categoryId = null, streamUrl = "u", container = null, year = null, rating = null, plot = null)
    private fun repo(api: TmdbApi, dao: TmdbDao): TmdbRepository {
        val app = RuntimeEnvironment.getApplication()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.ultratv.tv.nativeapp.data.db.UltraDb::class.java).allowMainThreadQueries().build()
        kotlinx.coroutines.runBlocking { db.profileDao().insert(com.ultratv.tv.nativeapp.data.profile.ProfileEntity(name = "Principal", color = 1, initial = "P")) }
        val profiles = com.ultratv.tv.nativeapp.data.profile.ProfileRepository(db.profileDao(), com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        return TmdbRepository(api, dao, UserPreferencesStore(app, profiles))
    }

    @Test fun nonAppaire_desactive_aucuneRequete_etRienEnCache() = runBlocking {
        val api = FakeApi(enabled = false); val dao = FakeDao()
        assertNull(repo(api, dao).forMovie(movie)); assertEquals(0, api.searches); assertTrue(dao.rows.isEmpty())
    }
    @Test fun idDeLaSource_utiliseSansRecherche() = runBlocking {
        val api = FakeApi(); val r = repo(api, FakeDao()).forMovie(movie, sourceTmdbId = "603")
        assertEquals(0, api.searches); assertEquals(603, api.lastId); assertEquals(603, r!!.tmdbId)
    }
    @Test fun sansIdSource_rechercheParTitre_puisCache() = runBlocking {
        val api = FakeApi(); val dao = FakeDao(); val rp = repo(api, dao)
        val first = rp.forMovie(movie, now = 1_000)!!
        rp.forMovie(movie, now = 2_000)
        assertEquals(1, api.searches); assertEquals(22, first.tmdbId); assertEquals("ja", first.originalLanguage)
        assertEquals("ja", rp.cachedOriginalLanguage(TmdbKind.MOVIE, 1, "r"))
    }
    @Test fun apres30Jours_recharge() = runBlocking {
        val api = FakeApi(); val rp = repo(api, FakeDao())
        rp.forMovie(movie, now = 0); rp.forMovie(movie, now = 31 * DAY)
        assertEquals(2, api.searches)
    }
    @Test fun erreurReseau_rendLeCachePerime() = runBlocking {
        val api = FakeApi(); val rp = repo(api, FakeDao())
        rp.forMovie(movie, now = 0); api.fail = true
        assertEquals(22, rp.forMovie(movie, now = 40 * DAY)!!.tmdbId)
    }
    @Test fun erreurReseauSansCache_null() = runBlocking {
        assertNull(repo(FakeApi(fail = true), FakeDao()).forMovie(movie))
    }
}

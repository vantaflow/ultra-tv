package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.data.db.CatalogLite
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trakt « complet » : bibliothèque étendue, À suivre, import des reprises, écritures, réglages. */
@OptIn(ExperimentalCoroutinesApi::class)
class TraktCompleteTest {
    private companion object {
        val BODY = """
        {"linked":true,"updatedAt":5,
         "watchlist":[{"type":"show","tmdb":4607,"year":2004,"title":"Lost","keys":["lost"]}],
         "recommendations":[],
         "watched":{"movies":[{"type":"movie","title":"Alien","year":1979,"keys":["alien"]}],
                    "shows":[{"type":"show","title":"Lost","year":2004,"keys":["lost"],"episodes":["1x1","1x2"],"lastWatchedAt":200},
                             {"type":"show","title":"Dark","year":2017,"keys":["dark"],"episodes":["1x1"],"lastWatchedAt":900}]},
         "ratings":{"movies":[{"type":"movie","title":"Alien","year":1979,"keys":["alien"],"rating":9}],"shows":[{"type":"show","title":"Lost","year":2004,"keys":["lost"],"rating":0}]},
         "lists":[{"id":"100","name":"Soirée","items":[{"type":"movie","title":"Heat","year":1995,"keys":["heat"]}]},{"id":"","name":"x","items":[]}],
         "playback":[{"type":"movie","title":"Dune","year":2021,"keys":["dune"],"progress":42.5,"pausedAt":1000,"runtime":155},
                     {"type":"show","title":"Lost","year":2004,"keys":["lost"],"progress":10,"pausedAt":2000,"runtime":null,"season":2,"episode":3},
                     {"type":"show","title":"Bad","keys":["bad"],"progress":10,"pausedAt":1}]}
        """.trimIndent()
    }

    // ───────── Bibliothèque ─────────

    @Test fun parse_champsEtendus() {
        val lib = TraktLibrary.parse(BODY)
        assertEquals(listOf(200L, 900L), lib.watchedShows.map { it.lastWatchedAt })
        assertEquals(9, lib.ratingFor(false, "FR - Alien (1979) 4K", 1979))
        assertNull("note 0 ignorée", lib.ratingFor(true, "Lost", 2004))
        assertEquals(listOf("100"), lib.lists.map { it.id })
        assertEquals(2, lib.playback.size)
        assertEquals(155, lib.playback[0].runtimeMin)
        assertEquals(2 to 3, lib.playback[1].season to lib.playback[1].episode)
        assertTrue(lib.isInWatchlist(true, "|FR| Lost", 2004))
        assertFalse(lib.isInWatchlist(false, "Lost", 2004))
    }

    @Test fun parse_ancienneReponse_sansNouveauxChamps() {
        val lib = TraktLibrary.parse("""{"linked":true,"watchlist":[],"recommendations":[],"watched":{"movies":[],"shows":[]}}""")
        assertTrue(lib.linked)
        assertTrue(lib.lists.isEmpty() && lib.playback.isEmpty() && lib.ratedMovies.isEmpty())
    }

    @Test fun correctionsLocales_watchlistVuNote() {
        val lib = TraktLibrary.parse(BODY)
        val a = lib.withWatchlist(false, "Heat", 1995, add = true)
        assertTrue(a.isInWatchlist(false, "Heat", 1995))
        assertFalse(a.withWatchlist(false, "Heat", 1995, add = false).isInWatchlist(false, "Heat", 1995))
        assertFalse(lib.withMovieWatched("Alien", 1979, watched = false).isMovieWatched("Alien", 1979))
        assertTrue(lib.withMovieWatched("Heat", 1995, watched = true).isMovieWatched("Heat", 1995))
        assertEquals(7, lib.withRating(false, "Alien", 1979, 7).ratingFor(false, "Alien", 1979))
        assertNull(lib.withRating(false, "Alien", 1979, 0).ratingFor(false, "Alien", 1979))
        val eps = lib.withEpisodes("Lost", 2004, setOf("1x3"), watched = true, now = 5_000)
        assertEquals(setOf("1x1", "1x2", "1x3"), eps.watchedEpisodes("Lost", 2004))
        assertEquals(5_000L, eps.watchedShows.first { it.item.title == "Lost" }.lastWatchedAt)
        assertEquals(emptySet<String>(), eps.withEpisodes("Lost", 2004, setOf("1x1", "1x2", "1x3"), watched = false).watchedEpisodes("Lost", 2004))
        assertEquals(setOf("2x1"), lib.withEpisodes("Fargo", 2014, setOf("2x1"), watched = true).watchedEpisodes("Fargo", 2014))
    }

    @Test fun repository_patch_seulementSiLie() = runBlocking {
        val src = object : TraktLibrarySource {
            var body: String? = BODY
            override val isPaired = true
            override suspend fun fetch(lang: String) = body
        }
        val repo = TraktLibraryRepository(src) { 1L }
        repo.patch { it.withWatchlist(false, "Heat", 1995, true) }
        assertSame("non chargé : rien", TraktLibrary.EMPTY, repo.library.value)
        repo.refreshIfStale("fr")
        repo.patch { it.withWatchlist(false, "Heat", 1995, true) }
        assertTrue(repo.library.value.isInWatchlist(false, "Heat", 1995))
    }

    // ───────── À suivre ─────────

    private fun refs(vararg k: String) = k.map { EpisodeRef.parse(it)!! }

    @Test fun upNext_suivantDisponibleApresLePlusAvance() {
        assertEquals(EpisodeRef(1, 3), TraktUpNext.nextEpisode(setOf("1x1", "1x2"), refs("1x1", "1x2", "1x3", "2x1")))
        // Fin de saison → première de la suivante.
        assertEquals(EpisodeRef(2, 1), TraktUpNext.nextEpisode(setOf("1x1", "1x2"), refs("2x1", "1x2", "1x1")))
        // Le plus avancé compte, pas les trous d'avant.
        assertEquals(EpisodeRef(3, 2), TraktUpNext.nextEpisode(setOf("1x1", "3x1"), refs("1x2", "3x1", "3x2")))
        // Spéciaux (saison 0) ignorés.
        assertEquals(EpisodeRef(1, 2), TraktUpNext.nextEpisode(setOf("0x5", "1x1"), refs("0x6", "1x2")))
    }

    @Test fun upNext_aJourOuSuiteAbsenteDeLaPlaylist_rien() {
        assertNull(TraktUpNext.nextEpisode(setOf("1x1", "1x2"), refs("1x1", "1x2")))
        assertNull(TraktUpNext.nextEpisode(emptySet(), refs("1x1")))
        assertNull(TraktUpNext.nextEpisode(setOf("1x1"), emptyList()))
    }

    @Test fun upNext_candidates_plusRecentesDAbord() {
        val lib = TraktLibrary.parse(BODY)
        assertEquals(listOf("Dark", "Lost"), TraktUpNext.candidates(lib).map { it.item.title })
        assertEquals(listOf("Dark"), TraktUpNext.candidates(lib, 1).map { it.item.title })
    }

    @Test fun lignes_listesPersonnelles_seulementLeDisponible() {
        val lib = TraktLibrary.parse(BODY)
        val idx = HashMap<String, MutableList<CatalogLite>>()
        TraktAvailability.indexInto(idx, setOf("heat"), listOf(CatalogLite(7, "FR - Heat (1995)", null, 1995, null)))
        val rows = TraktAvailability.rows(lib, idx, emptyMap())
        assertEquals(listOf("Soirée"), rows.lists.map { it.name })
        assertEquals(listOf(7L), rows.lists[0].cards.map { it.id })
        val none = TraktAvailability.rows(lib, emptyMap(), emptyMap())
        assertTrue("liste sans élément disponible : pas de rangée", none.lists.isEmpty())
    }

    @Test fun codec_listesEtAsuivre_allerRetour() {
        val c = TraktCard(true, 3, "Lost", "p", 2004, 8.1)
        val rows = TraktRows(emptyList(), emptyList(), lists = listOf(TraktListRow("100", "Soirée", listOf(c.copy(isShow = false, id = 7)))), upNext = listOf(TraktUpNextCard(c, 2, 5)))
        val back = TraktRowsCodec.decode(TraktRowsCodec.encode(mapOf(1L to rows)))[1L]
        assertEquals(rows, back)
    }

    @Test fun reglages_listesMasquees_auPlusCinq() {
        val lists = (1..7).map { TraktListRow("$it", "L$it", listOf(TraktCard(false, it.toLong(), "t", null, null, null))) }
        val r = TraktRows(emptyList(), emptyList(), lists = lists)
        val shown = TraktCatalogMatcher.visibleLists(r, TraktPrefs(hiddenLists = setOf("2")))
        assertEquals(listOf("1", "3", "4", "5", "6"), shown.lists.map { it.id })
        val p = TraktPrefs(autoWatched = false, hiddenLists = setOf("2", "9"))
        assertEquals(p, TraktPrefs.decode(p.encode()))
        assertEquals(TraktPrefs(), TraktPrefs.decode("{oops"))
    }

    // ───────── Reprises (import) ─────────

    private fun hit(progress: Double = 50.0, pausedAt: Long = 10_000, dur: Long = 6_000_000) =
        PlaybackHit(1, "MOVIE", "42", "Dune", null, "http://s/42.mkv", null, progress, pausedAt, dur)

    private fun local(watchedAt: Long, pos: Long = 600_000, dur: Long = 0) =
        WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "42", title = "Dune (local)", poster = "p", streamUrl = "http://s/42.mkv", positionMs = pos, durationMs = dur, watchedAt = watchedAt)

    @Test fun reprise_sansHistoriqueLocal_importee() {
        val row = TraktResume.decide(null, hit(), profileId = 3, now = 20_000)!!
        assertEquals(3_000_000L, row.positionMs)
        assertEquals(6_000_000L, row.durationMs)
        assertEquals(10_000L, row.watchedAt)
        assertEquals(3L, row.profileId)
    }

    @Test fun reprise_positionLocalePlusRecente_jamaisEcrasee() {
        assertNull(TraktResume.decide(local(watchedAt = 10_000), hit(pausedAt = 10_000), 1, 20_000))
        assertNull(TraktResume.decide(local(watchedAt = 15_000), hit(pausedAt = 10_000), 1, 20_000))
    }

    @Test fun reprise_positionLocalePlusAncienne_remplacee_avecSaDuree() {
        val row = TraktResume.decide(local(watchedAt = 5_000, dur = 4_000_000), hit(progress = 25.0), 1, 20_000)!!
        assertEquals(1_000_000L, row.positionMs)
        assertEquals("Dune (local)", row.title)
    }

    @Test fun reprise_dureeInconnueOuPositionInutile_ignoree() {
        assertNull(TraktResume.decide(null, hit(dur = 0), 1, 20_000))
        assertNull(TraktResume.decide(null, hit(progress = 0.1), 1, 20_000))
        assertNull(TraktResume.decide(null, hit(progress = 99.5), 1, 20_000))
    }

    @Test fun reprise_dureeDeLaSource() {
        assertEquals(6_130_000L, TraktResume.parseDurationMs("01:42:10"))
        assertEquals(2_530_000L, TraktResume.parseDurationMs("42:10"))
        assertEquals(5_700_000L, TraktResume.parseDurationMs("95"))
        assertEquals(5_700_000L, TraktResume.parseDurationMs("95 min"))
        assertEquals(0L, TraktResume.parseDurationMs("n/a"))
        assertEquals(0L, TraktResume.parseDurationMs(null))
    }

    @Test fun reprise_clesImportees_borneesEtRelues() {
        val keys = (1..450).map { "k$it" }
        val back = TraktProgressImporter.decodeKeys(TraktProgressImporter.encodeKeys(keys))
        assertEquals(400, back.size)
        assertTrue("k450" in back && "k1" !in back)
        assertTrue(TraktProgressImporter.decodeKeys("not json").isEmpty())
    }

    // ───────── Écritures ─────────

    private class FakeWrite(var result: TraktWriteResult = TraktWriteResult.OK) : TraktWriteTransport {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        override suspend fun post(path: String, body: JSONObject): TraktWriteResult { calls += path to body; return result }
    }

    private class CountingSource : TraktLibrarySource {
        var calls = 0
        override val isPaired = true
        override suspend fun fetch(lang: String): String { calls++; return BODY }
    }

    @Test fun ecriture_ok_corrigeToutDeSuiteEtRelitLeWorker() = runTest {
        val src = CountingSource()
        val repo = TraktLibraryRepository(src) { 1L }
        repo.refreshIfStale("fr")
        val t = FakeWrite()
        val a = TraktActions(t, repo, { "fr" }, CoroutineScope(StandardTestDispatcher(testScheduler)))
        assertEquals(TraktWriteResult.OK, a.setWatchlist(TraktWork(false, "Heat", 1995, 949), add = true))
        assertTrue(repo.library.value.isInWatchlist(false, "Heat", 1995))
        val (path, body) = t.calls.single()
        assertEquals("watchlist", path)
        assertEquals(mapOf("title" to "Heat", "year" to 1995, "tmdb" to 949, "action" to "add", "type" to "movie"), body.keys().asSequence().associateWith { body.get(it) })
        advanceUntilIdle()
        assertEquals("relecture forcée malgré les 15 min", 2, src.calls)
    }

    @Test fun ecriture_echec_rienNeChange() = runTest {
        val src = CountingSource()
        val repo = TraktLibraryRepository(src) { 1L }
        repo.refreshIfStale("fr")
        val a = TraktActions(FakeWrite(TraktWriteResult.UNMATCHED), repo, { "fr" }, CoroutineScope(StandardTestDispatcher(testScheduler)))
        assertEquals(TraktWriteResult.UNMATCHED, a.rate(TraktWork(false, "Alien", 1979, null), 3))
        advanceUntilIdle()
        assertEquals(9, repo.library.value.ratingFor(false, "Alien", 1979))
        assertEquals(1, src.calls)
    }

    @Test fun ecriture_episodes_corpsEtCorrection() = runTest {
        val repo = TraktLibraryRepository(CountingSource()) { 1L }
        repo.refreshIfStale("fr")
        val t = FakeWrite()
        val a = TraktActions(t, repo, { "fr" }, CoroutineScope(StandardTestDispatcher(testScheduler)))
        a.setEpisodesWatched(TraktWork(true, "Lost", 2004, null), listOf(EpisodeRef(1, 3), EpisodeRef(1, 3), EpisodeRef(1, 4)), watched = true)
        val body = t.calls.single().second
        assertEquals("episodes", body.getString("type"))
        assertEquals(2, body.getJSONArray("episodes").length())
        assertEquals(setOf("1x1", "1x2", "1x3", "1x4"), repo.library.value.watchedEpisodes("Lost", 2004))
        // Liste vide : aucun appel.
        a.setEpisodesWatched(TraktWork(true, "Lost", 2004, null), emptyList(), watched = false)
        assertEquals(1, t.calls.size)
    }

    @Test fun reponseDuWorker_traduite() {
        val p = WorkerTraktWriteTransport.Companion::parseWriteResponse
        assertEquals(TraktWriteResult.OK, p(200, """{"linked":true,"matched":true,"ok":true}"""))
        assertEquals(TraktWriteResult.NOT_LINKED, p(200, """{"linked":false}"""))
        assertEquals(TraktWriteResult.UNMATCHED, p(200, """{"linked":true,"matched":false,"ok":false}"""))
        assertEquals(TraktWriteResult.FAILED, p(502, """{"linked":true,"ok":false}"""))
        assertEquals(TraktWriteResult.FAILED, p(429, null))
        assertEquals(TraktWriteResult.FAILED, p(200, "<html>"))
    }

    // ───────── Scrobble : « Marquer vu automatiquement à la fin » ─────────

    private class FakeTransport : TraktTransport {
        val sent = mutableListOf<ScrobbleRequest>()
        override val isPaired = true
        override suspend fun send(req: ScrobbleRequest): ScrobbleResult { sent += req; return ScrobbleResult.LINKED }
    }

    private val resolver = object : TraktIdentityResolver {
        override suspend fun resolve(item: PlaybackContext.Item) = TraktIdentity("movie", item.title, 2020)
    }

    private fun item() = PlaybackContext.Item(providerId = 1, kind = "MOVIE", remoteId = "1", title = "Titre", poster = null, streamUrl = "u")

    @Test fun scrobble_marquageAutoDesactive_finEnvoyeeEnPause() = runTest {
        val t = FakeTransport()
        val s = TraktScrobbler(t, resolver, StandardTestDispatcher(testScheduler), autoWatched = { false })
        s.onTick(item(), true, 1_000, 100_000, 0)
        s.onStop(item(), 95_000, 100_000)
        advanceUntilIdle()
        assertEquals(listOf("start", "pause"), t.sent.map { it.action })
    }

    @Test fun scrobble_marquageAutoActif_finEnvoyeeEnStop() = runTest {
        val t = FakeTransport()
        val s = TraktScrobbler(t, resolver, StandardTestDispatcher(testScheduler))
        s.onTick(item(), true, 1_000, 100_000, 0)
        s.onStop(item(), 95_000, 100_000)
        advanceUntilIdle()
        assertEquals(listOf("start", "stop"), t.sent.map { it.action })
        assertNotNull(t.sent.last().identity)
    }
}

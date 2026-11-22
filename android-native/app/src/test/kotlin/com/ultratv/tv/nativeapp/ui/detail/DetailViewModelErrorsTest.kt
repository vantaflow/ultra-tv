package com.ultratv.tv.nativeapp.ui.detail

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.FakeCipher
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaApi
import com.ultratv.tv.nativeapp.data.extmeta.ExtResponse
import com.ultratv.tv.nativeapp.data.extmeta.FanartRepository
import com.ultratv.tv.nativeapp.data.extmeta.TvMazeRepository
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.HistoryRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.stalker.StalkerEndpointStore
import com.ultratv.tv.nativeapp.data.tmdb.RatingsClient
import com.ultratv.tv.nativeapp.data.tmdb.TmdbApi
import com.ultratv.tv.nativeapp.data.tmdb.TmdbDetails
import com.ultratv.tv.nativeapp.data.tmdb.TmdbExternalIdsStore
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.data.tmdb.TmdbQuery
import com.ultratv.tv.nativeapp.data.tmdb.TmdbRepository
import com.ultratv.tv.nativeapp.data.tmdb.TmdbSeasonRepository
import com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository
import com.ultratv.tv.nativeapp.data.trakt.TraktLibrarySource
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import com.ultratv.tv.nativeapp.ui.common.UiLog
import com.ultratv.tv.nativeapp.ui.movies.MovieDetailViewModel
import com.ultratv.tv.nativeapp.ui.series.SeriesDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Erreurs des fiches, avec les VRAIS dépôts sur une base Room en mémoire : seul le réseau est simulé (intercepteur OkHttp qui
 * échoue ou répond), TMDB / Fanart / TVmaze / Trakt sont « non appairés ». Le journal est lu par `UiLog.sink`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailViewModelErrorsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: UltraDb
    private val lines = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile private var netFailure: Throwable? = null
    @Volatile private var netBody = "{}"
    private val savedLocale = Locale.getDefault()

    private class Wiring(
        val catalog: CatalogRepository, val history: HistoryRepository, val tmdb: TmdbRepository, val trakt: TraktLibraryRepository,
        val ratings: RatingsClient, val fanart: FanartRepository, val tvMaze: TvMazeRepository, val seasons: TmdbSeasonRepository,
    )

    private lateinit var w: Wiring
    private var providerId = 0L

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        UiLog.sink = { t, m -> lines += t to m }
        db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
        runBlocking { db.profileDao().insert(ProfileEntity(name = "Principal", color = 1, initial = "P")) }
        val ok = OkHttpClient.Builder().addInterceptor { chain ->
            netFailure?.let { throw it }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(netBody.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val prefs = UserPreferencesStore(ctx, profiles)
        val tmdbApi = object : TmdbApi {
            override fun isEnabled() = false
            override suspend fun searchId(kind: TmdbKind, query: TmdbQuery, lang: String): Int? = null
            override suspend fun details(kind: TmdbKind, id: Int, lang: String): TmdbDetails? = null
        }
        val tmdb = TmdbRepository(tmdbApi, db.tmdbDao(), prefs, TmdbExternalIdsStore(ctx))
        val ext = object : ExtMetaApi { override fun isEnabled() = false; override suspend fun get(pathAndQuery: String): ExtResponse? = null }
        val catalog = CatalogRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), favoriteDao = db.favoriteDao(), epgDao = db.epgDao(),
            vodInfoDao = db.vodInfoDao(), genreDao = db.genreDao(), tmdb = tmdb, xtream = XtreamClient(ok),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()), profiles = profiles,
        )
        val trakt = TraktLibraryRepository(source = object : TraktLibrarySource { override val isPaired = false; override suspend fun fetch(lang: String): String? = null }, clock = { 0L })
        w = Wiring(
            catalog, HistoryRepository(db.watchHistoryDao(), profiles, db.seriesDao()), tmdb, trakt,
            RatingsClient(ok, DeviceTokenStore(ctx.getSharedPreferences("t", Context.MODE_PRIVATE), FakeCipher()), prefs),
            FanartRepository(ext), TvMazeRepository(ext), TmdbSeasonRepository(tmdbApi, tmdb, ctx),
        )
        providerId = runBlocking {
            // DAO brut : mot de passe en clair en base (le Keystore Android n'existe pas sur la JVM de test).
            db.providerDao().upsert(ProviderEntity(name = "Src", kind = "XTREAM", baseUrl = "http://h:8080", username = "jean", password = "s3cret"))
        }
    }

    @After fun tearDown() {
        Dispatchers.resetMain(); UiLog.sink = null; Locale.setDefault(savedLocale); runCatching { db.close() }
    }

    private fun movieVm() = MovieDetailViewModel(w.catalog, PlaybackContext(), w.history, w.tmdb, w.trakt, w.ratings, w.fanart)
    private fun seriesVm() = SeriesDetailViewModel(w.catalog, PlaybackContext(), w.history, w.tmdb, w.trakt, w.ratings, w.fanart, w.tvMaze, w.seasons)

    private fun addMovie(id: Long = 10) = runBlocking {
        db.movieDao().upsertAll(listOf(MovieEntity(id = id, providerId = providerId, remoteId = "55", name = "Orbite basse", poster = null, categoryId = null, streamUrl = "http://h:8080/movie/jean/s3cret/55.mkv", container = "mkv", year = 2024, rating = null, plot = null)))
    }
    private fun addSeries(id: Long = 20) = runBlocking {
        db.seriesDao().upsertAll(listOf(SeriesEntity(id = id, providerId = providerId, remoteId = "77", name = "Les Gardiens du phare", poster = null, categoryId = null, year = 2023, rating = null, plot = null)))
    }

    private fun <T> awaitState(flow: kotlinx.coroutines.flow.Flow<T>, what: String, cond: (T) -> Boolean): T =
        runBlocking { withTimeout(15_000) { flow.first(cond) } }

    @Test fun film_introuvable_etatNotFound() {
        val vm = movieVm()
        vm.load(999)
        assertEquals(DetailLoad.NotFound, awaitState(vm.load, "load") { it != DetailLoad.Loading })
        val l = lines.single { it.first == "ui.movie" }
        assertEquals("introuvable id=999", l.second)
        assertFalse(l.second.contains("55") || l.second.contains("h:8080"))
    }

    @Test fun film_present_etatReady() {
        addMovie()
        val vm = movieVm()
        vm.load(10)
        assertEquals(DetailLoad.Ready, awaitState(vm.load, "load") { it != DetailLoad.Loading })
    }

    @Test fun film_exceptionRoom_failedEtJournal() {
        // Table illisible (base abîmée) : la lecture locale lève une exception Room.
        db.openHelper.writableDatabase.execSQL("DROP TABLE movie")
        val vm = movieVm()
        vm.load(10)
        assertEquals(DetailLoad.Failed(SyncErrorKind.OTHER), awaitState(vm.load, "load") { it != DetailLoad.Loading })
        assertTrue(lines.any { it.first == "ui.movie" && it.second.startsWith("chargement · OTHER") })
    }

    @Test fun film_vodInfoEnEchec_ficheReady_uneLigneDetailsSource() {
        addMovie()
        netFailure = java.net.ConnectException("failed to connect to http://h:8080/player_api.php?username=jean&password=s3cret&action=get_vod_info&vod_id=55")
        val vm = movieVm()
        vm.load(10)
        awaitState(vm.infoLoading, "infoLoading") { !it }
        assertEquals(DetailLoad.Ready, vm.load.value)
        assertEquals(10L, vm.movie.value?.id)
        val l = lines.filter { it.first == "ui.movie" && it.second.startsWith("details-source") }
        assertEquals(l.toString(), 1, l.size)
        assertTrue(l.single().second, l.single().second.contains("UNREACHABLE"))
        assertFalse(l.single().second.contains("jean")); assertFalse(l.single().second.contains("s3cret"))
    }

    @Test fun film_vodInfoIllisible_uneLigneDetailsSource() {
        addMovie()
        netBody = "<html>pas du json</html>"
        val vm = movieVm()
        vm.load(10)
        awaitState(vm.infoLoading, "infoLoading") { !it }
        assertEquals(DetailLoad.Ready, vm.load.value)
        assertEquals(1, lines.count { it.first == "ui.movie" && it.second.startsWith("details-source") })
    }

    @Test fun film_retry_rechargeLaFiche() {
        val vm = movieVm()
        vm.load(10)
        assertEquals(DetailLoad.NotFound, awaitState(vm.load, "load") { it != DetailLoad.Loading })
        addMovie()
        vm.retry()
        assertEquals(DetailLoad.Ready, awaitState(vm.load, "load") { it == DetailLoad.Ready })
    }

    @Test fun serie_introuvable_etatNotFound() {
        val vm = seriesVm()
        vm.load(999)
        assertEquals(DetailLoad.NotFound, awaitState(vm.load, "load") { it != DetailLoad.Loading })
        assertEquals("introuvable id=999", lines.single { it.first == "ui.series" }.second)
    }

    @Test fun serie_episodesEnErreur_causeEtReessayer_journalMasque() {
        addSeries()
        netFailure = java.net.ConnectException("failed to connect to http://h:8080/series/jean/s3cret/1.mkv")
        val vm = seriesVm()
        vm.load(20)
        assertEquals(SyncErrorKind.UNREACHABLE, awaitState(vm.episodesError, "episodesError") { it != null })
        assertEquals(DetailLoad.Ready, vm.load.value)
        awaitState(vm.loading, "loading") { !it }
        val l = lines.filter { it.first == "ui.series" && it.second.startsWith("episodes") }
        assertEquals(l.toString(), 1, l.size)
        assertFalse(l.single().second.contains("jean")); assertFalse(l.single().second.contains("s3cret"))
        // Réessayer : la source répond, les épisodes arrivent et l'erreur disparaît.
        netFailure = null
        netBody = """{"info":{},"episodes":{"1":[{"id":"9001","episode_num":1,"title":"Pilote","container_extension":"mkv","season":1,"info":{}}]}}"""
        vm.retryEpisodes()
        awaitState(vm.episodesError, "episodesError") { it == null }
        assertEquals(1, awaitState(vm.episodes, "episodes") { it.isNotEmpty() }.size)
    }

    @Test fun serie_reponseIllisible_causeReponseIllisible() {
        addSeries()
        netBody = "<html>pas du json</html>"
        val vm = seriesVm()
        vm.load(20)
        assertEquals(SyncErrorKind.INVALID_RESPONSE, awaitState(vm.episodesError, "episodesError") { it != null })
    }

    @Test fun annulation_jamaisJournalisee() {
        addSeries()
        netFailure = java.util.concurrent.CancellationException("annulé")
        val vm = seriesVm()
        vm.load(20)
        awaitState(vm.load, "load") { it == DetailLoad.Ready }
        // L'annulation traverse `uiAttempt` sans ligne de journal ni erreur d'épisodes affichée.
        assertTrue(lines.toString(), lines.none { it.second.startsWith("episodes") })
        assertNull(vm.episodesError.value)
    }

    @Test fun titreDEpisodePersiste_chiffresLatins_sousLocaleArabe() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        var played: String? = null
        val ep = EpisodeEntity(seriesId = 20, remoteId = "e", season = 2, episode = 5, title = "Pilote", streamUrl = "http://h/x.mkv", container = null, plot = null)
        seriesVm().playEpisode("Les Gardiens du phare", "77", providerId, ep) { _, t -> played = t }
        assertTrue(played, played!!.contains("S02E05"))
        assertFalse(played!!.any { it in '٠'..'٩' })
    }
}

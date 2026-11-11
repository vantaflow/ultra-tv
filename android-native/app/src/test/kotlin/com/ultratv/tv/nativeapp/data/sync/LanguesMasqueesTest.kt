package com.ultratv.tv.nativeapp.data.sync

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.LangSelection
import com.ultratv.tv.nativeapp.data.repo.LanguageManager
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.stalker.StalkerEndpointStore
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Langues masquées : jamais chargées. Faux serveur en mémoire (aucun réseau). Les titres de [SyntheticXtream] sont
 * « LL | … » dans des catégories « LL' | … » dont la langue est décalée d'un cran : avec les langues « fr » et « ar »,
 * seuls les éléments d'indice multiple de 10 (titre FR, catégorie AR) passent les deux filtres (catégorie ET titre).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class LanguesMasqueesTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val toClose = mutableListOf<UltraDb>()
    @After fun cleanup() { toClose.forEach { runCatching { it.close() } } }

    private class Rig(val db: UltraDb, val repo: ProviderRepository, val providerId: Long, val prefs: UserPreferencesStore, val manager: LanguageManager)

    private fun rig(interceptor: Interceptor, dbFile: File): Rig {
        val db = Room.databaseBuilder(ctx, UltraDb::class.java, dbFile.absolutePath)
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build().also { toClose += it }
        val ok = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val prefs = UserPreferencesStore(ctx, profiles)
        val providerDao = ProviderDao(db.providerDao())
        val repo = ProviderRepository(
            providerDao = providerDao, channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = SyncStatusBus(), xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = prefs,
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()),
        )
        val id = runBlocking { db.providerDao().upsert(ProviderEntity(name = "fictif", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "test", password = "test", active = true)) }
        val manager = LanguageManager(db, providerDao, db.categoryDao(), db.channelDao(), db.movieDao(), db.seriesDao(), prefs, SyncCoordinator(ctx))
        return Rig(db, repo, id, prefs, manager)
    }

    private fun UltraDb.n(sql: String): Int = openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }
    private fun tmp(name: String) = File(System.getProperty("java.io.tmpdir"), "ultratv-langues-$name").also { it.deleteRecursively(); it.mkdirs() }
    private fun server(name: String) = SyntheticXtream(tmp(name), live = 300, movies = 2_000, series = 400, guideChannels = 120, guideDays = 2, perDay = 12).also { it.generate() }

    private fun UltraDb.shape() = listOf(n("SELECT COUNT(*) FROM channel"), n("SELECT COUNT(*) FROM movie"), n("SELECT COUNT(*) FROM series"))
    /** Lignes de l'index plein texte (table des tailles de documents : une ligne par document indexé). */
    private fun UltraDb.fts() = listOf(n("SELECT COUNT(*) FROM channel_fts_docsize"), n("SELECT COUNT(*) FROM movie_fts_docsize"), n("SELECT COUNT(*) FROM series_fts_docsize"))

    @Test
    fun synchro_langueMasquee_ni_base_ni_fts_ni_guide() = runBlocking {
        val s = server("synchro")
        val r = rig(s.interceptor, File(s.dir, "db.sqlite"))
        r.prefs.setLanguages("fr,ar")
        r.repo.syncAll(r.providerId, force = true)

        // Seuls les éléments dont la catégorie ET le titre sont dans une langue choisie sont écrits.
        assertEquals(listOf(30, 200, 40), r.db.shape())
        assertEquals("index plein texte = base : rien d'indexé pour les langues masquées", r.db.shape(), r.db.fts())
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM movie WHERE lang <> 'fr'"))
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM channel WHERE lang <> 'fr'"))
        assertTrue(r.db.movieDao().searchFts(r.providerId, "imaginaire*", 5000).all { it.lang == "fr" })
        // Les catégories restent listées (réactivables), désactivées hors langues choisies : 26 sur 130 gardent fr/ar (k % 10 ∈ {0, 1}).
        val cats = r.db.categoryDao().forProviderKind(r.providerId, "MOVIE")
        assertEquals(260, cats.size)
        assertEquals(52, cats.count { it.enabled })
        // Guide : aucun programme pour une chaîne non chargée (12 chaînes du guide ont un titre FR : c % 10 == 0, et celles-là seules sont chargées).
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM epg WHERE channelId NOT IN (SELECT id FROM channel)"))
        val epgChannels = r.db.n("SELECT COUNT(DISTINCT channelId) FROM epg")
        assertTrue("des programmes existent pour les chaînes chargées", epgChannels in 1..12)
    }

    @Test
    fun desactivation_purgeBaseEtFts_sansRetelecharger_puisReactivation_resynchronise() = runBlocking {
        val s = server("purge")
        val r = rig(s.interceptor, File(s.dir, "db.sqlite"))
        r.repo.syncAll(r.providerId, force = true)                       // aucune langue masquée : tout est chargé
        assertEquals(listOf(300, 2_000, 400), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())
        val requests = s.requests.size

        // Masquer : tout sauf fr et ar part de la base ET de l'index, sans aucune requête réseau.
        val frAr = LangSelection.of("fr,ar", true, true)
        val out = r.manager.apply(LangSelection(), frAr)
        assertTrue(out.purged); assertEquals(emptyList<Long>(), out.resync)
        assertEquals(listOf(30, 200, 40), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())
        assertEquals(requests, s.requests.size)
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM movie WHERE lang <> 'fr'"))
        assertEquals(52, r.db.categoryDao().forProviderKind(r.providerId, "MOVIE").count { it.enabled })
        assertTrue(r.db.movieDao().searchFts(r.providerId, "imaginaire*", 5000).all { it.lang == "fr" })

        // Masquer encore ar : leurs catégories sont désactivées, et ces titres FR s'y trouvaient (catégories AR) : tout part.
        val fr = LangSelection.of("fr", true, true)
        r.manager.apply(frAr, fr)
        assertEquals(listOf(0, 0, 0), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())

        // Réactiver : catégories rouvertes et resynchronisation COMPLÈTE de la source demandée.
        val back = r.manager.apply(fr, LangSelection())
        assertEquals(listOf(r.providerId), back.resync)
        assertEquals(260, r.db.categoryDao().forProviderKind(r.providerId, "MOVIE").count { it.enabled })
        r.repo.syncAll(r.providerId, force = true)                       // ce que fait SyncCoordinator.requestResync
        assertEquals(listOf(300, 2_000, 400), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())
    }

    @Test
    fun reactivationPartielle_ajouteSeulementLaLangueRevenue() = runBlocking {
        val s = server("partielle")
        val r = rig(s.interceptor, File(s.dir, "db.sqlite"))
        r.prefs.setLanguages("fr")
        r.repo.syncAll(r.providerId, force = true)
        val before = r.db.shape()
        r.prefs.setLanguages("fr,ar")
        val out = r.manager.apply(LangSelection.of("fr", true, true), LangSelection.of("fr,ar", true, true))
        assertEquals(listOf(r.providerId), out.resync)
        r.repo.syncAll(r.providerId, force = true)
        val after = r.db.shape()
        assertTrue("des éléments sont revenus", after[1] >= before[1] && after[1] == 200)
        assertEquals(after, r.db.fts())
    }

    /** Catégorie sans langue : « FR | » gardé, « TR | » sauté, titre sans langue détectée CONSERVÉ. */
    @Test
    fun titresSansLangue_conserves_etTitreTurcSaute() = runBlocking {
        val json = mapOf(
            "get_live_categories" to "[]", "get_series_categories" to "[]", "get_live_streams" to "[]", "get_series" to "[]",
            "get_vod_categories" to """[{"category_id":"1","category_name":"Divers","parent_id":0}]""",
            "get_vod_streams" to listOf("FR | Alpha", "TR | Beta", "Gamma", "GERMAN Delta").withIndex().joinToString(",", "[", "]") { (i, n) ->
                """{"num":$i,"name":"$n","title":"$n","year":"2020","stream_type":"movie","stream_id":${900 + i},"stream_icon":"","rating":"5.0","rating_5based":2.5,"added":"1700000000","category_id":"1","container_extension":"mkv","custom_sid":"","direct_source":""}"""
            },
            "user_info" to """{"user_info":{"username":"test","auth":1,"status":"Active","exp_date":"1900000000","is_trial":"0","active_cons":"0","created_at":"1600000000","max_connections":"2"},"server_info":{"url":"serveur-fictif.invalid","timestamp_now":1700000000,"time_now":"2026-01-01 00:00:00"}}""",
            "xmltv" to "<tv></tv>",
        )
        val server = Interceptor { chain ->
            val req = chain.request()
            val key = if (req.url.encodedPath.endsWith("xmltv.php")) "xmltv" else req.url.queryParameter("action") ?: "user_info"
            val body = json[key]
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(if (body == null) 404 else 200).message("m")
                .body(ResponseBody.create((if (key == "xmltv") "application/xml" else "application/json").toMediaType(), body.orEmpty())).build()
        }
        val r = rig(server, File(tmp("mixte"), "db.sqlite"))
        r.prefs.setLanguages("fr")
        r.repo.syncAll(r.providerId, force = true)
        val names = r.db.movieDao().searchFts(r.providerId, "alpha* OR beta* OR gamma* OR delta*", 10).map { it.name }.sorted()
        assertEquals(listOf("FR | Alpha", "Gamma"), names)
        assertEquals(2, r.db.n("SELECT COUNT(*) FROM movie"))
        assertEquals(2, r.db.n("SELECT COUNT(*) FROM movie_fts_docsize"))

        // Retirer « sans langue détectée » : la catégorie « Divers » (sans langue) est désactivée, ses titres purgés, sans réseau.
        val out = r.manager.apply(LangSelection.of("fr", true, true), LangSelection.of("fr", true, false))
        assertTrue(out.purged)
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM movie"))
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM movie_fts_docsize"))
        assertEquals(0, r.db.categoryDao().forProviderKind(r.providerId, "MOVIE").count { it.enabled })
    }
}

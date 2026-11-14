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
import com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore
import com.ultratv.tv.nativeapp.data.repo.CategoryManager
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
 * Langues masquées : jamais chargées, filtre par CATÉGORIE (et par type : direct, films, séries ont chacun les leurs,
 * avec des identifiants qui se recoupent). Faux serveur en mémoire (aucun réseau). Dans [SyntheticXtream], la catégorie
 * d'identifiant k est dans la langue langs[k % 10] (FR si k % 10 == 0, AR si 1), le titre i dans langs[i % 10] et sa
 * catégorie est 1 + i % n : avec « fr » et « ar », les éléments d'indice i % 10 ∈ {9, 0} sont dans une catégorie FR ou AR.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class LanguesMasqueesTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val toClose = mutableListOf<UltraDb>()
    @After fun cleanup() { toClose.forEach { runCatching { it.close() } } }

    private class Rig(val db: UltraDb, val repo: ProviderRepository, val providerId: Long, val prefs: UserPreferencesStore, val manager: LanguageManager, val categories: CategoryManager, val store: LangDisabledStore)

    private fun rig(interceptor: Interceptor, dbFile: File): Rig {
        val db = Room.databaseBuilder(ctx, UltraDb::class.java, dbFile.absolutePath)
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build().also { toClose += it }
        val ok = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val prefs = UserPreferencesStore(ctx, profiles)
        val providerDao = ProviderDao(db.providerDao())
        val store = LangDisabledStore(ctx).also { st -> (1L..3L).forEach { p -> listOf("LIVE", "MOVIE", "SERIES").forEach { k -> st.remove(p, k, st.ids(p, k)) } } }
        val repo = ProviderRepository(
            providerDao = providerDao, channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = SyncStatusBus(), xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = prefs,
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx), langDisabled = store, hiddenChannels = com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()),
        )
        val id = runBlocking { db.providerDao().upsert(ProviderEntity(name = "fictif", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "test", password = "test", active = true)) }
        val manager = LanguageManager(db, providerDao, db.categoryDao(), db.channelDao(), db.movieDao(), db.seriesDao(), prefs, store, SyncCoordinator(ctx))
        val categories = CategoryManager(db, db.categoryDao(), db.channelDao(), db.movieDao(), db.seriesDao(), SyncCoordinator(ctx), store, com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore(ctx))
        return Rig(db, repo, id, prefs, manager, categories, store)
    }

    private fun UltraDb.n(sql: String): Int = openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }
    private fun tmp(name: String) = File(System.getProperty("java.io.tmpdir"), "ultratv-langues-$name").also { it.deleteRecursively(); it.mkdirs() }
    private fun server(name: String) = SyntheticXtream(tmp(name), live = 300, movies = 2_000, series = 400, guideChannels = 120, guideDays = 2, perDay = 12).also { it.generate() }

    private fun UltraDb.shape() = listOf(n("SELECT COUNT(*) FROM channel"), n("SELECT COUNT(*) FROM movie"), n("SELECT COUNT(*) FROM series"))
    /** Lignes de l'index plein texte (table des tailles de documents : une ligne par document indexé). */
    private fun UltraDb.fts() = listOf(n("SELECT COUNT(*) FROM channel_fts_docsize"), n("SELECT COUNT(*) FROM movie_fts_docsize"), n("SELECT COUNT(*) FROM series_fts_docsize"))
    private fun Rig.enabled(kind: String) = runBlocking { db.categoryDao().forProviderKind(providerId, kind).count { it.enabled } }

    @Test
    fun synchro_categorieMasquee_ni_base_ni_fts_ni_guide() = runBlocking {
        val s = server("synchro")
        val r = rig(s.interceptor, File(s.dir, "db.sqlite"))
        r.prefs.setLanguages("fr,ar")
        r.repo.syncAll(r.providerId, force = true)

        // Catégories FR / AR actives : TOUS leurs titres sont chargés ; les autres catégories ne chargent rien.
        assertEquals(listOf(60, 400, 80), r.db.shape())
        assertEquals("index plein texte = base : rien d'indexé pour les catégories masquées", r.db.shape(), r.db.fts())
        assertEquals(listOf(28, 52, 22), listOf(r.enabled("LIVE"), r.enabled("MOVIE"), r.enabled("SERIES")))
        assertEquals(140, r.db.categoryDao().forProviderKind(r.providerId, "LIVE").size)   // toutes restent listées (réactivables)
        // Une catégorie FR/AR garde un titre dont la langue détectée est autre (le titre n'est pas filtré).
        assertTrue(r.db.n("SELECT COUNT(*) FROM movie WHERE lang NOT IN ('fr', 'ar')") > 0)
        // Les catégories désactivées par la langue sont tracées, par type.
        assertTrue(r.store.ids(r.providerId, "MOVIE").size == 260 - 52 && r.store.ids(r.providerId, "LIVE").size == 140 - 28)
        // Guide : aucun programme pour une chaîne non chargée.
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM epg WHERE channelId NOT IN (SELECT id FROM channel)"))
        assertTrue("des programmes existent pour les chaînes chargées", r.db.n("SELECT COUNT(DISTINCT channelId) FROM epg") in 1..24)
    }

    @Test
    fun masquer_purgeParCategorie_sansReseau_reactiver_resynchronise_categorieManuelleRestePurgee() = runBlocking {
        val s = server("purge")
        val r = rig(s.interceptor, File(s.dir, "db.sqlite"))
        r.repo.syncAll(r.providerId, force = true)                       // aucune langue masquée : tout est chargé
        assertEquals(listOf(300, 2_000, 400), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())
        // À la main : la catégorie « 10 » des FILMS seulement (les catégories 10 du direct et des séries ont le même identifiant).
        r.categories.setEnabled(r.providerId, "MOVIE", listOf("10"), false)
        assertEquals(8, 2_000 - r.db.n("SELECT COUNT(*) FROM movie"))
        assertEquals(listOf(300, 400), listOf(r.db.n("SELECT COUNT(*) FROM channel"), r.db.n("SELECT COUNT(*) FROM series")))
        assertEquals(listOf(140, 259, 110), listOf(r.enabled("LIVE"), r.enabled("MOVIE"), r.enabled("SERIES")))
        val requests = s.requests.size

        // Masquer fr+ar-seulement : les catégories des autres langues sont désactivées et leurs titres purgés, sans réseau.
        val out = r.manager.apply(LangSelection(), LangSelection.of("fr,ar", true, true))
        assertTrue(out.purged); assertEquals(emptyList<Long>(), out.resync)
        assertEquals(listOf(60, 400 - 8, 80), r.db.shape())             // 8 films de la catégorie 10 (FR, coupée à la main) déjà absents
        assertEquals(r.db.shape(), r.db.fts())
        assertEquals(requests, s.requests.size)

        // Masquer aussi ar : seules les catégories FR restent, par type.
        r.manager.apply(LangSelection.of("fr,ar", true, true), LangSelection.of("fr", true, true))
        assertEquals(listOf(30, 200 - 8, 40), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())

        // Tout réactiver : catégories coupées PAR LA LANGUE rouvertes (pas la 10 des films, coupée à la main) + resynchro.
        val back = r.manager.apply(LangSelection.of("fr", true, true), LangSelection())
        assertEquals(listOf(r.providerId), back.resync)
        assertEquals(listOf(140, 259, 110), listOf(r.enabled("LIVE"), r.enabled("MOVIE"), r.enabled("SERIES")))
        r.repo.syncAll(r.providerId, force = true)                       // ce que fait SyncCoordinator.requestResync
        assertEquals(listOf(300, 2_000 - 8, 400), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())
        assertEquals(0, r.db.n("SELECT COUNT(*) FROM movie WHERE categoryId = '10'"))
        assertTrue(r.db.n("SELECT COUNT(*) FROM channel WHERE categoryId = '10'") > 0 && r.db.n("SELECT COUNT(*) FROM series WHERE categoryId = '10'") > 0)
    }

    @Test
    fun reactivationPartielle_ajouteSeulementLesCategoriesDeLaLangueRevenue() = runBlocking {
        val s = server("partielle")
        val r = rig(s.interceptor, File(s.dir, "db.sqlite"))
        r.prefs.setLanguages("fr")
        r.repo.syncAll(r.providerId, force = true)
        assertEquals(listOf(30, 200, 40), r.db.shape())
        r.prefs.setLanguages("fr,ar")
        val out = r.manager.apply(LangSelection.of("fr", true, true), LangSelection.of("fr,ar", true, true))
        assertEquals(listOf(r.providerId), out.resync)
        r.repo.syncAll(r.providerId, force = true)
        assertEquals(listOf(60, 400, 80), r.db.shape())
        assertEquals(r.db.shape(), r.db.fts())
    }

    /** Catégorie sans langue active : tous ses titres (FR, TR, sans langue) chargés ; catégorie TR : aucun. */
    @Test
    fun categorieActive_chargeeEnEntier_categorieDeLangueMasqueeNon() = runBlocking {
        val item = { i: Int, n: String, cat: Int -> """{"num":$i,"name":"$n","title":"$n","year":"2020","stream_type":"movie","stream_id":${900 + i},"stream_icon":"","rating":"5.0","rating_5based":2.5,"added":"1700000000","category_id":"$cat","container_extension":"mkv","custom_sid":"","direct_source":""}""" }
        val json = mapOf(
            "get_live_categories" to "[]", "get_series_categories" to "[]", "get_live_streams" to "[]", "get_series" to "[]",
            "get_vod_categories" to """[{"category_id":"1","category_name":"Divers","parent_id":0},{"category_id":"2","category_name":"TR | Films","parent_id":0},{"category_id":"3","category_name":"FR | Action","parent_id":0}]""",
            "get_vod_streams" to listOf(item(1, "FR | Alpha", 1), item(2, "TR | Beta", 1), item(3, "Gamma", 1), item(4, "Omega", 2), item(5, "TR | Zeta", 3)).joinToString(",", "[", "]"),
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
        // « Divers » (sans langue, active) : ses 3 titres, dont « TR | Beta » ; « FR | Action » garde son titre TR ; « TR | Films » : rien.
        assertEquals(listOf("FR | Alpha", "Gamma", "TR | Beta", "TR | Zeta"), r.db.movieDao().searchFts(r.providerId, "alpha* OR beta* OR gamma* OR omega* OR zeta*", 10).map { it.name }.sorted())
        assertEquals(4, r.db.n("SELECT COUNT(*) FROM movie_fts_docsize"))
        assertEquals(setOf("2"), r.store.ids(r.providerId, "MOVIE"))

        // Retirer « sans langue détectée » : la catégorie « Divers » est désactivée, ses titres purgés, sans réseau.
        val out = r.manager.apply(LangSelection.of("fr", true, true), LangSelection.of("fr", true, false))
        assertTrue(out.purged)
        assertEquals(1, r.db.n("SELECT COUNT(*) FROM movie WHERE categoryId = '3'"))
        assertEquals(1, r.db.n("SELECT COUNT(*) FROM movie"))
        assertEquals(1, r.db.n("SELECT COUNT(*) FROM movie_fts_docsize"))
        assertEquals(setOf("1", "2"), r.store.ids(r.providerId, "MOVIE"))
    }
}

package com.ultratv.tv.nativeapp.data.stalker

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
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Films et séries d'un portail Stalker (faux portail en mémoire, [FakeVodPortal]) : module `type=series`, catégorie en
 * erreur sautée sans faire échouer la synchro, jeton qui expire en pleine synchro, gros catalogue paginé.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StalkerVodSeriesSyncTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val toClose = mutableListOf<UltraDb>()
    @After fun cleanup() { toClose.forEach { runCatching { it.close() } } }

    private val mac = "00:1A:79:AB:CD:EF"
    private val portal = "http://portail-fictif.invalid/stalker_portal/c"
    private fun provider(id: Long = 7) = ProviderEntity(id = id, name = "Portail", kind = "STALKER", baseUrl = portal, username = mac, password = "")
    private fun client(fake: FakeVodPortal) = StalkerClient(OkHttpClient.Builder().addInterceptor(fake).build(), StalkerEndpointStore.inMemory()).also { it.retryDelayMs = 1 }

    // ───────────── Client ─────────────

    @Test fun moduleSeries_categoriesEtSeriesPrefixees_episodesParSaison_lienVodPuisSeries() = runBlocking {
        val fake = FakeVodPortal()
        val c = client(fake)
        val p = provider()
        val cats = c.seriesCategories(p)
        assertEquals(listOf("s:1", "s:2", "s:3"), cats.map { it.remoteId })
        assertTrue(cats.all { it.kind == "SERIES" })
        assertTrue("la VOD n'a rien", c.vodCategories(p).isEmpty())

        val series = c.seriesOfCategory(p, "s:1")
        assertEquals(30, series.size)
        assertTrue(series.all { it.series.remoteId.startsWith("s:") && it.series.categoryId == "s:1" })
        assertTrue(fake.urls.any { it.queryParameter("type") == "series" && it.queryParameter("action") == "get_ordered_list" && it.queryParameter("category") == "1" })

        val s = series.first().series.copy(id = 42)
        val eps = c.episodes(p, s)
        assertEquals(6, eps.size)                                     // 2 saisons × 3 épisodes
        assertEquals(listOf(1, 2), eps.map { it.season }.distinct())
        assertTrue(fake.urls.any { it.queryParameter("type") == "series" && it.queryParameter("movie_id") == "1-0" })
        val link = StalkerLink.parse(eps.first().streamUrl!!)!!
        assertEquals("series", link.type)

        // create_link : vod d'abord (le plus courant)…
        assertEquals("http://flux.invalid/vod/1.mkv", c.createLink(p, link.type, link.cmd, link.series))
        // … puis le module séries si la VOD ne donne rien.
        fake.vodCreateLinkFails = true
        assertEquals("http://flux.invalid/series/1.mkv", c.createLink(p, link.type, link.cmd, link.series))
    }

    @Test fun categorieEn500_nouvellesTentativesPuisErreur() = runBlocking {
        val fake = FakeVodPortal().apply { failingCategories += "3" }
        val c = client(fake)
        try { c.seriesOfCategory(provider(), "s:3"); fail("500 attendu") } catch (e: HttpStatusException) { assertEquals(500, e.code) }
        val tries = fake.urls.count { it.queryParameter("category") == "3" }
        assertEquals(StalkerClient.PAGE_ATTEMPTS, tries)
    }

    @Test fun jetonExpireEnPleinePagination_nouvellePoigneeDeMain_rienNeManque() = runBlocking {
        val fake = FakeVodPortal(seriesCategories = mapOf("1" to ("FR Séries" to 200)))
        val c = client(fake)
        c.seriesCategories(provider())                               // session ouverte
        val before = fake.handshakes.get()
        fake.expireAfterListRequests = 4
        val series = c.seriesOfCategory(provider(), "1")
        assertEquals(200, series.size)
        assertTrue("nouvelle poignée de main", fake.handshakes.get() > before)
    }

    @Test fun grosCatalogue_50000Films_toutesLesPagesLues_JsHttpRequest() = runBlocking {
        val fake = FakeVodPortal(seriesCategories = emptyMap(), vodCategories = mapOf("9" to ("FR Films" to 50_001)))
        val c = client(fake)
        val split = c.vodOfCategory(provider(), "9")
        assertEquals(50_001, split.movies.size)                      // 3 572 pages de 14 : au-delà de l'ancien plafond de 3 000
        val lists = fake.urls.filter { it.queryParameter("action") == "get_ordered_list" }
        assertEquals((50_001 + 13) / 14, lists.size)
        assertTrue(fake.urls.filter { it.encodedPath.endsWith("load.php") }.all { it.queryParameter("JsHttpRequest") == "1-xml" })
    }

    @Test fun sansTotalItems_pagesLuesJusquALaPageVide() = runBlocking {
        val fake = FakeVodPortal(seriesCategories = mapOf("1" to ("FR Séries" to 40))).apply { omitTotal = true }
        val series = client(fake).seriesOfCategory(provider(), "1")
        assertEquals(40, series.size)
    }

    // ───────────── Synchro complète (dépôt) ─────────────

    private class Rig(val db: UltraDb, val repo: ProviderRepository, val bus: SyncStatusBus, val providerId: Long)

    private fun rig(fake: okhttp3.Interceptor, name: String): Rig {
        val dir = File(System.getProperty("java.io.tmpdir"), "ultratv-stalker-$name").also { it.deleteRecursively(); it.mkdirs() }
        val db = Room.databaseBuilder(ctx, UltraDb::class.java, File(dir, "db.sqlite").absolutePath)
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build().also { toClose += it }
        val ok = OkHttpClient.Builder().addInterceptor(fake).build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val prefs = UserPreferencesStore(ctx, profiles)
        val bus = SyncStatusBus()
        val stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()).also { it.retryDelayMs = 1 }
        val repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = bus, xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = prefs,
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx), langDisabled = LangDisabledStore(ctx),
            stalker = stalker,
        )
        val id = runBlocking { db.providerDao().upsert(provider(0).copy(active = true)) }
        return Rig(db, repo, bus, id)
    }

    private fun UltraDb.n(sql: String): Int = openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    @Test fun synchro_seriesDuModule_categorieEn500Sautee_messageCategoriesIgnorees() = runBlocking {
        val fake = FakeVodPortal().apply { failingCategories += "3" }
        val r = rig(fake, "module")
        r.repo.syncAll(r.providerId, force = true)                   // ne lève pas : le direct ET les séries sont là
        assertEquals(1, r.db.n("SELECT COUNT(*) FROM channel"))
        assertEquals(35, r.db.n("SELECT COUNT(*) FROM series"))      // 30 + 5, la catégorie 3 est sautée
        assertEquals(35, r.db.n("SELECT COUNT(*) FROM series WHERE remoteId LIKE 's:%'"))
        val partial = r.bus.partial.value
        assertNotNull(partial)
        assertEquals(1, partial!!.skipped)
        assertEquals(null, r.bus.failure.value)
    }

    @Test fun synchro_categorieQuiTombeEnPanne_gardeSonContenuPrecedent() = runBlocking {
        val fake = FakeVodPortal()
        val r = rig(fake, "garde")
        r.repo.syncAll(r.providerId, force = true)
        assertEquals(39, r.db.n("SELECT COUNT(*) FROM series"))
        assertEquals(null, r.bus.partial.value)

        fake.failingCategories += "2"
        r.repo.syncAll(r.providerId, force = true)
        assertEquals("les 5 séries de la catégorie en panne restent", 39, r.db.n("SELECT COUNT(*) FROM series"))
        assertEquals(1, r.bus.partial.value?.skipped)
    }

    @Test fun synchro_jetonExpireEnRoute_toutEstCharge() = runBlocking {
        val fake = FakeVodPortal(seriesCategories = mapOf("1" to ("FR Séries" to 100), "2" to ("FR Drames" to 100)))
        val r = rig(fake, "jeton")
        fake.expireAfterListRequests = 5
        r.repo.syncAll(r.providerId, force = true)
        assertEquals(200, r.db.n("SELECT COUNT(*) FROM series"))
        assertEquals(null, r.bus.partial.value)
        assertTrue(fake.handshakes.get() >= 2)
    }

    @Test fun synchro_listeDesCategoriesVodEnErreur_partieSauteeSansEchecGlobal() = runBlocking {
        val fake = object : okhttp3.Interceptor {
            val inner = FakeVodPortal()
            override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
                val u = chain.request().url
                if (u.queryParameter("type") == "vod" && u.queryParameter("action") == "get_categories")
                    return okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(503).message("x")
                        .body("".toResponseBody(null)).build()
                return inner.intercept(chain)
            }
        }
        val r = rig(fake, "vodcats")
        r.repo.syncAll(r.providerId, force = true)
        assertEquals(1, r.db.n("SELECT COUNT(*) FROM channel"))
        assertEquals(39, r.db.n("SELECT COUNT(*) FROM series"))
        assertTrue((r.bus.partial.value?.skipped ?: 0) >= 1)
        assertEquals(null, r.bus.failure.value)
    }

}

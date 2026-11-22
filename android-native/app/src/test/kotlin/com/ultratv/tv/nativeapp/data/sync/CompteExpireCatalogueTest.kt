package com.ultratv.tv.nativeapp.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.net.NetErrors
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.stalker.StalkerEndpointStore
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Un compte expiré ou refusé ne doit JAMAIS effacer le catalogue (S1 de l'analyse « session hors ligne »). Serveur
 * LOCAL (MockWebServer, 127.0.0.1) : une base pleine, puis une synchro où le panneau répond 200 avec `user_info.auth=0`,
 * un statut « Expired », ou des listes entièrement vides. Aucun fournisseur réel n'est contacté.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompteExpireCatalogueTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private val dbs = mutableListOf<UltraDb>()

    /** Ce que le faux panneau répond à chaque action de `player_api.php` (et à la playlist M3U). */
    @Volatile private var mode = Mode.FULL
    @Volatile private var liveCount = 6
    private val guideHits = java.util.concurrent.atomic.AtomicInteger()

    private enum class Mode { FULL, AUTH0, EXPIRED, BANNED, EMPTY, EMPTY_CATS_ONLY, ONE_CAT_EMPTY, FEW, DEMO, PARTIAL_CATS, VOD_EMPTY }

    @Before fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request)
        }
        server.start()
    }

    @After fun stop() {
        dbs.forEach { runCatching { it.close() } }
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun cats(prefix: String, n: Int) = (1..n).joinToString(",", "[", "]") { """{"category_id":"$prefix$it","category_name":"Cat $prefix$it"}""" }

    private fun respond(r: RecordedRequest): MockResponse {
        val url = r.requestUrl!!
        if (url.encodedPath.endsWith(".m3u")) return m3u()
        if (url.encodedPath.endsWith("xmltv.php")) { guideHits.incrementAndGet(); return MockResponse().setResponseCode(404) }
        val action = url.queryParameter("action")
        val auth0 = """{"user_info":{"auth":0}}"""
        val expired = """{"user_info":{"username":"u","auth":1,"status":"Expired","exp_date":"1600000000"},"server_info":{"url":"local"}}"""
        val banned = """{"user_info":{"username":"u","auth":0,"status":"Banned"}}"""
        when (mode) {
            Mode.AUTH0 -> return json(auth0)
            Mode.EXPIRED -> return json(expired)
            Mode.BANNED -> return json(banned)
            Mode.EMPTY -> return json("[]")
            else -> Unit
        }
        return when (action) {
            null -> json("""{"user_info":{"username":"u","auth":1,"status":"Active","max_connections":"1"},"server_info":{"url":"local","timezone":"UTC"}}""")
            "get_live_categories" -> json(
                when (mode) {
                    Mode.EMPTY_CATS_ONLY -> "[]"
                    Mode.DEMO -> """[{"category_id":"X","category_name":"Abonnement expire"}]"""
                    Mode.PARTIAL_CATS -> cats("L", 1)
                    else -> cats("L", 3)
                },
            )
            "get_vod_categories" -> json(if (mode == Mode.EMPTY_CATS_ONLY) "[]" else cats("V", 2))
            "get_series_categories" -> json(if (mode == Mode.EMPTY_CATS_ONLY) "[]" else cats("S", 2))
            "get_live_streams" -> if (mode == Mode.DEMO) json((1..3).joinToString(",", "[", "]") { """{"num":$it,"name":"Demo $it","stream_id":${900 + it},"category_id":"X"}""" }) else json(
                (1..(if (mode == Mode.FEW) 3 else liveCount))
                    .filter { mode != Mode.ONE_CAT_EMPTY || 1 + it % 3 != 1 }
                    .joinToString(",", "[", "]") { """{"num":$it,"name":"Chaine $it","stream_id":${100 + it},"category_id":"L${1 + it % 3}"}""" },
            )
            "get_vod_streams" -> if (mode == Mode.VOD_EMPTY) json("[]") else json((1..4).joinToString(",", "[", "]") { """{"num":$it,"name":"Film $it","stream_id":${200 + it},"category_id":"V${1 + it % 2}","container_extension":"mp4"}""" })
            "get_series" -> json((1..(if (mode == Mode.VOD_EMPTY) 5 else 3)).joinToString(",", "[", "]") { """{"num":$it,"name":"Serie $it","series_id":${300 + it},"category_id":"S${1 + it % 2}"}""" })
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun m3u(): MockResponse = when (mode) {
        Mode.EMPTY -> MockResponse().setBody("")
        Mode.AUTH0, Mode.EXPIRED, Mode.BANNED -> MockResponse().setHeader("Content-Type", "text/html")
            .setBody("<html><body><h1>Account expired</h1></body></html>")
        else -> MockResponse().setBody(buildString {
            append("#EXTM3U\n")
            for (i in 1..liveCount) append("#EXTINF:-1 group-title=\"G${1 + i % 3}\",Chaine $i\nhttp://127.0.0.1/s/$i.ts\n")
        })
    }

    private class Rig(val db: UltraDb, val repo: ProviderRepository, val bus: SyncStatusBus, val pid: Long)

    private fun rig(kind: String = "XTREAM"): Rig {
        val db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries()
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback()).build().also { dbs += it }
        val ok = OkHttpClient.Builder().build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val prefs = UserPreferencesStore(ctx, profiles)
        // Préférences NON modifiées : le DataStore est partagé par les tests de la même JVM. Le guide (xmltv.php) reçoit
        // un 404 du faux serveur, échec isolé qui ne touche jamais au catalogue.
        val bus = SyncStatusBus()
        val repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = bus, xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = prefs,
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx), langDisabled = LangDisabledStore(ctx),
            hiddenChannels = com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()),
        )
        val base = server.url("/").toString().trimEnd('/')
        val url = if (kind == "M3U") "$base/liste.m3u" else base
        val id = runBlocking { db.providerDao().upsert(ProviderEntity(name = "local", kind = kind, baseUrl = url, username = "u", password = "p", active = true)) }
        return Rig(db, repo, bus, id)
    }

    private fun UltraDb.n(sql: String): Int = openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }
    /** Chaînes, films, séries, catégories (tous types). */
    private fun Rig.shape() = listOf(db.n("SELECT COUNT(*) FROM channel"), db.n("SELECT COUNT(*) FROM movie"), db.n("SELECT COUNT(*) FROM series"), db.n("SELECT COUNT(*) FROM category"))

    /** Synchro qui DOIT échouer : renvoie la cause classée (celle du bandeau). */
    private fun Rig.syncFails(): SyncErrorKind = runBlocking {
        try {
            repo.syncAll(pid, force = true)
            fail("la synchro aurait dû échouer (mode $mode) ; catalogue après : ${shape()}")
            error("inaccessible")
        } catch (t: Throwable) {
            if (t is AssertionError) throw t
            val failure = bus.failure.value
            assertTrue("échec publié sur le bus de synchro", failure != null && failure.providerId == pid)
            assertEquals(NetErrors.classify(t), failure!!.kind)
            failure.kind
        }
    }

    private val full = listOf(6, 4, 3, 7)

    @Test fun xtream_auth0_garde_le_catalogue_et_signale_identifiants_refuses() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(full, r.shape())
        mode = Mode.AUTH0
        assertEquals(SyncErrorKind.UNAUTHORIZED, r.syncFails())
        assertEquals("catalogue intact après auth=0", full, r.shape())
    }

    @Test fun xtream_statut_expire_garde_le_catalogue_et_signale_abonnement_expire() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        mode = Mode.EXPIRED
        assertEquals(SyncErrorKind.ACCOUNT_EXPIRED, r.syncFails())
        assertEquals("catalogue intact après statut Expired", full, r.shape())
    }

    @Test fun xtream_statut_banni_garde_le_catalogue() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        mode = Mode.BANNED
        assertEquals(SyncErrorKind.UNAUTHORIZED, r.syncFails())
        assertEquals(full, r.shape())
    }

    @Test fun xtream_listes_entierement_vides_gardent_le_catalogue() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        mode = Mode.EMPTY
        assertEquals(SyncErrorKind.EMPTY_LIST, r.syncFails())
        assertEquals("catalogue intact après des listes []", full, r.shape())
    }

    @Test fun xtream_categories_vides_mais_titres_presents_gardent_le_catalogue() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        mode = Mode.EMPTY_CATS_ONLY
        assertEquals(SyncErrorKind.EMPTY_LIST, r.syncFails())
        assertEquals(full, r.shape())
    }

    @Test fun xtream_premiere_synchro_vide_est_acceptee() {
        mode = Mode.EMPTY
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(listOf(0, 0, 0, 0), r.shape())
        assertTrue(r.bus.failure.value == null)
    }

    @Test fun xtream_une_categorie_qui_se_vide_reste_legitime() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        mode = Mode.ONE_CAT_EMPTY
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals("les 2 chaînes de la catégorie L1 retirées, le reste en place", listOf(4, 4, 3, 7), r.shape())
    }

    @Test fun xtream_baisse_brutale_10000_vers_12_refusee() {
        liveCount = 1_200
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(1_200, r.shape()[0])
        mode = Mode.FEW   // 3 chaînes sur 1 200 (< 2 %)
        assertEquals(SyncErrorKind.EMPTY_LIST, r.syncFails())
        assertEquals(1_200, r.shape()[0])
    }

    @Test fun xtream_petite_source_qui_diminue_fortement_reste_acceptee() {
        liveCount = 60
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        mode = Mode.FEW   // 60 → 3 : sous le seuil de taille, une petite liste peut vraiment fondre
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(3, r.shape()[0])
    }

    @Test fun m3u_page_vide_ou_html_garde_les_chaines() {
        val r = rig("M3U")
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(6, r.shape()[0])
        mode = Mode.EMPTY
        assertEquals(SyncErrorKind.EMPTY_LIST, r.syncFails())
        assertEquals(6, r.shape()[0])
        mode = Mode.EXPIRED
        r.syncFails()
        assertEquals("page HTML d'erreur : chaînes gardées", 6, r.shape()[0])
    }

    private fun Rig.liveCats() = runBlocking { db.categoryDao().forProviderKind(pid, "LIVE") }.sortedBy { it.remoteId }

    /** C1 : liste de démonstration (1 catégorie, 3 chaînes) d'un compte expiré : chaînes ET catégories gardées, réglages compris. */
    @Test fun xtream_liste_de_demonstration_garde_les_categories_masquees_et_verrouillees() {
        liveCount = 1_200
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        val l3 = r.liveCats().first { it.remoteId == "L3" }
        runBlocking {
            r.db.categoryDao().setEnabled(r.pid, "LIVE", listOf("L2"), false)
            r.db.categoryDao().setLocked(l3.id, true)
        }
        val avant = r.liveCats().map { Triple(it.remoteId, it.enabled, it.locked) }
        val chaines = r.shape()[0]
        mode = Mode.DEMO
        r.syncFails()
        assertEquals("catégories, masquage et verrou intacts", avant, r.liveCats().map { Triple(it.remoteId, it.enabled, it.locked) })
        assertEquals("chaînes gardées", chaines, r.shape()[0])
    }

    /** C1 : le panneau ne renvoie qu'une partie des catégories mais toutes les chaînes : aucune chaîne orpheline. */
    @Test fun xtream_categories_partielles_gardent_celles_encore_utilisees() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        mode = Mode.PARTIAL_CATS
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(6, r.shape()[0])
        assertEquals(listOf("L1", "L2", "L3"), r.liveCats().map { it.remoteId })
    }

    @Test fun suppression_explicite_de_la_source_vide_tout() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        runBlocking { r.repo.delete(r.pid) }
        assertEquals(listOf(0, 0, 0, 0), r.shape())
    }
}

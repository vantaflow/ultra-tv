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
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Garde anti-effacement en mode « une requête par catégorie » et dans le repli catégorie par catégorie (I2, I3 de la
 * relecture robustesse 1). Faux panneau Xtream LOCAL qui respecte `category_id` : 10 catégories de 10 chaînes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GardeParCategorieTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private val dbs = mutableListOf<UltraDb>()

    private enum class Mode { FULL, RATE_LIMITED, CUT_THEN_EMPTY }
    @Volatile private var mode = Mode.FULL

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
    private fun chans(cat: Int) = (1..10).map { i -> val id = cat * 100 + i; """{"num":$id,"name":"Chaine $id","stream_id":$id,"category_id":"C$cat"}""" }

    private fun respond(r: RecordedRequest): MockResponse {
        val url = r.requestUrl!!
        if (url.encodedPath.endsWith("xmltv.php")) return MockResponse().setResponseCode(404)
        val cat = url.queryParameter("category_id")
        return when (url.queryParameter("action")) {
            null -> json("""{"user_info":{"username":"u","auth":1,"status":"Active","max_connections":"1"},"server_info":{"url":"local","timezone":"UTC"}}""")
            "get_live_categories" -> json((1..10).joinToString(",", "[", "]") { """{"category_id":"C$it","category_name":"Cat $it"}""" })
            "get_live_streams" -> when {
                cat == null && mode == Mode.CUT_THEN_EMPTY -> json("""[{"num":1,"name":"Chai""")   // liste coupée en route
                cat == null -> json((1..10).flatMap { chans(it) }.joinToString(",", "[", "]"))
                mode == Mode.FULL -> json(chans(cat.removePrefix("C").toInt()).joinToString(",", "[", "]"))
                // Panneau qui limite le débit : `[]` pour toutes les catégories sauf la première.
                mode == Mode.RATE_LIMITED && cat == "C1" -> json(chans(1).joinToString(",", "[", "]"))
                else -> json("[]")
            }
            else -> json("[]")
        }
    }

    private class Rig(val db: UltraDb, val repo: ProviderRepository, val bus: SyncStatusBus, val pid: Long)

    private fun rig(): Rig {
        val db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries()
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback()).build().also { dbs += it }
        val ok = OkHttpClient.Builder().build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val bus = SyncStatusBus()
        val repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = bus, xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = UserPreferencesStore(ctx, profiles),
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx), langDisabled = LangDisabledStore(ctx),
            hiddenChannels = com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()),
        )
        val id = runBlocking { db.providerDao().upsert(ProviderEntity(name = "local", kind = "XTREAM", baseUrl = server.url("/").toString().trimEnd('/'), username = "u", password = "p", active = true)) }
        return Rig(db, repo, bus, id)
    }

    private fun Rig.channels(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM channel").use { it.moveToFirst(); it.getInt(0) }

    private fun Rig.syncFails(): SyncErrorKind = runBlocking {
        try {
            repo.syncAll(pid, force = true)
            fail("la synchro aurait dû échouer (mode $mode) ; chaînes après : ${channels()}")
            error("inaccessible")
        } catch (t: Throwable) {
            if (t is AssertionError) throw t
            NetErrors.classify(t)
        }
    }

    /** I3 : 3 catégories actives sur 10 (une requête par catégorie) ; une seule répond, les deux autres `[]` : rien n'est vidé. */
    @Test fun parCategorie_uneSeuleQuiRepond_neVidePasLesAutres() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(100, r.channels())
        runBlocking { r.db.categoryDao().setEnabled(r.pid, "LIVE", (4..10).map { "C$it" }, false) }
        runBlocking { r.repo.syncAll(r.pid, force = true) }   // purge des désactivées, puis lecture par catégorie
        assertEquals(30, r.channels())
        mode = Mode.RATE_LIMITED
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals("C2 et C3 gardées", 30, r.channels())
    }

    /** I2 : liste complète coupée puis `[]` pour chaque catégorie : refus, rien n'est vidé (avant : tout vidé, en succès). */
    @Test fun replisApresCoupure_categoriesToutesVides_refuseEtGardeTout() {
        val r = rig()
        runBlocking { r.repo.syncAll(r.pid, force = true) }
        assertEquals(100, r.channels())
        runBlocking { r.db.providerDao().setCategoryFilter(r.pid, 1) }
        mode = Mode.CUT_THEN_EMPTY
        assertEquals(SyncErrorKind.EMPTY_LIST, r.syncFails())
        assertEquals(100, r.channels())
    }
}

package com.ultratv.tv.nativeapp.data.mediaserver

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.security.SecretBox
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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

/**
 * Ajout d'un serveur Jellyfin et indexation de ses bibliothèques dans le catalogue local (Room), avec un faux serveur
 * en mémoire : le jeton est chiffré au repos et n'apparaît dans AUCUNE adresse stockée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaServerSyncTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val toClose = mutableListOf<UltraDb>()
    private lateinit var savedBox: SecretBox
    private val token = "jeton-secret-xyz"

    @After fun cleanup() { toClose.forEach { runCatching { it.close() } }; if (::savedBox.isInitialized) SecretBox.shared = savedBox }

    private val fake = Interceptor { chain ->
        val req = chain.request()
        val path = req.url.encodedPath
        fun ok(body: String) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        fun code(c: Int) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(c).message("x").body("".toResponseBody(null)).build()
        when {
            path == "/Users/AuthenticateByName" -> {
                val sent = okio.Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                if (sent.contains("\"Pw\":\"bon\"")) ok("""{"User":{"Id":"u1","Name":"lilou"},"AccessToken":"$token"}""") else code(401)
            }
            req.header("X-Emby-Token") != token -> code(401)
            path == "/Users/u1/Views" -> ok("""{"Items":[{"Name":"Films","Id":"lib1","CollectionType":"movies"},{"Name":"Séries","Id":"lib2","CollectionType":"tvshows"}]}""")
            path == "/Users/u1/Items" -> if (req.url.queryParameter("IncludeItemTypes") == "Series")
                ok("""{"Items":[{"Name":"La Série","Id":"ser1","Type":"Series"},{"Name":"Autre Série","Id":"ser2","Type":"Series"}],"TotalRecordCount":2}""")
            else ok("""{"Items":[{"Name":"Film A","Id":"mA","Type":"Movie","ProductionYear":2001,"ImageTags":{"Primary":"p"},"MediaSources":[{"Id":"mA","Container":"mkv"}]},{"Name":"Film B","Id":"mB","Type":"Movie"},{"Name":"Film C","Id":"mC","Type":"Movie"}],"TotalRecordCount":3}""")
            path == "/Shows/ser1/Episodes" -> ok("""{"Items":[{"Name":"Pilote","Id":"e1","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1},{"Name":"Deux","Id":"e2","Type":"Episode","ParentIndexNumber":1,"IndexNumber":2}],"TotalRecordCount":2}""")
            else -> code(404)
        }
    }

    private fun repo(name: String): Pair<UltraDb, ProviderRepository> {
        savedBox = SecretBox.shared
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        SecretBox.shared = SecretBox { key }
        val dir = File(System.getProperty("java.io.tmpdir"), "ultratv-media-$name").also { it.deleteRecursively(); it.mkdirs() }
        val db = Room.databaseBuilder(ctx, UltraDb::class.java, File(dir, "db.sqlite").absolutePath)
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build().also { toClose += it }
        val ok = OkHttpClient.Builder().addInterceptor(fake).build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val prefs = UserPreferencesStore(ctx, profiles)
        val repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = SyncStatusBus(), xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = prefs,
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx), langDisabled = LangDisabledStore(ctx),
            hiddenChannels = com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()),
            mediaServer = MediaServerClient(ok, { "dev-test" }),
        )
        return db to repo
    }

    private fun UltraDb.n(sql: String): Int = openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }
    private fun UltraDb.s(sql: String): String = openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getString(0) }

    @Test fun ajout_puisSynchro_bibliothequesIndexeesDansLeCatalogue_jetonChiffreEtAbsentDesAdresses() = runBlocking {
        val (db, repo) = repo("sync")
        val id = repo.addMediaServer(MediaServer.JELLYFIN, "", "jf.invalid:8096/web/", "lilou", "bon")
        val p = repo.byId(id)!!
        assertEquals("JELLYFIN", p.kind); assertEquals("http://jf.invalid:8096", p.baseUrl); assertEquals("u1", p.username); assertEquals(token, p.password)
        assertEquals("jf.invalid", p.name)
        // Au repos, le jeton est chiffré.
        val raw = db.s("SELECT password FROM provider WHERE id = $id")
        assertTrue(raw.startsWith(SecretBox.PREFIX)); assertFalse(raw.contains(token))

        repo.syncAll(id, force = true)
        assertEquals(3, db.n("SELECT COUNT(*) FROM movie WHERE providerId = $id"))
        assertEquals(2, db.n("SELECT COUNT(*) FROM series WHERE providerId = $id"))
        assertEquals(1, db.n("SELECT COUNT(*) FROM category WHERE providerId = $id AND kind = 'MOVIE' AND remoteId = 'lib1'"))
        assertEquals(1, db.n("SELECT COUNT(*) FROM category WHERE providerId = $id AND kind = 'SERIES' AND remoteId = 'lib2'"))
        assertEquals(0, db.n("SELECT COUNT(*) FROM channel WHERE providerId = $id"))
        assertEquals("mediaserver://$id/mA", db.s("SELECT streamUrl FROM movie WHERE remoteId = 'mA'"))
        assertEquals(0, db.n("SELECT COUNT(*) FROM movie WHERE streamUrl LIKE '%$token%' OR poster LIKE '%$token%' OR backdrop LIKE '%$token%'"))
        assertEquals(0, db.n("SELECT COUNT(*) FROM series WHERE poster LIKE '%$token%'"))
        assertTrue(db.s("SELECT lastVodSyncAt FROM provider WHERE id = $id").toLong() > 0)

        // Seconde synchro : rien de dupliqué.
        repo.syncAll(id, force = true)
        assertEquals(3, db.n("SELECT COUNT(*) FROM movie WHERE providerId = $id"))
    }

    @Test fun ajout_identifiantsRefuses_rienNEstEnregistre() = runBlocking {
        val (db, repo) = repo("refus")
        try { repo.addMediaServer(MediaServer.EMBY, "", "http://jf.invalid", "lilou", "mauvais"); org.junit.Assert.fail("doit échouer") } catch (e: com.ultratv.tv.nativeapp.data.net.HttpStatusException) { assertEquals(401, e.code) }
        assertEquals(0, db.n("SELECT COUNT(*) FROM provider"))
    }

    @Test fun ajout_adresseInvalide() = runBlocking {
        val (_, repo) = repo("url")
        try { repo.addMediaServer(MediaServer.JELLYFIN, "", "ftp://nas", "a", "b"); org.junit.Assert.fail("doit échouer") } catch (e: IllegalArgumentException) { /* attendu */ }
    }

    @Test fun ajoutDeuxFois_memeUtilisateur_memeSource() = runBlocking {
        val (db, repo) = repo("deux")
        val a = repo.addMediaServer(MediaServer.JELLYFIN, "Salon", "http://jf.invalid", "lilou", "bon")
        val b = repo.addMediaServer(MediaServer.JELLYFIN, "Autre nom", "http://jf.invalid/", "lilou", "bon")
        assertEquals(a, b)
        assertEquals(1, db.n("SELECT COUNT(*) FROM provider"))
    }
}

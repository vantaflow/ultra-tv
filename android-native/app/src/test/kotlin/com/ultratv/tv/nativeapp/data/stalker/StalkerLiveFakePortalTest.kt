package com.ultratv.tv.nativeapp.data.stalker

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import com.ultratv.tv.nativeapp.ui.player.engine.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct Stalker contre un faux portail ([FakeStalkerPortal]) : en-têtes MAG à la lecture, lien create_link à usage
 * unique redemandé à chaque ouverture (nouvelle tentative, reconnexion, changement de moteur), `stream=` vide complété,
 * refus du portail (403 / 458) expliqué à l'utilisateur. Le « moteur » du test fait une vraie requête HTTP avec les
 * en-têtes et le User-Agent que reçoit le vrai moteur (EngineConfig), comme le fait ExoPlayer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StalkerLiveFakePortalTest {
    private val portal = FakeStalkerPortal()
    private val http = OkHttpClient()
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scope = TestScope(StandardTestDispatcher())
    private val created = mutableListOf<HttpEngine>()

    /** Moteur de test : ouvre l'adresse en HTTP avec config.userAgent + config.headers ; 2xx → image, sinon erreur classée. */
    private inner class HttpEngine(override val kind: EngineKind, override val config: EngineConfig) : PlayerEngine {
        val codes = mutableListOf<Int>()
        val bus = MutableSharedFlow<EngineEvent>(replay = 16, extraBufferCapacity = 16)
        override val view: View = View(ctx)
        override val events = bus
        override val reusable: Boolean get() = true
        override fun load(url: String, startPositionMs: Long) {
            val req = Request.Builder().url(url).header("User-Agent", config.userAgent).apply { config.headers.forEach { (k, v) -> header(k, v) } }.build()
            val code = http.newCall(req).execute().use { it.code }
            codes += code
            bus.tryEmit(if (code in 200..299) EngineEvent.FirstFrame else EngineEvent.Error(PlaybackPlanner.classifyExo(2004, code), code))
        }
        override fun play() {}
        override fun pause() {}
        override fun seekTo(ms: Long) {}
        override val isPlaying = true
        override val positionMs = 0L
        override val durationMs = -1L
        override fun audioTracks() = emptyList<TrackInfo>()
        override fun subtitleTracks() = emptyList<TrackInfo>()
        override fun selectAudio(id: String) {}
        override fun selectSubtitle(id: String?) {}
        override fun setAspect(mode: AspectMode) {}
        override fun setSpeed(speed: Float) {}
        override val hasVideo = true
        override fun limitQuality(maxHeight: Int, maxBitrateBps: Int?) {}
        override fun stats() = EngineStats()
        override fun release() {}
    }

    private val settings = ResolvedPlayback(
        engine = EngineChoice.EXO, decoder = DecoderMode.AUTO, buffer = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 192, lowRam = false),
        bufferPreset = BufferPreset.AUTO, maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false,
        manualEngine = false, manualDecoder = false, manualBuffer = false,
    )

    @Before fun up() { portal.start(); com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = true }
    @After fun down() { portal.shutdown(); com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = false; scope.cancel() }

    private fun provider() = ProviderEntity(id = 5, name = "P", kind = "STALKER", baseUrl = portal.portalUrl().trimEnd('/'), username = portal.mac, password = "")
    private val client by lazy { StalkerClient(OkHttpClient(), StalkerEndpointStore.inMemory()) }
    private val resolver by lazy { DefaultStreamUrlResolver(PortalDao.of(provider()), client) }
    private val chUrl = StalkerLink.build(5, "itv", "ffmpeg http://localhost/ch/1234_", chId = "1234")

    private fun session(opener: suspend (String) -> StreamOpen = { resolver.open(it) }) = PlaybackSession(
        ctx, scope, FrameLayout(ctx), { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
        isLive = true, autoFrameRate = false, userAgent = "UltraTV/1.0 (Android TV)", opener = opener,
        engineFactory = { k, c -> HttpEngine(k, c).also { created += it } },
    )

    /** Fait avancer le planificateur de test jusqu'à [cond] (le client Stalker passe par Dispatchers.IO : vrai temps). */
    private fun settle(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 8_000
        while (true) {
            scope.runCurrent()
            if (cond()) return
            if (System.currentTimeMillis() > end) fail("délai dépassé : $what")
            Thread.sleep(15)
        }
    }

    // ─── a) en-têtes MAG ───

    @Test fun direct_lectureAvecLesEnTetesMag_dansLeLecteur() {
        val s = session()
        s.start(chUrl, "5:1234"); settle("première image") { portal.liveStatuses.isNotEmpty() }
        assertEquals(listOf(200), portal.liveStatuses)
        val r = portal.liveRequests.single()
        assertEquals(StalkerDevice.USER_AGENT, r.getHeader("User-Agent"))
        assertEquals(StalkerDevice.X_USER_AGENT, r.getHeader("X-User-Agent"))
        assertEquals("mac=00%3A1A%3A79%3AAB%3ACD%3AEF; stb_lang=en; timezone=UTC", r.getHeader("Cookie"))
        assertTrue(r.getHeader("Authorization")!!.startsWith("Bearer TOKEN"))
        settle("lecture") { s.state.value.phase == Phase.PLAYING }
    }

    @Test fun portail_refuseLiveSansEnTetesMag_maisServeLesFilmsSansControle() {
        // Lecteur « d'avant » : User-Agent de l'appli et aucun en-tête → 403 sur live.php.
        val link = runBlocking { client.createLink(provider(), "itv", "ffmpeg http://localhost/ch/1234_", null, "1234") }
        val bare = http.newCall(Request.Builder().url(link).header("User-Agent", "UltraTV/1.0 (Android TV)").build()).execute().use { it.code }
        assertEquals(403, bare)
        // Film : servi sans User-Agent MAG ni cookie.
        val vod = runBlocking { client.createLink(provider(), "vod", "eyJ9") }
        assertEquals(200, http.newCall(Request.Builder().url(vod).header("User-Agent", "UltraTV/1.0 (Android TV)").build()).execute().use { it.code })
    }

    // ─── b) lien à usage unique ───

    @Test fun retry_redemandeUnLien() {
        val s = session()
        s.start(chUrl, "5:1234"); settle("1re ouverture") { portal.liveStatuses.size == 1 }
        s.retry(); settle("retry") { portal.liveStatuses.size == 2 }
        assertEquals(listOf(200, 200), portal.liveStatuses)   // sans nouveau lien : 458 (jeton déjà utilisé)
        assertEquals(2, portal.createLinks.get())
    }

    @Test fun reconnexionDuDirect_redemandeUnLien() {
        val s = session()
        s.start(chUrl, "5:1234"); settle("1re ouverture") { portal.liveStatuses.size == 1 }
        created.single().bus.tryEmit(EngineEvent.Ended)
        scope.advanceTimeBy(600); settle("reconnexion") { portal.liveStatuses.size == 2 }
        assertEquals(listOf(200, 200), portal.liveStatuses)
        assertEquals(2, portal.createLinks.get())
    }

    @Test fun bascule_deMoteur_redemandeUnLien() {
        val s = session()
        s.start(chUrl, "5:1234"); settle("1re ouverture") { portal.liveStatuses.size == 1 }
        s.switchTo(Combo(EngineKind.VLC, DecoderMode.AUTO)); settle("2e moteur") { portal.liveStatuses.size == 2 }
        assertEquals(listOf(200, 200), portal.liveStatuses)
        assertEquals(2, created.size)
        assertEquals(StalkerDevice.USER_AGENT, created.last().config.userAgent)
        assertTrue(created.last().config.headers["Cookie"]!!.startsWith("mac="))
    }

    @Test fun zapping_memeMoteur_lienFraisEtEnTetes() {
        val s = session()
        s.start(chUrl, "5:1234"); settle("1re chaîne") { portal.liveStatuses.size == 1 }
        s.start(StalkerLink.build(5, "itv", "ffmpeg http://localhost/ch/77_", chId = "77"), "5:77"); settle("2e chaîne") { portal.liveStatuses.size == 2 }
        assertEquals(listOf(200, 200), portal.liveStatuses)
        assertEquals(1, created.size)   // même moteur réutilisé
    }

    // ─── c) stream= vide ───

    @Test fun streamVide_completeAvecLIdentifiantDeLaChaine() {
        portal.emptyStreamResponses = 1
        val s = session()
        s.start(chUrl, "5:1234"); settle("ouverture") { portal.liveStatuses.isNotEmpty() }
        assertEquals(listOf(200), portal.liveStatuses)
        assertEquals("1234", portal.liveRequests.single().requestUrl!!.queryParameter("stream"))
        assertEquals(1, portal.createLinks.get())
    }

    // ─── d) refus du portail ───

    @Test fun portailRefuse_403_messageDedie_sansBoucle() {
        val s = session { throw HttpStatusException(403) }
        s.start(chUrl, "5:1234"); scope.runCurrent()
        scope.advanceTimeBy(2_000); scope.runCurrent()
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.PORTAL_REFUSED, s.state.value.error)
    }

    @Test fun erreur458_enLecture_nouvelleOuvertureAvecLienFrais() {
        val s = session()
        s.start(chUrl, "5:1234"); settle("lecture") { s.state.value.phase == Phase.PLAYING }
        // Un lecteur qui rouvrirait l'ancien lien recevrait 458 ; le moteur le signale, la session se reconnecte avec un lien frais.
        created.last().bus.tryEmit(EngineEvent.Error(PlayErrorKind.REFUSED, 458))
        scope.advanceTimeBy(600); settle("reconnexion") { portal.liveStatuses.size == 2 }
        assertEquals(listOf(200, 200), portal.liveStatuses)
        assertEquals(2, portal.createLinks.get())
    }

    @Test fun journaux_codeHttpEtTypeSeulement_sansMacNiHote() {
        val line = StalkerPlayLog.openFailed(java.io.IOException("http://203.0.113.9:8080/c/ mac=00:1A:79:AB:CD:EF"))
        assertTrue(line, line.contains("type=IOException"))
        assertFalse(line, line.contains("203.0.113.9") || line.contains("AB:CD") || line.contains("8080"))
        val http = StalkerPlayLog.openFailed(HttpStatusException(458, "HTTP 458 portal.example"))
        assertTrue(http, http.contains("http=458"))
        assertFalse(http, http.contains("portal.example"))
        assertFalse(StalkerPlayLog.playerError("REFUSED", 403, "EXO").contains("//"))
    }
}

/** ProviderDao minimal pour le résolveur (aucune base). */
private object PortalDao {
    fun of(p: ProviderEntity): com.ultratv.tv.nativeapp.data.db.ProviderDao {
        val raw = java.lang.reflect.Proxy.newProxyInstance(
            com.ultratv.tv.nativeapp.data.db.ProviderRawDao::class.java.classLoader,
            arrayOf(com.ultratv.tv.nativeapp.data.db.ProviderRawDao::class.java),
        ) { _, m, _ -> if (m.name == "byId") p else null } as com.ultratv.tv.nativeapp.data.db.ProviderRawDao
        return com.ultratv.tv.nativeapp.data.db.ProviderDao(raw)
    }
}

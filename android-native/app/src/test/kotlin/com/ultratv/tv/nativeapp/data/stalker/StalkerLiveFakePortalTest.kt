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
        var token: String = ""
        var cutSent = false
        val bus = MutableSharedFlow<EngineEvent>(replay = 16, extraBufferCapacity = 16)
        override val view: View = View(ctx)
        override val events = bus
        override val reusable: Boolean get() = true
        override fun load(url: String, startPositionMs: Long) {
            val req = Request.Builder().url(url).header("User-Agent", config.userAgent).apply { config.headers.forEach { (k, v) -> header(k, v) } }.build()
            val code = http.newCall(req).execute().use { it.code }
            codes += code; token = req.url.queryParameter("play_token").orEmpty(); cutSent = false
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

    /** Temps virtuel (ms) vu par le faux portail : avancé par [advance], jamais attendu pour de vrai. */
    private val vnow = java.util.concurrent.atomic.AtomicLong()

    /** Avance de [ms] par pas d'une seconde ; à chaque pas, un flux que le portail coupe faute de présence signale l'erreur. */
    private fun advance(ms: Long) {
        repeat((ms / 1_000).toInt()) {
            scope.advanceTimeBy(1_000); vnow.addAndGet(1_000)
            scope.runCurrent(); Thread.sleep(3); scope.runCurrent()
            created.lastOrNull()?.let { e ->
                if (e.token.isNotEmpty() && !e.cutSent && portal.isCut(e.token, vnow.get())) { e.cutSent = true; e.bus.tryEmit(EngineEvent.Error(PlayErrorKind.NETWORK)) }
            }
        }
    }

    @Before fun up() { portal.clock = { vnow.get() }; portal.start(); com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = true }
    @After fun down() { portal.shutdown(); com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = false; scope.cancel() }

    private fun provider() = ProviderEntity(id = 5, name = "P", kind = "STALKER", baseUrl = portal.portalUrl().trimEnd('/'), username = portal.mac, password = "")
    private val client by lazy { StalkerClient(OkHttpClient(), StalkerEndpointStore.inMemory()) }
    private val resolver by lazy { DefaultStreamUrlResolver(PortalDao.of(provider()), client) }
    private val chUrl = StalkerLink.build(5, "itv", "ffmpeg http://localhost/ch/1234_", chId = "1234")

    private fun session(
        withPresence: Boolean = false,
        opener: suspend (String) -> StreamOpen = { resolver.open(it) },
    ) = PlaybackSession(
        ctx, scope, FrameLayout(ctx), { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
        isLive = true, autoFrameRate = false, userAgent = "UltraTV/1.0 (Android TV)", opener = opener,
        presence = if (withPresence) { u -> resolver.presence(u) } else null,
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
        val s = session(opener = { throw HttpStatusException(403) })
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

    @Test fun erreurAvantLecture_nouvelleTentative_lienFrais() {
        portal.forbidNextLive = 1
        val s = session()
        s.start(chUrl, "5:1234"); settle("1re ouverture refusée") { portal.liveStatuses.size == 1 }
        scope.advanceTimeBy(2_000); settle("nouvelle tentative") { portal.liveStatuses.size == 2 }
        assertEquals(listOf(403, 200), portal.liveStatuses)
        assertEquals(2, portal.createLinks.get())
    }

    // ─── présence (watchdog) : le portail coupe le direct d'un boîtier qui ne se manifeste plus ───

    @Test fun avecWatchdog_leDirectTientPlusDe5Minutes_etSurvitAUneCoupureReseau() {
        val s = session(withPresence = true)
        s.start(chUrl, "5:1234"); settle("lecture") { s.state.value.phase == Phase.PLAYING }
        advance(180_000)
        // Coupure réseau : le moteur signale l'erreur, la session se reconnecte avec un lien NEUF (l'ancien est périmé : 458).
        created.last().bus.tryEmit(EngineEvent.Error(PlayErrorKind.NETWORK)); scope.runCurrent()
        advance(2_000); settle("reconnexion") { portal.liveStatuses.size == 2 }
        advance(240_000)   // 6 min 2 s au total
        assertEquals(listOf(200, 200), portal.liveStatuses)
        assertEquals(2, portal.createLinks.get())
        assertEquals(Phase.PLAYING, s.state.value.phase)
        assertTrue("un signal toutes les ~2 min : ${portal.watchdogs}", portal.watchdogs.size >= 3)
    }

    @Test fun sansWatchdog_leDirectEstCoupeApres10s_puisErreurReseau() {
        val s = session(withPresence = false)
        s.start(chUrl, "5:1234"); settle("lecture") { s.state.value.phase == Phase.PLAYING }
        advance(300_000)
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.NETWORK, s.state.value.error)
        assertTrue(portal.watchdogs.isEmpty())
    }

    @Test fun sourceNonStalker_aucunSignalDePresence() {
        val s = session(withPresence = true)
        s.start(portal.server.url("/vod/abc.mkv").toString(), "9:1"); advance(300_000)
        assertTrue(portal.watchdogs.isEmpty())
        assertEquals(0, portal.handshakes.get())
    }

    @Test fun presence_arretee_quandLaLectureSeTermine() {
        val s = session(withPresence = true)
        s.start(chUrl, "5:1234"); settle("lecture") { s.state.value.phase == Phase.PLAYING }
        s.release(); val n = portal.watchdogs.size
        advance(300_000)
        assertEquals(n, portal.watchdogs.size)
    }

    @Test fun journaux_reconnexion_codeHttpEtDuree() {
        val l = StalkerPlayLog.reconnect(2, 458, 12_900)
        assertEquals("stalker live reconnect n=2 http=458 played=12s", l)
        assertEquals("stalker live reconnect n=1 http=- played=0s", StalkerPlayLog.reconnect(1, null, 0))
    }

    // ─── set_played ───

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond()) { if (System.currentTimeMillis() > end) fail("délai dépassé : $what"); Thread.sleep(15) }
    }

    @Test fun setPlayed_envoyeUneFoisParOuvertureDeChaine_avecEnTetesEtJeton() {
        val s = session()
        s.start(chUrl, "5:1234"); settle("1re ouverture") { portal.liveStatuses.size == 1 }
        waitFor("set_played") { portal.setPlayedIds.size == 1 }
        assertEquals(listOf("1234"), portal.setPlayedIds)
        val r = portal.setPlayedRequests.single()
        assertEquals("itv", r.requestUrl!!.queryParameter("type")); assertEquals("1-xml", r.requestUrl!!.queryParameter("JsHttpRequest"))
        assertEquals(StalkerDevice.USER_AGENT, r.getHeader("User-Agent"))
        assertTrue(r.getHeader("Cookie")!!.startsWith("mac=")); assertTrue(r.getHeader("Authorization")!!.startsWith("Bearer TOKEN"))
        s.retry(); settle("2e ouverture") { portal.liveStatuses.size == 2 }
        waitFor("2e set_played") { portal.setPlayedIds.size == 2 }
        // Un film n'envoie rien.
        runBlocking { client.openLink(provider(), "vod", "eyJ9") }
        Thread.sleep(200)
        assertEquals(2, portal.setPlayedIds.size)
    }

    @Test fun setPlayed_enEchec_nEmpechePasLaLecture() {
        portal.setPlayedStatus = 500
        val s = session()
        s.start(chUrl, "5:1234"); settle("lecture") { s.state.value.phase == Phase.PLAYING }
        assertEquals(listOf(200), portal.liveStatuses)
    }

    // ─── relais de différé, téléchargements, enregistrements ───

    @Test fun differe_relais_enTetesMag_lienNeuf_etPresence() {
        val up = com.ultratv.tv.nativeapp.data.timeshift.TimeshiftUpstream({ resolver.open(it) }, { resolver.presence(it) }, presenceMs = 50)
        val tmp = java.io.File.createTempFile("tsbuf", ".buf")
        val proxy = com.ultratv.tv.nativeapp.data.timeshift.TimeshiftProxy(http, chUrl, "UltraTV/1.0 (Android TV)", tmp, 188L * 1000, 1_000_000, up)
        try {
            proxy.start()
            waitFor("ouverture amont") { portal.liveStatuses.isNotEmpty() }
            assertEquals(listOf(200), portal.liveStatuses)
            val r = portal.liveRequests.first()
            assertEquals(StalkerDevice.USER_AGENT, r.getHeader("User-Agent"))
            assertEquals(StalkerDevice.X_USER_AGENT, r.getHeader("X-User-Agent"))
            assertTrue(r.getHeader("Cookie")!!.startsWith("mac=")); assertTrue(r.getHeader("Authorization")!!.startsWith("Bearer TOKEN"))
            assertEquals(1, portal.createLinks.get())
            waitFor("présence pendant le relais") { portal.watchdogs.size >= 2 }
        } finally { proxy.close(); tmp.delete() }
        // Nouveau relais = nouveau lien (l'ancien est périmé : 458 sinon).
        val tmp2 = java.io.File.createTempFile("tsbuf", ".buf")
        val p2 = com.ultratv.tv.nativeapp.data.timeshift.TimeshiftProxy(http, chUrl, "UltraTV/1.0 (Android TV)", tmp2, 188L * 1000, 1_000_000, up)
        try { p2.start(); waitFor("2e ouverture") { portal.liveStatuses.size == 2 }; assertEquals(listOf(200, 200), portal.liveStatuses); assertEquals(2, portal.createLinks.get()) }
        finally { p2.close(); tmp2.delete() }
    }

    @Test fun telechargement_enTetesMag_etLienNeufAChaqueTentative() {
        portal.failLive5xx = 1   // 1re tentative : 503 passager → la 2e doit demander un NOUVEAU lien
        val dir = java.nio.file.Files.createTempDirectory("dl").toFile()
        val dl = com.ultratv.tv.nativeapp.data.downloads.HttpRangeDownloader(http, freeBytes = { Long.MAX_VALUE }, sleep = {})
        val out = java.io.File(dir, "x.ts")
        val res = runBlocking { dl.download(chUrl, out, open = { resolver.open(chUrl) }) }
        assertTrue(res.toString(), res is com.ultratv.tv.nativeapp.data.downloads.HttpRangeDownloader.Outcome.Completed)
        assertEquals(listOf(503, 200), portal.liveStatuses)
        assertEquals(2, portal.createLinks.get())
        val r = portal.liveRequests.last()
        assertEquals(StalkerDevice.USER_AGENT, r.getHeader("User-Agent"))
        assertTrue(r.getHeader("Cookie")!!.startsWith("mac=")); assertTrue(r.getHeader("Authorization")!!.startsWith("Bearer TOKEN"))
        dir.deleteRecursively()
    }

    @Test fun enregistrement_clientAvecEnTetesMag_seulementPourLHoteDuFlux() {
        val opened = runBlocking { resolver.open(chUrl) }
        val rec = http.withStreamHeaders(opened)   // ce que RecordingWorker utilise pour le flux et pour HLS
        assertEquals(200, rec.newCall(Request.Builder().url(opened.url).build()).execute().use { it.code })
        assertEquals(StalkerDevice.USER_AGENT, portal.liveRequests.single().getHeader("User-Agent"))
        // Autre hôte : ni cookie ni jeton.
        val other = okhttp3.mockwebserver.MockWebServer().also { it.start() }
        try {
            other.enqueue(okhttp3.mockwebserver.MockResponse().setBody("x"))
            rec.newCall(Request.Builder().url(other.url("/seg.ts")).build()).execute().close()
            val seen = other.takeRequest()
            assertEquals(null, seen.getHeader("Cookie")); assertEquals(null, seen.getHeader("Authorization"))
        } finally { other.shutdown() }
        // Source ordinaire : client inchangé.
        assertTrue(http.withStreamHeaders(StreamOpen("http://h/x.ts")) === http)
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

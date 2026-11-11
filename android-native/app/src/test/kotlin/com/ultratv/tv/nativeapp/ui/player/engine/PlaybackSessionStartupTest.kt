package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import com.ultratv.tv.nativeapp.data.m3u.DrmScheme
import com.ultratv.tv.nativeapp.data.m3u.StreamProps
import com.ultratv.tv.nativeapp.data.stalker.StreamOpen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Démarrage figé : le moteur reste en chargement (manifeste lu, segments qui ne viennent pas, serveur muet…) sans jamais
 * rendre d'image ni signaler d'erreur. Sans délai de démarrage, le lecteur chargeait SANS FIN, sans repli ni message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PlaybackSessionStartupTest {

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig) : PlayerEngine {
        val loads = mutableListOf<String>()
        var released = false
        var progress = -1L
        override fun loadProgress() = progress
        val bus = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events = bus
        override fun load(url: String, startPositionMs: Long) { loads += url }
        override fun loadProtected(url: String, startPositionMs: Long, props: StreamProps) { loads += url }
        override val supportsProtected get() = kind == EngineKind.EXO
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
        override fun release() { released = true }
    }

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val buffer = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 192, lowRam = false)
    private fun settings(e: EngineChoice) = ResolvedPlayback(
        engine = e, decoder = DecoderMode.AUTO, buffer = buffer, bufferPreset = BufferPreset.AUTO,
        maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false,
        manualEngine = false, manualDecoder = false, manualBuffer = false,
    )
    private val scope = TestScope(StandardTestDispatcher())
    private val created = mutableListOf<FakeEngine>()

    @After fun end() { scope.cancel() }

    private val wv = StreamProps(DrmScheme.WIDEVINE, "https://lic.invalid/wv")

    private fun session(choice: EngineChoice = EngineChoice.AUTO, live: Boolean = true) = PlaybackSession(
        ctx, scope, FrameLayout(ctx), { settings(choice) }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
        isLive = live, autoFrameRate = false, userAgent = "test",
        propsLookup = { k -> if (k == "1:p") wv else null },
        opener = { StreamOpen(if (it.startsWith("stalker://")) "http://portal.invalid/real.ts" else it) },
        engineFactory = { k, c -> FakeEngine(k, c).also { created += it } },
    )

    private fun emit(e: EngineEvent) { created.last().bus.tryEmit(e); scope.runCurrent() }
    private fun elapse(ms: Long) { scope.advanceTimeBy(ms); scope.runCurrent() }

    @Test fun chargementSansFinSansImage_repliVersVlcApresLeDelai() {
        val s = session(); s.start("http://a/tvp1.m3u8", "1:a"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        elapse(14_000)
        assertEquals(1, created.size)                       // pas encore : le délai n'est pas écoulé
        elapse(2_000)
        // Le décodage logiciel d'ExoPlayer ne change rien à un flux qui n'arrive pas : on saute directement à VLC.
        assertEquals(listOf(EngineKind.EXO, EngineKind.VLC), created.map { it.kind })
        assertTrue(created[0].released)
        assertEquals(Phase.LOADING, s.state.value.phase)
    }

    @Test fun imageAvantLeDelai_aucunRepli() {
        val s = session(); s.start("http://a/eurosport.m3u8", "1:a"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        elapse(5_000)
        emit(EngineEvent.FirstFrame)
        elapse(60_000)
        assertEquals(1, created.size)
        assertEquals(Phase.PLAYING, s.state.value.phase)
    }

    @Test fun tousLesMoteursFigent_panneauDErreur_pasDeChargementInfini() {
        val s = session(); s.start("http://a/tvp1.m3u8", "1:a"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        elapse(16_000)                                      // EXO -> VLC
        emit(EngineEvent.Buffering)
        elapse(16_000)                                      // VLC -> plus de combinaison
        assertEquals(2, created.size)
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.START_TIMEOUT, s.state.value.error)
    }

    @Test fun fluxProtege_erreurSansRepli() {
        val s = session(); s.start("http://a/p.mpd", "1:p"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        elapse(16_000)
        assertEquals(1, created.size)
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.START_TIMEOUT, s.state.value.error)
    }

    @Test fun stalker_jamaisVlc_erreurApresLeDelai() {
        val s = session(); s.start("stalker://p1/itv?cmd=x", "1:s"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        elapse(16_000)
        assertTrue(created.none { it.kind == EngineKind.VLC })
        assertEquals(Phase.ERROR, s.state.value.phase)
    }

    @Test fun moteurImpose_pasDeChangementDeMoteur() {
        val s = session(EngineChoice.EXO); s.start("http://a/tvp1.m3u8", "1:a"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        elapse(16_000)
        assertTrue(created.none { it.kind == EngineKind.VLC })
        assertEquals(Phase.ERROR, s.state.value.phase)
    }

    @Test fun finDeSession_annuleLeDelai() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        elapse(10_000)
        s.release(); scope.runCurrent()
        elapse(60_000)
        assertEquals(1, created.size)
    }

    @Test fun pretMaisSansImage_laSurveillanceDeLImagePrendLeRelais() {
        // Ready sans image : la surveillance de l'image (8 s) prend le relais du délai de démarrage.
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.Ready)
        elapse(9_000)
        assertEquals(EngineKind.EXO, created.last().kind)  // NO_PICTURE -> décodage logiciel Exo (comportement existant)
        assertEquals(2, created.size)
    }

    @Test fun demarrageLentMaisQuiProgresse_pasCoupe() {
        val s = session(); s.start("http://a/vod.mp4", "1:a"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        // Les segments arrivent lentement : la position tamponnée avance à chaque échéance, le délai est prolongé.
        for (i in 1..3) { created.last().progress = i * 1_000L; elapse(15_000) }
        assertEquals(1, created.size)
        assertEquals(Phase.LOADING, s.state.value.phase)
        emit(EngineEvent.FirstFrame)
        elapse(60_000)
        assertEquals(1, created.size)
        assertEquals(Phase.PLAYING, s.state.value.phase)
    }

    @Test fun demarrageSansAucunProgres_estCoupe() {
        val s = session(); s.start("http://a/vod.mp4", "1:a"); scope.runCurrent()
        emit(EngineEvent.Buffering)
        created.last().progress = 500L
        elapse(15_000)                                       // un premier progrès : prolongé
        assertEquals(1, created.size)
        elapse(15_000)                                       // plus rien : figé
        assertEquals(EngineKind.VLC, created.last().kind)
    }
}

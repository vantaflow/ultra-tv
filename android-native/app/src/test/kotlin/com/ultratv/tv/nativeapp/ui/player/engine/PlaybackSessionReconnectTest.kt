package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct coupé par le serveur (session fermée, réseau, flux figé) : reconnexion automatique sur la même adresse. */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PlaybackSessionReconnectTest {

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig) : PlayerEngine {
        val loads = mutableListOf<String>()
        val bus = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events = bus
        override val reusable: Boolean get() = true
        override fun load(url: String, startPositionMs: Long) { loads += url }
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

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val buffer = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 192, lowRam = false)
    private val settings = ResolvedPlayback(
        engine = EngineChoice.EXO, decoder = DecoderMode.AUTO, buffer = buffer, bufferPreset = BufferPreset.AUTO,
        maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false,
        manualEngine = false, manualDecoder = false, manualBuffer = false,
    )
    private val scope = TestScope(StandardTestDispatcher())
    private val created = mutableListOf<FakeEngine>()

    private fun session(isLive: Boolean = true) = PlaybackSession(
        ctx, scope, FrameLayout(ctx), { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
        isLive = isLive, autoFrameRate = false, userAgent = "test",
        engineFactory = { k, c -> FakeEngine(k, c).also { created += it } },
    )

    private fun emit(e: EngineEvent) { created.last().bus.tryEmit(e); scope.runCurrent() }

    @Test
    fun directQuiJouait_serveurFermeLaSession_seReconnecte() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Ended)
        scope.advanceTimeBy(600); scope.runCurrent()
        assertEquals(listOf("http://a/1", "http://a/1"), created.single().loads)
        assertEquals(Phase.LOADING == s.state.value.phase || s.state.value.phase == Phase.PLAYING, true)
    }

    @Test
    fun directQuiJouait_erreurReseau_seReconnecteSansChangerDeMoteur() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Error(PlayErrorKind.NETWORK))
        scope.advanceTimeBy(600); scope.runCurrent()
        assertEquals(1, created.size)
        assertEquals(2, created.single().loads.size)
    }

    @Test
    fun directFigeEnChargement_seReconnecteApres12s() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Buffering)
        scope.advanceTimeBy(11_000); scope.runCurrent()
        assertEquals(1, created.single().loads.size)
        scope.advanceTimeBy(2_000); scope.runCurrent()
        assertEquals(2, created.single().loads.size)
    }

    @Test
    fun directRepartiAvantLeDelai_pasDeReconnexion() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Buffering)
        scope.advanceTimeBy(3_000); scope.runCurrent()
        emit(EngineEvent.Ready)
        scope.advanceTimeBy(20_000); scope.runCurrent()
        assertEquals(1, created.single().loads.size)
    }

    @Test
    fun coupuresRepetees_abandonApresLesTentatives() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        repeat(9) {
            emit(EngineEvent.Ended)
            scope.advanceTimeBy(11_000); scope.runCurrent()
        }
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(9, created.single().loads.size)   // 1 ouverture + 8 reconnexions
    }

    @Test
    fun video_aLaDemande_finNormale_pasDeReconnexion() {
        val s = session(isLive = false); s.start("http://a/film", "1:f"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Ended)
        scope.advanceTimeBy(5_000); scope.runCurrent()
        assertEquals(Phase.ENDED, s.state.value.phase)
        assertEquals(1, created.single().loads.size)
    }

    @Test
    fun directJamaisDemarre_erreurReseau_pasDeBoucleDeReconnexion() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.Error(PlayErrorKind.NOT_FOUND))
        scope.advanceTimeBy(15_000); scope.runCurrent()
        assertEquals(Phase.ERROR, s.state.value.phase)
    }
}

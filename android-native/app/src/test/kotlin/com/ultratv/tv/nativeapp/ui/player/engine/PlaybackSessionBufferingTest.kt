package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Box 1.3.73-rc9 : direct dont les horodatages reculent, vidéo qui joue mais « Recharger » affiché (le moteur a signalé un
 * chargement sans repasser « prêt »). L'état « buffering » doit suivre la progression réelle (images, position).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PlaybackSessionBufferingTest {

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig) : PlayerEngine {
        val loads = mutableListOf<String>()
        val bus = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        var frames = 0L
        var pos = 0L
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events = bus
        override val reusable: Boolean get() = true
        override fun load(url: String, startPositionMs: Long) { loads += url }
        override fun play() {}
        override fun pause() {}
        override fun seekTo(ms: Long) {}
        override val isPlaying = true
        override val positionMs get() = pos
        override val durationMs = -1L
        override fun renderedFrames(): Long = frames
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

    @org.junit.Before fun visible() { com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = true }
    @org.junit.After fun hidden() { com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = false; scope.cancel() }

    private fun session() = PlaybackSession(
        ctx, scope, FrameLayout(ctx), { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
        isLive = true, autoFrameRate = false, userAgent = "test",
        engineFactory = { k, c -> FakeEngine(k, c).also { created += it } },
        liveEngineMemo = LiveEngineMemo(),
    )

    private fun emit(e: EngineEvent) { created.last().bus.tryEmit(e); scope.runCurrent() }

    /** Avance de [ms] en faisant défiler 25 images/s (et la position) sur le moteur. */
    private fun play(ms: Long) {
        var t = 0L
        while (t < ms) { created.last().frames += 5; created.last().pos += 200; scope.advanceTimeBy(200); scope.runCurrent(); t += 200 }
    }

    @Test
    fun chargementSignale_maisImagesRendues_bufferingRepasseAFaux_sansReconnexion() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Buffering)
        assertTrue(s.state.value.buffering)
        play(1_500)
        assertFalse(s.state.value.buffering)
        play(20_000)   // jamais de « prêt » : pas de reconnexion pour autant, la vidéo joue
        assertFalse(s.state.value.buffering)
        assertEquals(1, created.single().loads.size)
    }

    @Test
    fun chargementAvecProgression_puisPlusRien_bufferingRevient_etReconnexionApres12s() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Buffering)
        play(3_000)
        assertFalse(s.state.value.buffering)
        scope.advanceTimeBy(2_100); scope.runCurrent()   // plus aucune image ni position
        assertTrue(s.state.value.buffering)
        scope.advanceTimeBy(13_000); scope.runCurrent()
        assertEquals(2, created.single().loads.size)
    }

    @Test
    fun vraiChargement_sansProgression_bufferingResteVrai() {
        val s = session(); s.start("http://a/1", "1:a"); scope.runCurrent()
        emit(EngineEvent.FirstFrame)
        emit(EngineEvent.Buffering)
        scope.advanceTimeBy(5_000); scope.runCurrent()
        assertTrue(s.state.value.buffering)
    }
}

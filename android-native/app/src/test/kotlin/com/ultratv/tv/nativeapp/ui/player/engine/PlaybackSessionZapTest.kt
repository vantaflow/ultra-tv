package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Zapping : le moteur en place enchaîne le flux suivant au lieu d'être détruit puis recréé. */
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PlaybackSessionZapTest {

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig, private val canReuse: Boolean) : PlayerEngine {
        val loads = mutableListOf<String>()
        var released = false
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events: Flow<EngineEvent> = MutableSharedFlow()
        override val reusable: Boolean get() = canReuse
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
        override fun release() { released = true }
    }

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val buffer = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 192, lowRam = false)
    private fun settings(engine: EngineChoice = EngineChoice.EXO) = ResolvedPlayback(
        engine = engine, decoder = DecoderMode.AUTO, buffer = buffer, bufferPreset = BufferPreset.AUTO,
        maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false,
        manualEngine = false, manualDecoder = false, manualBuffer = false,
    )

    private fun session(isLive: Boolean, reusable: Boolean, created: MutableList<FakeEngine>, s: () -> ResolvedPlayback = { settings() }) =
        PlaybackSession(
            ctx, TestScope(UnconfinedTestDispatcher()), FrameLayout(ctx), s, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
            isLive = isLive, autoFrameRate = false, userAgent = "test",
            engineFactory = { k, c -> FakeEngine(k, c, reusable).also { created += it } },
        )

    @Test
    fun start_zapEnDirect_reutiliseLeMoteurEnPlace() {
        val created = mutableListOf<FakeEngine>()
        val s = session(isLive = true, reusable = true, created)
        s.start("http://a/1", "1:a")
        s.start("http://a/2", "1:b")
        s.start("http://a/3", "1:c")
        assertEquals(1, created.size)
        assertEquals(listOf("http://a/1", "http://a/2", "http://a/3"), created[0].loads)
        assertEquals(false, created[0].released)
    }

    @Test
    fun start_moteurNonReutilisable_recreeLeMoteur() {
        val created = mutableListOf<FakeEngine>()
        val s = session(isLive = true, reusable = false, created)
        s.start("http://a/1", "1:a")
        s.start("http://a/2", "1:b")
        assertEquals(2, created.size)
        assertEquals(true, created[0].released)
    }

    @Test
    fun start_videoAlaDemande_recreeLeMoteur() {
        val created = mutableListOf<FakeEngine>()
        val s = session(isLive = false, reusable = true, created)
        s.start("http://a/1", "1:a")
        s.start("http://a/2", "1:b")
        assertEquals(2, created.size)
    }

    @Test
    fun start_moteurChoisiDifferent_recreeLeMoteur() {
        val created = mutableListOf<FakeEngine>()
        var choice = EngineChoice.EXO
        val s = session(isLive = true, reusable = true, created) { settings(choice) }
        s.start("http://a/1", "1:a")
        choice = EngineChoice.VLC
        s.start("http://a/2", "1:b")
        assertEquals(2, created.size)
        assertEquals(EngineKind.VLC, created[1].kind)
    }
}

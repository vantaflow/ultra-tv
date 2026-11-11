package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import com.ultratv.tv.nativeapp.data.m3u.DrmScheme
import com.ultratv.tv.nativeapp.data.m3u.StreamProps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Flux protégés : Media3 seul, jamais de repli ni de choix VLC. */
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PlaybackProtectedTest {

    @Test fun planner_fluxProtege_toujoursExo_memeSiVlcChoisiOuMemorise() {
        val vlc = Combo(EngineKind.VLC, DecoderMode.AUTO)
        assertEquals(EngineKind.EXO, PlaybackPlanner.initial(EngineChoice.VLC, DecoderMode.AUTO, vlc, protectedStream = true).engine)
        assertEquals(EngineKind.EXO, PlaybackPlanner.initial(EngineChoice.AUTO, DecoderMode.AUTO, vlc, protectedStream = true).engine)
        assertEquals(EngineKind.VLC, PlaybackPlanner.initial(EngineChoice.VLC, DecoderMode.AUTO, null).engine)   // flux ordinaire inchangé
    }

    @Test fun planner_fluxProtege_aucunRepli() {
        for (c in EngineChoice.values()) assertNull(PlaybackPlanner.next(c, emptySet(), protectedStream = true))
        for (k in PlayErrorKind.values()) assertFalse(PlaybackPlanner.shouldFallBack(k, protectedStream = true))
        assertTrue(PlaybackPlanner.shouldFallBack(PlayErrorKind.FORMAT))                       // flux ordinaire inchangé
        assertEquals(Combo(EngineKind.EXO, DecoderMode.SOFTWARE), PlaybackPlanner.manualCombo(Combo(EngineKind.VLC, DecoderMode.SOFTWARE), true))
        assertFalse(PlaybackPlanner.shouldReconnectLive(PlayErrorKind.DRM_LICENSE))
    }

    private class Fake(override val kind: EngineKind, override val config: EngineConfig, val log: MutableList<String>) : PlayerEngine {
        override val view: View = View(ApplicationProvider.getApplicationContext())
        val bus = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8)
        override val events: Flow<EngineEvent> = bus
        override val supportsProtected get() = kind == EngineKind.EXO
        override fun load(url: String, startPositionMs: Long) { log += "${kind}:load" }
        override fun loadProtected(url: String, startPositionMs: Long, props: StreamProps) { log += "${kind}:protected" }
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
    private fun settings(e: EngineChoice) = ResolvedPlayback(
        engine = e, decoder = DecoderMode.AUTO, buffer = buffer, bufferPreset = BufferPreset.AUTO,
        maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false, manualEngine = false, manualDecoder = false, manualBuffer = false,
    )
    private val wv = StreamProps(DrmScheme.WIDEVINE, "https://lic.invalid/wv")

    private fun session(choice: EngineChoice, created: MutableList<Fake>, log: MutableList<String>, supported: Boolean = true) =
        PlaybackSession(
            ctx, TestScope(UnconfinedTestDispatcher()), FrameLayout(ctx), { settings(choice) }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
            isLive = true, autoFrameRate = false, userAgent = "test",
            propsLookup = { k -> if (k == "1:p") wv else null },
            drmPrecheck = { if (supported) null else PlayErrorKind.DRM_UNSUPPORTED },
            engineFactory = { k, c -> Fake(k, c, log).also { created += it } },
        )

    @Test fun session_vlcImpose_fluxProtege_ouvertSurExoAvecSaConfiguration() {
        val created = mutableListOf<Fake>(); val log = mutableListOf<String>()
        val s = session(EngineChoice.VLC, created, log)
        s.start("http://s.invalid/a.mpd", "1:p")
        assertEquals(listOf(EngineKind.EXO), created.map { it.kind })
        assertEquals(listOf("EXO:protected"), log)
        assertTrue(s.state.value.isProtected)
    }

    @Test fun session_erreur_aucunRepliVlc_ecranDErreur() {
        val created = mutableListOf<Fake>(); val log = mutableListOf<String>()
        val s = session(EngineChoice.AUTO, created, log)
        s.start("http://s.invalid/a.mpd", "1:p")
        created.single().bus.tryEmit(EngineEvent.Error(PlayErrorKind.FORMAT))
        assertEquals(1, created.size)
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertTrue(created.none { it.kind == EngineKind.VLC })
    }

    @Test fun session_choixManuelVlc_ignore() {
        val created = mutableListOf<Fake>(); val log = mutableListOf<String>()
        val s = session(EngineChoice.AUTO, created, log)
        s.start("http://s.invalid/a.mpd", "1:p")
        s.switchTo(Combo(EngineKind.VLC, DecoderMode.AUTO))
        assertTrue(created.none { it.kind == EngineKind.VLC })
        assertEquals(EngineKind.EXO, s.state.value.combo.engine)
    }

    @Test fun session_appareilSansLeSysteme_erreurClaire_aucunMoteur() {
        val created = mutableListOf<Fake>(); val log = mutableListOf<String>()
        val s = session(EngineChoice.AUTO, created, log, supported = false)
        s.start("http://s.invalid/a.mpd", "1:p")
        assertTrue(created.isEmpty())
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.DRM_UNSUPPORTED, s.state.value.error)
    }

    @Test fun session_fluxOrdinaire_gardeSonComportement() {
        val created = mutableListOf<Fake>(); val log = mutableListOf<String>()
        val s = session(EngineChoice.AUTO, created, log)
        s.start("http://s.invalid/a.ts", "1:o")
        assertEquals(listOf("EXO:load"), log)
        assertFalse(s.state.value.isProtected)
        created.single().bus.tryEmit(EngineEvent.Error(PlayErrorKind.FORMAT))
        assertEquals(EngineKind.EXO, s.state.value.combo.engine)   // repli vers le décodage logiciel (EXO), comme avant
    }
}

package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import com.ultratv.tv.nativeapp.data.m3u.StreamProps
import com.ultratv.tv.nativeapp.data.stalker.StreamOpen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plafond de durée à l'ouverture d'un flux (S2) : la demande de lien d'un portail Stalker (create_link, poignée de main)
 * n'avait AUCUN délai maximal ; « Chargement » pouvait durer 50 à 150 s, deux fois avec la nouvelle tentative.
 * Horloge de test (temps virtuel), aucun réseau.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PlaybackOpenTimeoutTest {

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig) : PlayerEngine {
        val loads = mutableListOf<String>()
        var released = false
        val bus = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events = bus
        override fun load(url: String, startPositionMs: Long) { loads += url }
        override fun loadProtected(url: String, startPositionMs: Long, props: StreamProps) { loads += url }
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
    private fun settings() = ResolvedPlayback(
        engine = EngineChoice.AUTO, decoder = DecoderMode.AUTO, buffer = buffer, bufferPreset = BufferPreset.AUTO,
        maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false,
        manualEngine = false, manualDecoder = false, manualBuffer = false,
    )
    private val scope = TestScope(StandardTestDispatcher())
    private val created = mutableListOf<FakeEngine>()
    /** Comportement de la demande de lien, appel par appel (le dernier se répète). */
    private val behaviours = ArrayDeque<suspend () -> StreamOpen>()
    private var opens = 0

    @Before fun visible() { com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = true }
    @After fun end() { com.ultratv.tv.nativeapp.ui.common.AppForeground.visible = false; scope.cancel() }

    private fun session(live: Boolean = true) = PlaybackSession(
        ctx, scope, FrameLayout(ctx), { settings() }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
        isLive = live, autoFrameRate = false, userAgent = "test",
        opener = { opens++; (if (behaviours.size > 1) behaviours.removeFirst() else behaviours.first())() },
        engineFactory = { k, c -> FakeEngine(k, c).also { created += it } },
        nowMs = { scope.testScheduler.currentTime },
    )

    private val never: suspend () -> StreamOpen = { awaitCancellation() }
    private fun after(ms: Long): suspend () -> StreamOpen = { delay(ms); StreamOpen("http://portal.invalid/real.ts") }
    private fun failsAfter(ms: Long): suspend () -> StreamOpen = { delay(ms); throw java.net.ConnectException("refused") }

    private fun emit(e: EngineEvent) { created.last().bus.tryEmit(e); scope.runCurrent() }
    private fun elapse(ms: Long) { scope.advanceTimeBy(ms); scope.runCurrent() }

    @Test fun direct_lienQuiNeRepondJamais_erreurA12s_uneSeuleFois() {
        behaviours += never
        val s = session(); s.start("stalker://p1/itv?cmd=x", "1:s"); scope.runCurrent()
        elapse(11_900)
        assertEquals("toujours en chargement avant 12 s", Phase.LOADING, s.state.value.phase)
        elapse(200)
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.START_TIMEOUT, s.state.value.error)
        assertNull(s.state.value.errorHttp)
        assertEquals("aucun moteur créé", 0, created.size)
        elapse(120_000)
        assertEquals("une seule demande, pas de seconde attente de 12 s", 1, opens)
        assertEquals(Phase.ERROR, s.state.value.phase)
    }

    @Test fun film_lienQuiNeRepondJamais_erreurA20s() {
        behaviours += never
        val s = session(live = false); s.start("stalker://p1/vod?cmd=y", "1:v"); scope.runCurrent()
        elapse(19_900)
        assertEquals(Phase.LOADING, s.state.value.phase)
        elapse(200)
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.START_TIMEOUT, s.state.value.error)
        elapse(120_000)
        assertEquals(1, opens)
    }

    @Test fun direct_lienA11s_puisImage_aucuneErreur() {
        behaviours += after(11_000)
        val s = session(); s.start("stalker://p1/itv?cmd=x", "1:s"); scope.runCurrent()
        elapse(11_000)
        assertEquals(1, created.size)
        emit(EngineEvent.Buffering)
        elapse(3_000)                         // image à 14 s depuis l'appui : la phase moteur garde son propre délai (15 s)
        emit(EngineEvent.FirstFrame)
        elapse(60_000)
        assertEquals(Phase.PLAYING, s.state.value.phase)
        assertEquals(1, opens)
    }

    @Test fun echecRapidePuisLienMuet_plafondGlobalDe12sDepuisLAppui() {
        behaviours += failsAfter(2_000); behaviours += never
        val s = session(); s.start("stalker://p1/itv?cmd=x", "1:s"); scope.runCurrent()
        elapse(2_000)                         // refus en 2 s → nouvelle tentative 1,5 s plus tard (règle existante)
        elapse(1_500)
        assertEquals(2, opens)
        elapse(8_400)                         // 11,9 s depuis l'appui
        assertEquals(Phase.LOADING, s.state.value.phase)
        elapse(200)                           // 12,1 s : le plafond est global, pas 12 s de plus
        assertEquals(Phase.ERROR, s.state.value.phase)
        assertEquals(PlayErrorKind.START_TIMEOUT, s.state.value.error)
    }

    @Test fun lectureDejaDemarree_jamaisCoupeeParLePlafond() {
        behaviours += after(500)
        val s = session(); s.start("stalker://p1/itv?cmd=x", "1:s"); scope.runCurrent()
        elapse(500)
        emit(EngineEvent.FirstFrame)
        elapse(10 * 60_000L)                  // dix minutes de lecture
        emit(EngineEvent.Buffering)           // remise en tampon après la première image
        elapse(11_000)
        emit(EngineEvent.Ready)
        elapse(60_000)
        assertEquals(Phase.PLAYING, s.state.value.phase)
        assertEquals(1, created.size)
        assertEquals(1, opens)
    }

    @Test fun reconnexionDuDirect_lienMuet_compteCommeUnEssai_sansEcranAvantLaFinDesReconnexions() {
        behaviours += after(500); behaviours += never; behaviours += after(500)
        val s = session(); s.start("stalker://p1/itv?cmd=x", "1:s"); scope.runCurrent()
        elapse(500)
        emit(EngineEvent.FirstFrame)
        elapse(40_000)
        emit(EngineEvent.Error(PlayErrorKind.NETWORK, null))   // coupure en pleine lecture → reconnexion
        elapse(500)                            // 1er essai : lien muet
        assertEquals(2, opens)
        elapse(12_100)                         // plafond par appel de reconnexion → essai suivant, pas d'écran d'erreur
        assertTrue(s.state.value.phase != Phase.ERROR)
        elapse(2_000)
        assertEquals(3, opens)
        emit(EngineEvent.FirstFrame)
        assertEquals(Phase.PLAYING, s.state.value.phase)
    }

    @Test fun zapPendantLaDemande_annuleLAttente_sansErreurTardive() {
        behaviours += never; behaviours += after(300)
        val s = session(); s.start("stalker://p1/itv?cmd=a", "1:a"); scope.runCurrent()
        elapse(5_000)
        s.start("stalker://p1/itv?cmd=b", "1:b"); scope.runCurrent()
        elapse(300)
        emit(EngineEvent.FirstFrame)
        elapse(30_000)
        assertEquals(Phase.PLAYING, s.state.value.phase)
        assertEquals(2, opens)
    }

    @Test fun adresseOrdinaire_aucunEffet() {
        val s = session(); s.start("http://a/live.m3u8", "1:h"); scope.runCurrent()
        assertEquals(1, created.size)
        assertEquals(0, opens)
    }
}

package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * Vidéo de l'aperçu rendue vers la surface de l'hôte À L'ÉCRAN (anomalie box 1.3.73 : son seul ~30 s à l'entrée du Direct
 * et du Guide, image arrivée seulement au changement de surface suivant).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SurfaceHostTest {

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig) : PlayerEngine {
        val loads = mutableListOf<String>()
        var released = false
        var refreshes = 0
        val flow = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events: Flow<EngineEvent> = flow
        override val reusable: Boolean get() = true
        override fun load(url: String, startPositionMs: Long) { loads += url }
        override fun play() {}
        override fun pause() {}
        override fun seekTo(ms: Long) {}
        /** Le son avance (lecteur prêt et en lecture). */
        var playing = true
        override val isPlaying get() = playing
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
        /** Surface de la vue vidéo à l'écran dans l'hôte courant. */
        var surface = false
        override val surfaceReady: Boolean get() = surface
        override fun refreshVideoSurface() { refreshes++ }
        override fun release() { released = true }
    }

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val buffer = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 192, lowRam = false)
    private val settings = ResolvedPlayback(
        engine = EngineChoice.EXO, decoder = DecoderMode.AUTO, buffer = buffer, bufferPreset = BufferPreset.AUTO,
        maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false,
        manualEngine = false, manualDecoder = false, manualBuffer = false,
    )
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val engines = mutableListOf<FakeEngine>()
    private val shared = SharedLiveSession(scope, orphanTimeoutMs = 2_000)
    private val create: () -> PlaybackSession = {
        PlaybackSession(
            ctx, scope, FrameLayout(ctx), { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
            isLive = true, autoFrameRate = false, userAgent = "test",
            engineFactory = { k, c -> FakeEngine(k, c).also { engines += it } },
        )
    }
    private fun engine() = engines.last()
    private fun logs() = ShadowLog.getLogsForTag("UltraPlay").map { it.msg }

    /** La vue vidéo placée dans [host] (ce que fait l'AndroidView de l'écran). */
    private fun hostIn(session: PlaybackSession, name: String, host: FrameLayout) { host.addView(session.takeVideoView(name)) }

    @Test
    fun hoteA_puisHoteB_aLEntreeDuDirect_laVueQuitteAEtLaSurfaceDeBEstSeuleRetenue() {
        // Hôte A (accueil / plein écran) : flux en cours, surface de A valide.
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        val session = shared.session!!
        val hostA = FrameLayout(ctx); val hostB = FrameLayout(ctx)
        hostIn(session, "home", hostA)
        engine().surface = true
        // Entrée dans le Direct : l'encadré B reprend la vue AVANT que sa propre surface n'existe.
        engine().surface = false
        hostIn(session, "direct", hostB)
        assertSame(hostB, session.container.parent)
        assertEquals(0, hostA.childCount)                       // la vue a explicitement quitté A
        assertEquals("direct", session.hostName)
        assertTrue(logs().any { it == "surface host=direct take previous=home" })
        assertTrue(logs().any { it == "surface detach host=home (view moved)" })
        // Pas de surface dans B : rien n'est rebranché (aucune image ne peut sortir, ce n'est pas une panne).
        scope.advanceTimeBy(5_000)
        assertEquals(0, engine().refreshes)
        // Surface de B créée et affichée ; le son avance, aucune image pendant 2 s → rebranchement sur la surface de B.
        engine().surface = true
        scope.advanceTimeBy(SURFACE_REATTACH_MS + SURFACE_POLL_MS + 1)
        assertEquals(1, engine().refreshes)
        assertTrue(logs().any { it.startsWith("surface reattach host=direct reason=no-picture") })
        // Une seule fois par hôte : pas de rebranchements en boucle.
        scope.advanceTimeBy(5_000)
        assertEquals(1, engines.size)
        assertEquals(1, engine().refreshes)
        assertEquals(listOf("http://a"), engine().loads)       // jamais de rechargement du flux
    }

    @Test
    fun changementDHotePendantLAttente_leDelaiRepartDeLaSurfaceDuNouvelHote() {
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        val session = shared.session!!
        hostIn(session, "home", FrameLayout(ctx))
        engine().surface = true
        scope.advanceTimeBy(1_500)
        hostIn(session, "direct", FrameLayout(ctx))
        scope.advanceTimeBy(1_000)
        assertEquals(0, engine().refreshes)                     // 2,5 s au total, mais 1 s seulement dans le Direct
        scope.advanceTimeBy(SURFACE_REATTACH_MS + SURFACE_POLL_MS)
        assertEquals(1, engine().refreshes)
        assertTrue(logs().any { it.startsWith("surface reattach host=direct") })
    }

    @Test
    fun imageArriveeAvantLeDelai_aucunRebranchement() {
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        hostIn(shared.session!!, "direct", FrameLayout(ctx))
        engine().surface = true
        scope.advanceTimeBy(800)
        engine().flow.tryEmit(EngineEvent.FirstFrame)
        scope.advanceTimeBy(10_000)
        assertEquals(0, engine().refreshes)
        assertTrue(shared.isReady("1:a"))
    }

    @Test
    fun sonQuiNAvancePas_aucunRebranchement() {
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        hostIn(shared.session!!, "direct", FrameLayout(ctx))
        engine().playing = false                                 // chargement : rien n'est rendu, ni son ni image
        engine().surface = true
        scope.advanceTimeBy(5_000)
        assertEquals(0, engine().refreshes)
    }

    @Test
    fun entreeDuGuide_hotePrecedentEncoreValide_rebranchementSurLEncadreDuGuide() {
        shared.gateAudioOnPicture = true                         // aperçu du Guide
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        val session = shared.session!!
        hostIn(session, "player", FrameLayout(ctx))             // hôte précédent : surface encore valide
        engine().surface = true
        hostIn(session, "guide", FrameLayout(ctx))               // encadré du Guide, première chaîne
        scope.advanceTimeBy(SURFACE_REATTACH_MS + SURFACE_POLL_MS + 1)
        assertEquals(1, engine().refreshes)
        assertTrue(logs().any { it.startsWith("surface reattach host=guide") })
        assertEquals(listOf("http://a"), engine().loads)
    }

    @Test
    fun zapDansLApercu_nouveauFluxSansImage_rebranchementAussi() {
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        hostIn(shared.session!!, "direct", FrameLayout(ctx))
        engine().surface = true
        engine().flow.tryEmit(EngineEvent.FirstFrame)
        shared.preview("1:b", "1:b", "http://b", muted = false, create)   // moteur réutilisé
        scope.advanceTimeBy(SURFACE_REATTACH_MS + SURFACE_POLL_MS + 1)
        assertEquals(1, engines.size)
        assertEquals(1, engine().refreshes)
    }

    // ── Sortie du décodeur avant la première vraie surface ──

    @Test
    fun decodeur_surfaceFactice_refuseeAvantLaPremiereVraieSurface_puisAdmisePourLesChangementsDHote() {
        val t = VideoOutputPolicy.Tracker()
        assertTrue(t.refusePlaceholder)                          // moteur lancé avant l'encadré : le décodeur attend
        t.onOutput(false)
        assertTrue(t.refusePlaceholder)                          // toujours aucune vraie surface
        t.onOutput(true)                                         // surface de l'encadré : décodeur configuré dessus
        assertFalse(t.refusePlaceholder)
        t.onOutput(false)                                        // aperçu → plein écran : surface factice permise (pas de recréation)
        assertFalse(t.refusePlaceholder)
    }

    @Test
    fun filetDeSecurite_decisionPure() {
        assertTrue(SurfaceWatch.shouldReattach(sameHost = true, surfaceShown = true, hasVideo = true, playing = true, firstFrame = false))
        assertFalse(SurfaceWatch.shouldReattach(false, true, true, true, false))   // hôte changé
        assertFalse(SurfaceWatch.shouldReattach(true, false, true, true, false))   // surface pas à l'écran
        assertFalse(SurfaceWatch.shouldReattach(true, true, false, true, false))   // radio
        assertFalse(SurfaceWatch.shouldReattach(true, true, true, false, false))   // son qui n'avance pas
        assertFalse(SurfaceWatch.shouldReattach(true, true, true, true, true))     // image déjà là
    }

    @Test
    fun surfaceReady_exigeUneVueAttacheeVisibleEtDimensionnee() {
        assertFalse(FrameProbe.shown(attached = false, visible = true, width = 1280, height = 720))   // hôte retiré
        assertFalse(FrameProbe.shown(attached = true, visible = false, width = 1280, height = 720))   // hôte caché
        assertFalse(FrameProbe.shown(attached = true, visible = true, width = 0, height = 0))         // pas encore mis en page
        assertTrue(FrameProbe.shown(attached = true, visible = true, width = 1280, height = 720))
        // Vue vidéo jamais attachée à une fenêtre (encadré retiré) : surface jamais jugée prête.
        val sv = android.view.SurfaceView(ctx)
        assertFalse(FrameProbe.surfaceReady(FrameLayout(ctx).apply { addView(sv) }))
    }
}

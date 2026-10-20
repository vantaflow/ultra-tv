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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Session du Direct partagée entre l'aperçu et le plein écran : aperçu → plein écran → retour sans rechargement,
 * changement de focus pendant le chargement, une seule connexion ouverte à la fois.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SharedLiveSessionTest {

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig, val all: MutableList<FakeEngine>) : PlayerEngine {
        val loads = mutableListOf<String>()
        var released = false
        var muted: Boolean? = null
        val flow = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events: Flow<EngineEvent> = flow
        override val reusable: Boolean get() = true
        override fun load(url: String, startPositionMs: Long) {
            // Invariant « connexion unique » : jamais deux moteurs vivants qui tiennent un flux.
            assertTrue("deux flux ouverts en même temps", all.none { it !== this && !it.released && it.loads.isNotEmpty() })
            loads += url
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
        override fun setMuted(muted: Boolean) { this.muted = muted }
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
    private var sessionsCreated = 0
    private val shared = SharedLiveSession(scope, orphanTimeoutMs = 2_000)
    private val create: () -> PlaybackSession = {
        sessionsCreated++
        PlaybackSession(
            ctx, scope, FrameLayout(ctx), { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
            isLive = true, autoFrameRate = false, userAgent = "test",
            engineFactory = { k, c -> FakeEngine(k, c, engines).also { engines += it } },
        )
    }
    private val loads get() = engines.flatMap { it.loads }
    private fun live() = engines.filter { !it.released }
    private fun firstFrame() { engines.last().flow.tryEmit(EngineEvent.FirstFrame) }

    @Test
    fun apercu_memeLigne_neRechargePas() {
        assertTrue(shared.preview("1:a", "1:a", "http://a", muted = true, create))
        assertFalse(shared.preview("1:a", "1:a", "http://a", muted = true, create))
        assertEquals(listOf("http://a"), loads)
        assertEquals(LiveOwner.PREVIEW, shared.owner)
        assertEquals(true, engines.single().muted)
    }

    @Test
    fun focusQuiChangePendantLeChargement_unSeulMoteurUnSeulFlux() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        shared.preview("1:b", "1:b", "http://b", true, create)       // A encore en chargement
        shared.preview("1:c", "1:c", "http://c", true, create)
        assertEquals(1, sessionsCreated)
        assertEquals(1, engines.size)                                // le moteur en place enchaîne les flux
        assertEquals(listOf("http://a", "http://b", "http://c"), loads)
        assertEquals("1:c", shared.channelKey.value)
        assertFalse(shared.isReady("1:a"))
    }

    @Test
    fun okSurApercuPret_pleinEcranSansRechargement_puisRetourSansRechargement() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        assertTrue(shared.isReady("1:a"))
        val session = shared.session

        assertTrue(shared.openFullscreen("1:a", create))
        val adoption = shared.adopt("1:a")
        assertNotNull(adoption)
        assertTrue(adoption!!.playing)
        assertSame(session, adoption.session)
        assertEquals(false, engines.single().muted)                  // le son revient en plein écran
        assertEquals(listOf("http://a"), loads)                     // aucun stop / load
        assertFalse(engines.single().released)

        shared.demote()
        assertEquals(LiveOwner.PREVIEW, shared.owner)
        assertEquals(true, engines.single().muted)                   // aperçu muet de nouveau
        assertEquals(listOf("http://a"), loads)
        assertSame(session, shared.session)
        assertEquals(1, sessionsCreated)
    }

    @Test
    fun okSurApercuEnChargement_fermeLeFluxAvantLeLecteur() {
        shared.preview("1:a", "1:a", "http://a", true, create)      // pas encore d'image
        assertFalse(shared.openFullscreen("1:a", create))
        assertTrue(engines.single().released)                        // connexion libérée tout de suite
        val adoption = shared.adopt("1:a")!!
        assertFalse(adoption.playing)
        adoption.session.start("http://a", "1:a")                    // chemin normal du lecteur
        assertEquals(1, live().size)
        assertEquals(1, sessionsCreated)
        assertEquals(false, live().single().muted ?: false)
    }

    @Test
    fun okSurAutreLigne_cheminNormal() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        assertFalse(shared.openFullscreen("1:b", create))
        assertTrue(engines.single().released)
        assertFalse(shared.adopt("1:b")!!.playing)
    }

    @Test
    fun varianteDifferenteALAdoption_repartProprement() {
        shared.preview("1:a", "1:a-hd", "http://a-hd", true, create)
        firstFrame()
        assertTrue(shared.openFullscreen("1:a", create))
        val adoption = shared.adopt("1:a-4k")!!                     // le lecteur lit une autre variante
        assertFalse(adoption.playing)
        assertTrue(engines.single().released)
    }

    @Test
    fun zapEnPleinEcran_lApercuSuitLaChaineAuRetour() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        shared.openFullscreen("1:a", create)
        val s = shared.adopt("1:a")!!.session
        s.start("http://b", "1:b")                                   // zap ▲▼ dans le lecteur
        shared.onFullscreenChannel("1:b")
        shared.demote()
        assertEquals("1:b", shared.channelKey.value)
        assertEquals("1:b", shared.rowKey)
        assertEquals(listOf("http://a", "http://b"), loads)
        assertEquals(1, live().size)
    }

    @Test
    fun sortieDuPleinEcranSansRetour_libereTout() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        shared.openFullscreen("1:a", create)
        shared.adopt("1:a")
        shared.onFullscreenGone()                                    // Accueil, veille, erreur…
        assertEquals(LiveOwner.NONE, shared.owner)
        assertNull(shared.session)
        assertTrue(live().isEmpty())
    }

    @Test
    fun retourSansApercuAffiche_libereApresLeDelai() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        shared.openFullscreen("1:a", create)
        shared.adopt("1:a")
        shared.demote()
        scope.advanceTimeBy(2_100)
        assertEquals(LiveOwner.NONE, shared.owner)
        assertTrue(live().isEmpty())
    }

    @Test
    fun retourAvecApercuAffiche_resteOuvert() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        shared.openFullscreen("1:a", create)
        shared.adopt("1:a")
        shared.demote()
        shared.hostAttached()
        scope.advanceTimeBy(2_100)
        assertEquals(LiveOwner.PREVIEW, shared.owner)
        assertEquals(1, live().size)
    }

    @Test
    fun lecteurOuvertDAilleurs_fermeLApercu() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        assertNull(shared.adopt("2:x"))
        assertEquals(LiveOwner.NONE, shared.owner)
        assertTrue(live().isEmpty())
    }

    @Test
    fun ecranQuitte_stopPreview_neFermePasLePleinEcran() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        shared.openFullscreen("1:a", create)
        shared.stopPreview()                                         // onDispose du Direct pendant la navigation
        assertEquals(LiveOwner.FULLSCREEN, shared.owner)
        assertEquals(1, live().size)
        shared.adopt("1:a")
        shared.demote()
        shared.stopPreview()                                         // Direct quitté / arrière-plan
        assertTrue(live().isEmpty())
    }

    @Test
    fun apercuPendantLePleinEcran_ignore() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        shared.openFullscreen("1:a", create)
        assertFalse(shared.preview("1:b", "1:b", "http://b", true, create))
        assertEquals(listOf("http://a"), loads)
    }

    @Test
    fun sonDeLApercu_appliqueTouteSuite() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        shared.applyPreviewMuted(false)
        assertEquals(false, engines.single().muted)
    }

    @Test
    fun okSansOuvertureDuLecteur_libereApresLeDelai() {
        shared.preview("1:a", "1:a", "http://a", true, create)
        firstFrame()
        shared.openFullscreen("1:a", create)                        // résolution en échec : jamais d'adopt
        scope.advanceTimeBy(2_500)
        assertEquals(LiveOwner.FULLSCREEN, shared.owner)              // pas le délai « orphelin » de l'aperçu
        scope.advanceTimeBy(8_000)
        assertEquals(LiveOwner.NONE, shared.owner)
        assertTrue(live().isEmpty())
    }
}

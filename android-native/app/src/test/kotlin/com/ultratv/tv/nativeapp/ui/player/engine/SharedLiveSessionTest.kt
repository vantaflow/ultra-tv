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
        var video = true
        override val hasVideo get() = video
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
    /** Lecture en base du flux lente (propsLookup) : le démarrage est asynchrone, comme dans l'appli. */
    private var lookupGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private val createSlow: () -> PlaybackSession = {
        sessionsCreated++
        PlaybackSession(
            ctx, scope, FrameLayout(ctx), { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
            isLive = true, autoFrameRate = false, userAgent = "test",
            propsLookup = { lookupGate?.await(); null },
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

    // ── Aperçu du Guide : jamais de son sans image ──────────────────────────────────────────────

    @Test
    fun guide_sonCoupeTantQueLImageNEstPasAffichee_memeAvecLeSonActive() {
        shared.gateAudioOnPicture = true
        shared.preview("1:a", "1:a", "http://a", muted = false, create)    // réglage « Son de l'aperçu » activé
        assertEquals("audio avant la première image", true, engines.single().muted)
        firstFrame()
        assertEquals("image rendue mais pas encore affichée dans l'encadré", true, engines.single().muted)
        shared.setPictureShown(true)
        assertEquals(false, engines.single().muted)
        shared.setPictureShown(false)                                       // l'encadré ne montre plus la vidéo (focus ailleurs)
        assertEquals(true, engines.single().muted)
    }

    @Test
    fun guide_sonDesactive_resteMuetMemeImageAffichee() {
        shared.gateAudioOnPicture = true
        shared.preview("1:a", "1:a", "http://a", muted = true, create)
        firstFrame(); shared.setPictureShown(true)
        assertEquals(true, engines.single().muted)
        shared.applyPreviewMuted(false)
        assertEquals(false, engines.single().muted)
        shared.applyPreviewMuted(true)
        assertEquals(true, engines.single().muted)
    }

    @Test
    fun guide_autreChaine_remetLeSonAZeroJusquALaNouvelleImage() {
        shared.gateAudioOnPicture = true
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        firstFrame(); shared.setPictureShown(true)
        assertEquals(false, engines.single().muted)
        shared.preview("1:b", "1:b", "http://b", muted = false, create)     // le moteur en place enchaîne le flux suivant
        assertEquals("ancienne image affichée : le nouveau flux ne doit pas s'entendre", true, engines.single().muted)
        firstFrame(); shared.setPictureShown(true)
        assertEquals(false, engines.single().muted)
    }

    // Garde-fou de la session (le Guide ferme son aperçu avant d'ouvrir le lecteur : ce passage n'est pas emprunté depuis lui).
    @Test
    fun guide_demoteAvecPorteActive_muetJusquAuRetourDeLImage() {
        shared.gateAudioOnPicture = true
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        firstFrame(); shared.setPictureShown(true)
        shared.openFullscreen("1:a", create); shared.adopt("1:a")
        assertEquals(false, engines.single().muted)                          // plein écran : le son revient toujours
        shared.setPictureShown(false)                                        // l'hôte de l'aperçu a quitté l'écran
        assertEquals("le plein écran garde son son", false, engines.single().muted)
        shared.demote()
        assertEquals(true, engines.single().muted)
        shared.setPictureShown(true)
        assertEquals(false, engines.single().muted)
    }

    @Test
    fun direct_sansPorte_sonInchange() {
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        shared.setPictureShown(false)
        assertNull("aucune coupure hors du Guide", engines.single().muted)
    }

    @Test
    fun guide_ouverture_apercuDemarreImmediatementEtMuet_sonAvecLaPremiereImage() {
        shared.gateAudioOnPicture = true
        assertTrue(shared.preview("1:a", "1:a", "http://a", muted = false, create))
        assertEquals("flux lancé dès l'ouverture", listOf("http://a"), loads)
        assertEquals("pas de son avant l'image", true, engines.single().muted)
        firstFrame()
        shared.setPictureShown(shared.session!!.state.value.showsMedia)
        assertEquals("son et image ensemble", false, engines.single().muted)
    }

    @Test
    fun guide_apercuMasque_stopPreviewLiberelFlux_etRienNeDemarre() {
        shared.gateAudioOnPicture = true
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        firstFrame()
        shared.stopPreview()
        assertEquals(LiveOwner.NONE, shared.owner)
        assertTrue("aucun moteur vivant", live().isEmpty())
        assertEquals(listOf("http://a"), loads)
    }

    // ── Relecture b271b601, I5.1 : démarrage asynchrone ──

    @Test
    fun guide_lectureEnBaseLente_l_ancienneImageNeSertPasLaNouvelleChaine() {
        shared.gateAudioOnPicture = true
        shared.preview("1:a", "1:a", "http://a", muted = false, createSlow)
        firstFrame()
        val s = shared.session!!
        assertTrue("A affichée", s.state.value.showsMedia)
        shared.setPictureShown(true)
        assertEquals(false, engines.single().muted)

        lookupGate = kotlinx.coroutines.CompletableDeferred()          // la lecture en base de B traîne
        shared.preview("1:b", "1:b", "http://b", muted = false, createSlow)
        // Ce que l'écran lit à cet instant : aucune image, donc son coupé (rien de A ne passe sous le titre de B).
        assertFalse("l'état de A ne doit plus passer pour une image affichée", s.state.value.showsMedia)
        assertEquals(Phase.LOADING, s.state.value.phase)
        shared.setPictureShown(s.state.value.showsMedia)
        assertEquals(true, engines.single().muted)

        lookupGate!!.complete(Unit)
        assertEquals(listOf("http://a", "http://b"), loads)
        firstFrame()
        assertTrue(s.state.value.showsMedia)
    }

    // ── I5.2 : flux radio (aucune piste vidéo) ──

    @Test
    fun guide_fluxRadio_sonRetabliQuandLeMoteurEstPret_sansImage() {
        shared.gateAudioOnPicture = true
        shared.preview("1:r", "1:r", "http://radio", muted = false, create)
        val e = engines.single(); e.video = false
        assertFalse(shared.session!!.state.value.showsMedia)
        shared.setPictureShown(shared.session!!.state.value.showsMedia)
        assertEquals("pas de son avant que le moteur soit prêt", true, e.muted)
        e.flow.tryEmit(EngineEvent.Ready)
        assertFalse("audio seul pas confirmé tout de suite (VLC : la piste vidéo peut arriver après Ready)", shared.session!!.state.value.audioOnly)
        scope.advanceTimeBy(AUDIO_ONLY_CONFIRM_MS - 100)
        assertFalse("pas encore", shared.session!!.state.value.audioOnly)
        scope.advanceTimeBy(200)
        assertTrue("audio seul prêt après le délai", shared.session!!.state.value.audioOnly)
        shared.setPictureShown(shared.session!!.state.value.showsMedia)
        assertEquals(false, e.muted)
    }

    @Test
    fun guide_fluxAvecVideo_sonSeulementALaPremiereImage_memeApresReady() {
        shared.gateAudioOnPicture = true
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        val e = engines.single()
        e.flow.tryEmit(EngineEvent.Ready)
        assertFalse("flux avec vidéo : Ready n'est pas une image", shared.session!!.state.value.showsMedia)
        shared.setPictureShown(shared.session!!.state.value.showsMedia)
        assertEquals(true, e.muted)
        firstFrame()
        shared.setPictureShown(shared.session!!.state.value.showsMedia)
        assertEquals(false, e.muted)
    }

    @Test
    fun guide_radioPuisChaineVideo_audioSeulRetombe() {
        shared.gateAudioOnPicture = true
        shared.preview("1:r", "1:r", "http://radio", muted = false, create)
        engines.single().video = false
        engines.single().flow.tryEmit(EngineEvent.Ready)
        scope.advanceTimeBy(AUDIO_ONLY_CONFIRM_MS + 100)
        assertTrue(shared.session!!.state.value.audioOnly)
        shared.preview("1:a", "1:a", "http://a", muted = false, create)
        assertFalse("la chaîne suivante repart sans marque audio seul", shared.session!!.state.value.audioOnly)
    }

    @Test
    fun guide_pisteVideoQuiArriveApresReady_jamaisDeSonAvantLImage() {
        shared.gateAudioOnPicture = true
        shared.preview("1:h", "1:h", "http://hls", muted = false, create)
        val e = engines.single(); e.video = false          // VLC : Playing avant l'ajout de la piste vidéo
        e.flow.tryEmit(EngineEvent.Ready)
        scope.advanceTimeBy(500)
        e.video = true                                      // la piste arrive
        scope.advanceTimeBy(AUDIO_ONLY_CONFIRM_MS * 3)
        val st = shared.session!!.state.value
        assertFalse("une piste vidéo est arrivée : pas audio seul", st.audioOnly)
        assertFalse(st.showsMedia)
        shared.setPictureShown(st.showsMedia)
        assertEquals("le son attend l'image", true, e.muted)
        firstFrame()
        shared.setPictureShown(shared.session!!.state.value.showsMedia)
        assertEquals(false, e.muted)
    }

    @Test
    fun guide_imageQuiArriveAvantLeDelai_pasAudioSeul() {
        shared.preview("1:h", "1:h", "http://hls", muted = false, create)
        val e = engines.single(); e.video = false
        e.flow.tryEmit(EngineEvent.Ready)
        scope.advanceTimeBy(300)
        firstFrame()
        scope.advanceTimeBy(AUDIO_ONLY_CONFIRM_MS * 2)
        assertFalse(shared.session!!.state.value.audioOnly)
    }

    @Test
    fun guide_changementDeFluxPendantLeDelai_rienNeFuitDeLAncienFlux() {
        shared.gateAudioOnPicture = true
        shared.preview("1:r", "1:r", "http://radio", muted = false, create)
        val e = engines.single(); e.video = false
        e.flow.tryEmit(EngineEvent.Ready)
        scope.advanceTimeBy(500)
        shared.preview("1:a", "1:a", "http://a", muted = false, create)   // même moteur, flux suivant, aucun Ready encore
        scope.advanceTimeBy(AUDIO_ONLY_CONFIRM_MS * 3)
        assertFalse("la confirmation de l'ancien flux ne touche pas le nouveau", shared.session!!.state.value.audioOnly)
        assertFalse(shared.session!!.state.value.showsMedia)
    }

    @Test
    fun guide_liberationPendantLeDelai_aucuneConfirmationOrpheline() {
        shared.preview("1:r", "1:r", "http://radio", muted = false, create)
        val s = shared.session!!
        val e = engines.single(); e.video = false
        e.flow.tryEmit(EngineEvent.Ready)
        scope.advanceTimeBy(500)
        s.release()
        scope.advanceTimeBy(AUDIO_ONLY_CONFIRM_MS * 3)
        assertFalse(s.state.value.audioOnly)
    }
}

package com.ultratv.tv.nativeapp.ui.live

import com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.Action
import com.ultratv.tv.nativeapp.ui.player.engine.LiveOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

class LivePreviewPolicyTest {

    @Test
    fun active_autoDesactiveSurBoxFaibleMemoireEtTactile() {
        assertTrue(LivePreviewPolicy.enabled("auto", lowRam = false, touch = false))
        assertFalse(LivePreviewPolicy.enabled("auto", lowRam = true, touch = false))
        assertTrue(LivePreviewPolicy.enabled("on", lowRam = true, touch = false))
        assertFalse(LivePreviewPolicy.enabled("off", lowRam = false, touch = false))
        assertFalse(LivePreviewPolicy.enabled("on", lowRam = false, touch = true))
    }

    @Test
    fun delai_borne() {
        assertEquals(400L, LivePreviewPolicy.delayMs(LivePreviewPolicy.DEFAULT_DELAY_MS))
        // Chaque choix proposé dans les réglages est dans les bornes.
        LivePreviewPolicy.DELAY_CHOICES.forEach { assertEquals(it.toLong(), LivePreviewPolicy.delayMs(it)) }
        assertEquals(300L, LivePreviewPolicy.delayMs(0))
        assertEquals(3_000L, LivePreviewPolicy.delayMs(60_000))
    }

    @Test
    fun decision() {
        // Rien ne joue : on lance.
        assertEquals(Action.START, LivePreviewPolicy.decide(LiveOwner.NONE, null, "1:a", locked = false, holdAfterReturn = false))
        // La ligne joue déjà : aucun rechargement.
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:a", "1:a", false, false))
        // Autre ligne : on enchaîne.
        assertEquals(Action.START, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:a", "1:b", false, false))
        // Retour du plein écran, focus restauré sur l'ancienne ligne : la chaîne du dernier zap reste.
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:z", "1:a", false, holdAfterReturn = true))
        // Plein écran en cours : l'aperçu ne touche à rien.
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.FULLSCREEN, "1:a", "1:b", false, false))
        // Chaîne verrouillée : jamais d'aperçu (code parental), l'aperçu en cours s'arrête.
        assertEquals(Action.STOP, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:a", "1:b", locked = true, holdAfterReturn = false))
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.NONE, null, "1:b", locked = true, holdAfterReturn = false))
    }

    @Test fun guide_attendUneVraiePause_avantLApercu() {
        assertEquals(1_500L, LivePreviewPolicy.guideDelayMs(LivePreviewPolicy.DEFAULT_DELAY_MS))
        assertEquals(1_500L, LivePreviewPolicy.guideDelayMs(0))
        val max = LivePreviewPolicy.DELAY_CHOICES.max()
        assertEquals(maxOf(max.toLong(), 1_500L), LivePreviewPolicy.guideDelayMs(max))
    }

    // ── Aperçu du Guide : départ immédiat à l'ouverture ──

    private fun timed(vararg steps: Pair<Long, String?>): kotlinx.coroutines.flow.Flow<String?> = kotlinx.coroutines.flow.flow {
        var t = 0L
        for ((at, v) in steps) { kotlinx.coroutines.delay(at - t); t = at; emit(v) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun collectTimed(flow: kotlinx.coroutines.flow.Flow<String>): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        kotlinx.coroutines.test.runTest { flow.collect { out += testScheduler.currentTime to it } }
        return out
    }

    @Test
    fun guide_premiereChaineDemarreSansDelai_lesSuivantesAttendentLaPause() {
        with(LivePreviewPolicy) {
            val got = collectTimed(timed(0L to null, 0L to "A", 100L to "B", 200L to "C", 5_000L to "D").immediateThenDebounced(1_500))
            // A tout de suite ; B abandonné par C ; C après 1,5 s de pause ; D après 1,5 s.
            assertEquals(listOf(0L to "A", 1_700L to "C", 6_500L to "D"), got)
        }
    }

    @Test
    fun guide_selectionQuiArriveTard_partImmediatement_etNeRepartPasSurLaMemeChaine() {
        with(LivePreviewPolicy) {
            val got = collectTimed(timed(0L to null, 300L to "A", 400L to "A").immediateThenDebounced(1_500))
            assertEquals(listOf(300L to "A"), got)
        }
    }

    @Test
    fun guide_apercuMasque_arreteLApercuEnCours_seulement() {
        assertTrue(LivePreviewPolicy.stopWhenDisabled(enabled = false, owner = LiveOwner.PREVIEW))
        assertFalse("jamais le plein écran", LivePreviewPolicy.stopWhenDisabled(false, LiveOwner.FULLSCREEN))
        assertFalse(LivePreviewPolicy.stopWhenDisabled(false, LiveOwner.NONE))
        assertFalse(LivePreviewPolicy.stopWhenDisabled(true, LiveOwner.PREVIEW))
    }

    // ── Retour du lecteur vers le Guide : la chaîne 1 ne joue pas avant la bonne ──

    @Test
    fun guide_ouvertureNeuve_lAperçuVisePremiereChaine_tantQuAucuneSelection() {
        assertEquals("1", LivePreviewPolicy.guideTarget(selected = null, first = "1", returningFromPlayer = false))
        assertEquals("37", LivePreviewPolicy.guideTarget(selected = "37", first = "1", returningFromPlayer = false))
    }

    @Test
    fun guide_retourDuLecteur_neVisePasLaPremiereChaine_attendLaSelectionRestauree() {
        assertEquals(null, LivePreviewPolicy.guideTarget(selected = null, first = "1", returningFromPlayer = true))
        assertEquals("37", LivePreviewPolicy.guideTarget(selected = "37", first = "1", returningFromPlayer = true))
    }

    @Test
    fun guide_retourDuLecteur_seulLePleinEcranEncoreProprietaireLeSignale() {
        assertTrue(LivePreviewPolicy.returningFromPlayer(LiveOwner.FULLSCREEN))
        assertFalse("aperçu d'un autre écran (Direct) : ouverture neuve", LivePreviewPolicy.returningFromPlayer(LiveOwner.PREVIEW))
        assertFalse("rien ne joue : ouverture neuve", LivePreviewPolicy.returningFromPlayer(LiveOwner.NONE))
    }

    // ── Verrou parental : jamais de démarrage sur une liste de verrous inconnue ──

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun guide_chaineVerrouillee_neDemarreJamais_memeSiLesVerrousArriventApresLaPremierePage() = kotlinx.coroutines.test.runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Set<String>>()
        val result = async { LivePreviewPolicy.decideChecked(LiveOwner.NONE, null, "1:7") { gate.await() } }
        testScheduler.runCurrent()
        assertFalse("pas de décision (donc pas de flux) tant que les verrous sont inconnus", result.isCompleted)
        gate.complete(setOf("1:7"))
        assertEquals(Action.KEEP, result.await())   // rien ne joue : rien à arrêter, et surtout aucun START
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun guide_chaineLibre_demarreUneFoisLesVerrousConnus() = kotlinx.coroutines.test.runTest {
        assertEquals(Action.START, LivePreviewPolicy.decideChecked(LiveOwner.NONE, null, "1:8") { setOf("1:7") })
    }
}

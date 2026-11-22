package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.ui.design.Text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `DetailGuard` : la garde commune des fiches du mode simple (film et série). Verrouillé / masqué : le contenu (affiche,
 * synopsis, « Lecture ») n'est PAS composé ; introuvable / erreur : état dessiné ; sinon : la fiche.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DetailGuardTest {
    @get:Rule val rule = createComposeRule()
    @After fun reset() = resetDetailHost()

    private var composed = 0
    private fun show(load: DetailLoad, lock: DetailLock, hasItem: Boolean = true, onUnlocked: () -> Unit = {}) {
        composed = 0
        rule.setDetail(tv = true, width = 1920, height = 1080) {
            DetailGuard(load, hasItem, lock, "Orbite basse", onRetry = {}, onUnlocked = onUnlocked, onBack = {}) {
                composed++
                Box(androidx.compose.ui.Modifier.testTag("fiche")) { Text("Lecture") }
            }
        }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    @Test fun verrouille_contenuNonCompose_codeProposeSansLecture() {
        show(DetailLoad.Ready, DetailLock.PIN)
        assertEquals(0, composed)
        rule.onAllNodes(hasTestTag("fiche")).assertCountEquals(0)
        rule.onAllNodes(hasText("Lecture")).assertCountEquals(0)
        rule.onNodeWithTag("dt-lock-unlock").assertExists()
    }

    @Test fun masque_contenuNonCompose_retourSeulement() {
        show(DetailLoad.Ready, DetailLock.HIDDEN)
        assertEquals(0, composed)
        rule.onAllNodes(hasTestTag("dt-lock-unlock")).assertCountEquals(0)
        rule.onNodeWithTag("dt-lock-back").assertExists()
    }

    @Test fun deverrouille_ficheAffichee() {
        show(DetailLoad.Ready, DetailLock.NONE)
        rule.onNodeWithTag("fiche").assertExists()
        rule.onAllNodes(hasTestTag("dt-lock")).assertCountEquals(0)
    }

    @Test fun introuvable_etatDessine_pasDeChargementInfini() {
        show(DetailLoad.NotFound, DetailLock.NONE, hasItem = false)
        rule.onNode(hasText("Ce titre n'est plus disponible")).assertExists()
        assertEquals(0, composed)
    }

    @Test fun erreur_etatDessineAvecReessayer() {
        show(DetailLoad.Failed(SyncErrorKind.TIMEOUT), DetailLock.NONE, hasItem = false)
        rule.onNodeWithTag("dt-state-retry").assertExists()
    }

    @Test fun chargement_silhouette() {
        show(DetailLoad.Loading, DetailLock.NONE, hasItem = false)
        rule.onNodeWithTag("dt-state").assertExists()
        assertEquals(0, composed)
    }
}

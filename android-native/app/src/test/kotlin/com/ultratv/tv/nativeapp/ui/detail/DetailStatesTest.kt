package com.ultratv.tv.nativeapp.ui.detail

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.stringsOf
import com.ultratv.tv.nativeapp.ui.common.InputClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val NO_TEXT = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

/** États de fiche à la télécommande (TV 1920 × 1080) : introuvable, erreur, chargement, focus. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DetailStatesTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() { resetDetailHost(); InputClock.lastKeyMs = 0L }

    private fun press(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
    }

    private fun show(state: DetailLoad, lang: AppLang = AppLang.French, onRetry: () -> Unit = {}, onBack: () -> Unit = {}) =
        rule.setDetail(tv = true, width = 1920, height = 1080, lang = lang) { DetailStatePanel(state, onRetry, onBack) }

    private fun settle() { rule.mainClock.advanceTimeBy(600); rule.waitForIdle() }

    @Test fun film_introuvable_messageEtRetour() {
        var back = 0
        show(DetailLoad.NotFound, onBack = { back++ })
        settle()
        rule.onNode(hasText("Ce titre n'est plus disponible")).assertExists()
        rule.onAllNodes(hasTestTag("dt-state-retry")).assertCountEquals(0)
        rule.onNodeWithTag("dt-state-back").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, back)
    }

    @Test fun erreur_reessayer_puisRetour_ordreDuFocus() {
        var retry = 0; var back = 0
        show(DetailLoad.Failed(SyncErrorKind.TIMEOUT), onRetry = { retry++ }, onBack = { back++ })
        settle()
        rule.onNode(hasText(stringsOf(AppLang.French).sync.messageFor(SyncErrorKind.TIMEOUT))).assertExists()
        rule.onNodeWithTag("dt-state-retry").assertIsFocused()
        press(Key.DirectionRight)
        rule.onNodeWithTag("dt-state-back").assertIsFocused()
        press(Key.DirectionLeft)
        rule.onNodeWithTag("dt-state-retry").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, retry); assertEquals(0, back)
        press(Key.DirectionRight); press(Key.DirectionCenter)
        assertEquals(1, back)
    }

    @Test fun chargement_aucunTexteNiFocusPerdu() {
        show(DetailLoad.Loading)
        settle()
        rule.onAllNodes(NO_TEXT).assertCountEquals(0)
        rule.onAllNodes(isFocused()).assertCountEquals(0)
        rule.onAllNodes(hasTestTag("dt-state-retry")).assertCountEquals(0)
        rule.onAllNodes(hasTestTag("dt-state-back")).assertCountEquals(0)
    }

    @Test fun chargement_puisIntrouvable_lefocusArriveSurRetour() {
        var state by mutableStateOf<DetailLoad>(DetailLoad.Loading)
        rule.setDetail(tv = true, width = 1920, height = 1080) { DetailStatePanel(state, {}, {}) }
        settle()
        state = DetailLoad.NotFound
        settle()
        rule.onNodeWithTag("dt-state-back").assertIsFocused()
        state = DetailLoad.Ready
        settle()
        rule.onAllNodes(NO_TEXT).assertCountEquals(0)
    }

    @Test fun focusInitial_neSeDeplacePasApresUneTouche() {
        // Une touche pressée depuis l'ouverture (ici : dans le futur proche) arrête la demande de focus initial.
        InputClock.lastKeyMs = SystemClock.uptimeMillis() + 60_000L
        show(DetailLoad.Failed(SyncErrorKind.OTHER))
        settle()
        rule.onAllNodes(isFocused()).assertCountEquals(0)
    }

    @Test fun erreur_chaqueCause_unMessageNonVide() {
        for (kind in SyncErrorKind.entries) {
            val msg = stringsOf(AppLang.French).sync.messageFor(kind)
            assertTrue("$kind", msg.isNotBlank())
        }
    }

    @Test fun etats_arabe_miroir() {
        show(DetailLoad.Failed(SyncErrorKind.TIMEOUT), lang = AppLang.Arabic)
        settle()
        val retry = rule.onNodeWithTag("dt-state-retry").fetchSemanticsNode().boundsInRoot
        val back = rule.onNodeWithTag("dt-state-back").fetchSemanticsNode().boundsInRoot
        // De droite à gauche : « Réessayer » (première action) est à droite de « Retour ».
        assertTrue("retry $retry / back $back", retry.left > back.left)
        rule.onNodeWithTag("dt-state-retry").assertIsFocused()
        rule.onNode(hasText(stringsOf(AppLang.Arabic).sync.messageFor(SyncErrorKind.TIMEOUT))).assertExists()
    }

    @Test fun arabe_introuvable_messageTraduit() {
        show(DetailLoad.NotFound, lang = AppLang.Arabic)
        settle()
        rule.onNode(hasText("هذا العنوان لم يعد متاحًا")).assertExists()
    }

    // --- Contenu verrouillé par le contrôle parental ---

    private fun showLock(lock: DetailLock, lang: AppLang = AppLang.French, onUnlock: () -> Unit = {}, onBack: () -> Unit = {}) =
        rule.setDetail(tv = true, width = 1920, height = 1080, lang = lang) { LockedDetailPanel(lock, "Orbite basse", onUnlock, onBack) }

    /**
     * Le panneau verrouillé ne contient aucun nœud d'image ; la « fiche déverrouillée » est ici un hôte minimal (les écrans
     * réels dépendent de Hilt). L'absence de requête d'image des vrais écrans est couverte par `DetailGuardTest` (le contenu
     * n'est pas composé tant que verrouillé) et par les tests de ViewModel (aucune requête réseau).
     */
    @Test fun verrouille_aucuneImage_codePuisFiche() {
        var lock by mutableStateOf(DetailLock.PIN)
        var asked = 0
        val primary = androidx.compose.ui.focus.FocusRequester()
        rule.setDetail(tv = true, width = 1920, height = 1080) {
            if (lock != DetailLock.NONE) LockedDetailPanel(lock, "Orbite basse", onUnlock = { asked++; lock = DetailLock.NONE }, onBack = {})
            else {
                // Fiche déverrouillée (représentée par son bouton principal) : le focus initial va au principal.
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.testTag("dt-backdrop").size(10.dp)
                    )
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.testTag("dt-primary").size(40.dp).focusRequester(primary).focusable()
                    )
                    com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus(primary, hasFocus = { false })
                }
            }
        }
        settle()
        // Verrouillé : ni fond, ni affiche, ni synopsis ; le titre en texte, le message de verrou et « Déverrouiller » (focus).
        rule.onAllNodes(hasTestTag("dt-backdrop")).assertCountEquals(0)
        rule.onAllNodes(hasTestTag("dt-backdrop-poster")).assertCountEquals(0)
        rule.onNode(hasText("Orbite basse")).assertExists()
        rule.onNode(hasText(stringsOf(AppLang.French).parentalLockedTitle)).assertExists()
        rule.onNodeWithTag("dt-lock-unlock").assertIsFocused()
        rule.onAllNodes(hasTestTag("dt-primary")).assertCountEquals(0)
        press(Key.DirectionCenter)
        assertEquals(1, asked)
        settle()
        rule.onAllNodes(hasTestTag("dt-lock")).assertCountEquals(0)
        rule.onNodeWithTag("dt-primary").assertIsFocused()
    }

    @Test fun verrouille_boutonRetourAppelleOnBack() {
        var back = 0
        showLock(DetailLock.PIN, onBack = { back++ })
        settle()
        press(Key.DirectionRight)
        rule.onNodeWithTag("dt-lock-back").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, back)
    }

    @Test fun codeAnnule_lefocusRevientSurDeverrouiller() {
        var key by mutableStateOf(0)
        rule.setDetail(tv = true, width = 1920, height = 1080) { LockedDetailPanel(DetailLock.PIN, "Orbite basse", {}, {}, refocusKey = key) }
        settle()
        press(Key.DirectionRight)
        rule.onNodeWithTag("dt-lock-back").assertIsFocused()
        // Fenêtre de code annulée : la demande de refocus ramène sur « Déverrouiller » même après une touche.
        key++
        settle()
        rule.onNodeWithTag("dt-lock-unlock").assertIsFocused()
    }

    @Test fun masqueProfil_retourSeulement() {
        var back = 0
        showLock(DetailLock.HIDDEN, onBack = { back++ })
        settle()
        rule.onNode(hasText("Non disponible pour ce profil")).assertExists()
        rule.onAllNodes(hasTestTag("dt-lock-unlock")).assertCountEquals(0)
        rule.onAllNodes(hasTestTag("dt-backdrop")).assertCountEquals(0)
        rule.onNodeWithTag("dt-lock-back").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, back)
    }

    @Test fun verrouille_arabe_textesTraduits() {
        showLock(DetailLock.PIN, lang = AppLang.Arabic)
        settle()
        rule.onNode(hasText(stringsOf(AppLang.Arabic).parentalLockedTitle)).assertExists()
        rule.onNode(hasText(stringsOf(AppLang.Arabic).parentalUnlock)).assertExists()
        rule.onNodeWithTag("dt-lock-unlock").assertIsFocused()
    }
}

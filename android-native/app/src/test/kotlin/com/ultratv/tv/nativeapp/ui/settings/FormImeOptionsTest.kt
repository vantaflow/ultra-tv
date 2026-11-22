package com.ultratv.tv.nativeapp.ui.settings

import android.view.inputmethod.EditorInfo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ajout de source sur Fire TV (clavier système plein écran, retour du 22/11) : pendant la saisie, le clavier annonce les
 * champs voisins (« Suivant » / « Précédent ») et l'action « Suivant » passe au champ suivant : tous les champs restent
 * atteignables sans refermer le clavier, au toucher comme à la télécommande. URL fictives uniquement (.invalid).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-xhdpi")
class FormImeOptionsTest {
    @get:Rule val rule = createComposeRule()

    private fun has(o: Int, flag: Int) = o and flag == flag

    @Test fun champIntermediaire_annonceSuivantEtPrecedent() {
        val o = formImeOptions(EditorInfo.IME_ACTION_NEXT, keepTextArea = true, last = false)
        assertTrue(has(o, EditorInfo.IME_FLAG_NAVIGATE_NEXT))
        assertTrue(has(o, EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS))
        assertEquals("Fire TV : sa zone de texte reste (nom du champ visible)", 0, o and EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        assertEquals(EditorInfo.IME_ACTION_NEXT, o and EditorInfo.IME_MASK_ACTION)
    }

    @Test fun dernierChamp_pasDeSuivant_etAutresClaviersSansPleinEcran() {
        val o = formImeOptions(EditorInfo.IME_ACTION_DONE, keepTextArea = false, last = true)
        assertEquals(0, o and EditorInfo.IME_FLAG_NAVIGATE_NEXT)
        assertTrue(has(o, EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS))
        assertTrue(has(o, EditorInfo.IME_FLAG_NO_EXTRACT_UI))
        assertTrue(has(o, EditorInfo.IME_FLAG_NO_FULLSCREEN))
    }

    @Test fun xtream_suivantDuClavier_parcourtTousLesChamps() {
        rule.setContent { XtreamDialog(onDismiss = {}, onSubmit = { _, _, _, _ -> }) }
        rule.onNodeWithTag("field-url").performTextInput("http://serveur-fictif.invalid:8080")
        rule.onNodeWithTag("field-url").performImeAction()
        rule.onNodeWithTag("field-user").assertIsFocused()
        rule.onNodeWithTag("field-user").performImeAction()
        rule.onNodeWithTag("field-pass").assertIsFocused()
    }
}

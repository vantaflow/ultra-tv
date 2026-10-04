package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertTextContains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** URL fictives uniquement (.invalid) : aucune vraie source dans les tests. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-xhdpi")
class ProviderFormsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun isValidHttpUrl_accepteHttpEtHttpsAvecHote() {
        assertTrue(isValidHttpUrl("http://serveur-fictif.invalid:8080"))
        assertTrue(isValidHttpUrl("  https://serveur-fictif.invalid/p.m3u "))
        assertFalse(isValidHttpUrl("serveur-fictif.invalid"))
        assertFalse(isValidHttpUrl("ftp://serveur-fictif.invalid"))
        assertFalse(isValidHttpUrl("http://"))
        assertFalse(isValidHttpUrl(""))
    }

    @Test fun m3u_saisieNomPuisUrl_chaqueValeurArriveDansSonChamp() {
        var submitted: Pair<String, String>? = null
        rule.setContent { M3uDialog(onDismiss = {}, onSubmit = { n, u -> submitted = n to u }) }

        // Ordre de la maquette : Nom puis URL ; le champ URL (obligatoire) reçoit le focus initial.
        rule.onNodeWithTag("field-url").assertIsFocused()
        rule.onNodeWithTag("field-url").performTextInput("http://serveur-fictif.invalid/liste.m3u")

        // D-pad HAUT depuis le champ : le focus DOIT passer au champ Nom (avant : il restait bloqué).
        rule.onNodeWithTag("field-url").performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag("field-name").assertIsFocused()
        rule.onNodeWithTag("field-name").performTextInput("Ma playlist")

        rule.onNodeWithTag("field-url").assertTextContains("http://serveur-fictif.invalid/liste.m3u")
        rule.onNodeWithTag("field-name").assertTextContains("Ma playlist")

        rule.onNodeWithTag("dialog-submit").performClick()
        assertEquals("Ma playlist" to "http://serveur-fictif.invalid/liste.m3u", submitted)
    }

    @Test fun m3u_urlInvalide_afficheUneErreur_etBloqueLAjout() {
        var submitted = false
        rule.setContent { M3uDialog(onDismiss = {}, onSubmit = { _, _ -> submitted = true }) }
        rule.onNodeWithTag("field-url").performTextInput("pas-une-url")
        rule.onNodeWithText("Enter a valid address starting with http:// or https://").assertExists()
        rule.onNodeWithTag("dialog-submit").performClick()
        assertFalse(submitted)
    }

    @Test fun xtream_ordreEtRemonteeAuD_pad() {
        var got: List<String>? = null
        rule.setContent { XtreamDialog(onDismiss = {}, onSubmit = { n, u, user, pw -> got = listOf(n, u, user, pw) }) }
        rule.onNodeWithTag("field-url").performTextInput("http://serveur-fictif.invalid:8080")
        rule.onNodeWithTag("field-url").performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("field-user").assertIsFocused()
        rule.onNodeWithTag("field-user").performTextInput("test")
        rule.onNodeWithTag("field-user").performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("field-pass").performTextInput("secret")
        // HAUT remonte au champ précédent.
        rule.onNodeWithTag("field-pass").performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag("field-user").assertIsFocused()
        rule.onNodeWithTag("dialog-submit").performClick()
        assertEquals(listOf("", "http://serveur-fictif.invalid:8080", "test", "secret"), got)
    }
}

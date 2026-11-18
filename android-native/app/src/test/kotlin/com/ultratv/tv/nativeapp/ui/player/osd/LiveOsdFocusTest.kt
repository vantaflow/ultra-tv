package com.ultratv.tv.nativeapp.ui.player.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parcours D-pad de l'OSD du Direct : le focus atteint chaque bouton de la barre, monte aux récents, redescend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LiveOsdFocusTest {
    @get:Rule val rule = createComposeRule()

    /** #85 : ouvert depuis le bandeau (OK / Bas), l'OSD pose le focus sur « Plus » APRÈS sa composition. */
    @Test fun ouvertureDepuisLeBandeau_poseLeFocusSurPlus() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = AppLang.English
        val full = LiveOsdRenderTest.state(lang)
        val expanded = androidx.compose.runtime.mutableStateOf(false)
        val focus = LiveOsdFocus()
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    LiveOsd(full.copy(expanded = expanded.value), focus, onAction = {}, onRecent = {}, moreLabel = "More")
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("More").assertDoesNotExist()       // bandeau seul : ni « Plus » ni commandes
        rule.onNodeWithText("Pause").assertDoesNotExist()
        rule.runOnIdle { expanded.value = true }
        rule.waitForIdle()
        rule.onNodeWithText("More").assertIsFocused()
        rule.onNodeWithText("Pause").assertDoesNotExist()      // commandes derrière « Plus »
    }

    /** OSD allégé : « Plus » ouvre les commandes (toutes atteignables), Retour les referme, Gauche mène aux récentes. */
    @Test fun plus_ouvreLesCommandes_puisRetourEtRecentes() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = AppLang.English
        val st = LiveOsdRenderTest.state(lang)
        val focus = LiveOsdFocus()
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { LiveOsd(st, focus, onAction = {}, onRecent = {}, moreLabel = "More") }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("More").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        rule.onNodeWithText("Pause").assertIsFocused()
        for (label in st.actions.drop(1).map { it.label }) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
            rule.onNodeWithText(label).assertIsFocused()
        }
        // Retour : le panneau se ferme, le focus revient sur « Plus ».
        rule.onRoot().performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()
        rule.onNodeWithText("Pause").assertDoesNotExist()
        rule.onNodeWithText("More").assertIsFocused()
        // Gauche : dernière chaîne récente (même rangée).
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        var onRecent = false
        for (r in st.recents) if (runCatching { rule.onNodeWithText(r.name).assertIsFocused() }.isSuccess) onRecent = true
        assertTrue("Gauche depuis « Plus » doit mener aux récentes", onRecent)
    }
}

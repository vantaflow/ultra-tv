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

    @Test fun dpad_atteintChaqueBoutonDeLaBarre_puisMonteAuxRecentsEtRedescend() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = AppLang.English
        val st = LiveOsdRenderTest.state(lang)
        val focus = LiveOsdFocus()
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { LiveOsd(st, focus, onAction = {}, onRecent = {}) }
            }
        }
        rule.waitForIdle()
        // Focus initial : premier bouton de la barre (Lecture/Pause).
        rule.onNodeWithText("Pause").assertIsFocused()
        val labels = st.actions.drop(1).map { a -> a.label }
        for (label in labels) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
            rule.onNodeWithText(label).assertIsFocused()
        }
        // Retour au début de la barre, puis Haut : rangée des récents.
        repeat(labels.size) { rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }; rule.waitForIdle() }
        rule.onNodeWithText("Pause").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        rule.onNodeWithText("TF1").assertIsFocused()
        // Haut depuis les récents : le focus reste (l'étage du haut n'est pas focalisable).
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        rule.onNodeWithText("TF1").assertIsFocused()
        // Bas : retour à la barre.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        var onBar = false
        for (a in st.actions) if (runCatching { rule.onNodeWithText(a.label).assertIsFocused() }.isSuccess) onBar = true
        assertTrue("le focus doit revenir dans la barre", onBar)
    }
}

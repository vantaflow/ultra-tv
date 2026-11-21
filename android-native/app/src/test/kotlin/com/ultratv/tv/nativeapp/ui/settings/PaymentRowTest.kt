package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.SettingsCard
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.theme.isLightTheme
import com.ultratv.tv.nativeapp.ui.theme.isOledTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ligne « Paiement par carte » de Réglages › À propos : visible, grisée, focalisable, sans effet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PaymentRowTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() { Ux.themeLight = false; Ux.themeOled = false; Ux.themeKind = AppTheme.DARK }

    private fun mount(lang: AppLang = AppLang.English, rtl: Boolean = false, touch: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit = { SettingsCard { PaymentRow() } }) {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides touch,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                androidx.compose.foundation.layout.Box(Modifier.background(Ux.Bg)) { content() }
            }
        }
    }

    @Test fun afficheSesTroisTextes() {
        mount()
        rule.onNodeWithTag("settings-payment").assertIsDisplayed()
        rule.onNodeWithText("Card payment", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Premium options will arrive in a future version.", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Coming soon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun desactiveePourLAccessibilite_etLueAvecSonEtat() {
        mount(AppLang.French)
        rule.onNodeWithTag("settings-payment").assertIsNotEnabled()
        rule.onNodeWithTag("settings-payment").assertTextContains("Paiement par carte", substring = true)
        rule.onNodeWithTag("settings-payment").assertTextContains("Bientôt disponible", substring = true)
    }

    @Test fun clicEtToucheOK_neFontRien() {
        mount()
        val app = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        rule.onNodeWithTag("settings-payment").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-payment").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithTag("settings-payment").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertNull("aucune activité lancée", app.nextStartedActivity)
        // Rien n'apparaît : toujours la seule ligne, mêmes textes.
        rule.onNodeWithText("Card payment", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTagCount("settings-payment-dialog"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test fun telecommande_traverseLaListeSansSautNiPiege() {
        mount {
            SettingsCard {
                Column {
                    PrefRow("Ligne A", "", Modifier.testTag("row-a")) { }
                    PaymentRow()
                    PrefRow("Ligne B", "", Modifier.testTag("row-b")) { }
                }
            }
        }
        rule.onNodeWithTag("row-a").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("row-a").assertIsFocused()
        fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
        key(Key.DirectionDown); rule.onNodeWithTag("settings-payment").assertIsFocused()
        key(Key.DirectionDown); rule.onNodeWithTag("row-b").assertIsFocused()
        key(Key.DirectionUp); rule.onNodeWithTag("settings-payment").assertIsFocused()
        key(Key.DirectionUp); rule.onNodeWithTag("row-a").assertIsFocused()
    }

    @Test fun rendu_dansTousLesThemes() {
        mount()
        for (theme in AppTheme.entries) {
            rule.runOnUiThread {
                Ux.themeLight = isLightTheme(theme, systemDark = true, isTv = true)
                Ux.themeOled = isOledTheme(theme)
                Ux.themeKind = theme
            }
            rule.waitForIdle()
            rule.onNodeWithTag("settings-payment").assertIsDisplayed()
        }
        assertTrue(AppTheme.entries.size >= 10)
    }

    @Test fun rendu_enArabeRTL_etAuTactile() {
        mount(AppLang.Arabic, rtl = true)
        rule.onNodeWithText("الدفع بالبطاقة", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("قريبًا", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun rendu_auTactile() {
        mount(touch = true)
        rule.onNodeWithTag("settings-payment").assertIsDisplayed().assertIsNotEnabled()
    }
}

package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsActions
import com.ultratv.tv.nativeapp.ui.common.ModalOpener
import org.junit.Assert.assertNotNull
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
        rule.onNodeWithText("Card payment will arrive in a future version.", useUnmergedTree = true).assertIsDisplayed()
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
        ModalOpener.take() // vide un éventuel marquage antérieur
        rule.onNodeWithTag("settings-payment").requestFocus()
        rule.onNodeWithTag("settings-payment").assertIsFocused()
        val avant = rule.onRoot().printToString()
        rule.onNodeWithTag("settings-payment").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-payment").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithTag("settings-payment").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertNull("aucune activité lancée", app.nextStartedActivity)
        assertNull("aucun ouvreur de fenêtre marqué", ModalOpener.take())
        assertEquals("arbre sémantique inchangé (aucun dialogue, aucun nœud ajouté)", avant, rule.onRoot().printToString())
        rule.onNodeWithTag("settings-payment").assertIsFocused()
    }

    private fun verifieSansActionMaisFocalisable(touch: Boolean) {
        mount(AppLang.French, touch = touch)
        val n = rule.onNodeWithTag("settings-payment")
        n.assertHasNoClickAction().assertIsNotEnabled()
        n.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus))
        n.assertTextContains("Paiement par carte", substring = true)
        n.assertTextContains("Bientôt disponible", substring = true)
        n.requestFocus(); n.assertIsFocused()
    }

    @Test fun pasDActionDeClicExposee_maisFocalisableEtLue_tv() = verifieSansActionMaisFocalisable(false)
    @Test fun pasDActionDeClicExposee_maisFocalisableEtLue_tactile() = verifieSansActionMaisFocalisable(true)

    private fun verifiePrefRow(touch: Boolean) {
        var n = 0
        ModalOpener.take()
        mount(touch = touch) { SettingsCard { PrefRow("Ligne", "v", Modifier.testTag("pref")) { n++ } } }
        rule.onNodeWithTag("pref").assertHasClickAction()
        rule.onNodeWithTag("pref").performClick(); rule.waitForIdle()
        assertEquals("onClick appelé (touch=$touch)", 1, n)
        if (!touch) assertNotNull("l'ouvreur est marqué comme avant", ModalOpener.take())
    }

    @Test fun prefRowExistante_gardeSonComportement_tv() = verifiePrefRow(false)
    @Test fun prefRowExistante_gardeSonComportement_tactile() = verifiePrefRow(true)

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

    @Test fun rendu_dansTousLesThemes_etContrasteLisible() {
        mount()
        val rapports = mutableListOf<String>()
        for (theme in AppTheme.entries) {
            rule.runOnUiThread {
                Ux.themeLight = isLightTheme(theme, systemDark = true, isTv = true)
                Ux.themeOled = isOledTheme(theme)
                Ux.themeKind = theme
            }
            rule.waitForIdle()
            rule.onNodeWithTag("settings-payment").assertIsDisplayed()
            var ligne = ""
            rule.runOnUiThread {
                for ((etat, fond) in listOf("repos" to Ux.SurfaceDeep, "focus" to Ux.Cta)) {
                    val f = etat == "focus"
                    for ((nom, c) in listOf("libelle" to paymentTitleColor(f), "bientot" to paymentValueColor(f), "sous-texte" to paymentSubColor(f))) {
                        val r = ratio(over(c.copy(alpha = PAYMENT_ROW_ALPHA), fond), fond)
                        ligne += " $etat/$nom=%.2f".format(r)
                        // « Bientôt disponible » et le libellé : au moins 3:1 ; le sous-texte, texte secondaire, au moins 2,5:1.
                        val seuil = if (nom == "sous-texte") 2.5 else 3.0
                        if (r < seuil) ligne += "(!)"
                    }
                }
            }
            rapports += "$theme:$ligne"
        }
        println("CONTRASTES\n" + rapports.joinToString("\n"))
        assertTrue("contrastes insuffisants :\n" + rapports.filter { "(!)" in it }.joinToString("\n"), rapports.none { "(!)" in it })
    }

    private fun over(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) = androidx.compose.ui.graphics.Color(
        red = fg.red * fg.alpha + bg.red * (1 - fg.alpha), green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
        blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha), alpha = 1f,
    )
    private fun lum(c: androidx.compose.ui.graphics.Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
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

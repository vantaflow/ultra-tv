package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.InputClock
import com.ultratv.tv.nativeapp.ui.common.ScreenFocusHost
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.SettingsCard
import com.ultratv.tv.nativeapp.ui.design.Ux
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Retour propriétaire : « En arabe, en ouvrant Réglages, le focus n'est pas dans les rubriques mais directement sur le
 * réglage. » À l'ouverture, le focus doit être sur la rubrique sélectionnée, dans toutes les langues ; la flèche vers le
 * volet (▶ en LTR, ◀ en RTL) y entre sur la première ligne, la flèche inverse revient à la rubrique ouverte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SettingsRtlFocusTest {
    @get:Rule val rule = createComposeRule()

    private val rubs = listOf(
        SettingsRubUi(Icons.List, "Sources", "3"),
        SettingsRubUi(Icons.Settings, "Affichage", "Thème"),
        SettingsRubUi(Icons.Play, "Lecture", "Moteur"),
    )
    private val selected = mutableIntStateOf(1)

    private fun mount(rtl: Boolean) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = if (rtl) AppLang.Arabic else AppLang.French
        val rubFocus = List(rubs.size) { FocusRequester() }
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    // Comme l'appli : l'écran est dans un ScreenFocusHost, qui pose le focus initial.
                    ScreenFocusHost {
                        SettingsTvLayout(
                            title = "Réglages", rubs = rubs, selected = selected.intValue, onSelect = { selected.intValue = it },
                            rubFocus = rubFocus, modalOpen = false,
                        ) {
                            SettingsCard {
                                PrefRow("Ligne 1", "A", Modifier.testTag("row-1")) {}
                                PrefRow("Ligne 2", "B", Modifier.testTag("row-2")) {}
                                PrefRow("Ligne 3", "C", Modifier.testTag("row-3")) {}
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private fun texts(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        (if (SemanticsProperties.Text in n.config) n.config[SemanticsProperties.Text].map { it.text } else emptyList()) + n.children.flatMap { texts(it) }

    /** Étiquette du nœud focalisé : son testTag (lignes du volet) ou le libellé de rubrique qu'il contient. */
    private fun focusedLabel(): String? {
        val n = rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull() ?: return null
        generateSequence(n) { it.parent }.firstNotNullOfOrNull { p -> if (SemanticsProperties.TestTag in p.config) p.config[SemanticsProperties.TestTag] else null }?.let { return it }
        return texts(n).firstOrNull { t -> rubs.any { it.label == t } } ?: texts(n).firstOrNull()
    }

    private fun press(key: Key, code: Int) {
        InputClock.lastKeyCode = code
        InputClock.lastKeyMs = android.os.SystemClock.uptimeMillis()
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun inRubrics(label: String?) = label != null && rubs.any { label.startsWith(it.label) }

    private fun scenario(rtl: Boolean) {
        mount(rtl)
        val opened = focusedLabel()
        assertTrue("ouverture (${if (rtl) "arabe" else "français"}) : focus dans les rubriques, pas « $opened »", inRubrics(opened))
        assertTrue("sur la rubrique sélectionnée : « $opened »", opened!!.startsWith("Affichage"))
        val (intoPane, back) = if (rtl) Key.DirectionLeft to Key.DirectionRight else Key.DirectionRight to Key.DirectionLeft
        val (intoCode, backCode) = if (rtl) android.view.KeyEvent.KEYCODE_DPAD_LEFT to android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            else android.view.KeyEvent.KEYCODE_DPAD_RIGHT to android.view.KeyEvent.KEYCODE_DPAD_LEFT
        press(intoPane, intoCode)
        assertEquals("flèche vers le volet : première ligne", "row-1", focusedLabel())
        press(Key.DirectionDown, android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("row-2", focusedLabel())
        press(back, backCode)
        val returned = focusedLabel()
        assertTrue("flèche inverse : retour à la rubrique ouverte, pas « $returned »", returned != null && returned.startsWith("Affichage"))
        assertEquals(1, selected.intValue)
    }

    @Test fun arabe_ouverture_focusDansLesRubriques_gaucheEntre_droiteRevient() = scenario(rtl = true)
    @Test fun francais_ouverture_focusDansLesRubriques_droiteEntre_gaucheRevient() = scenario(rtl = false)
}

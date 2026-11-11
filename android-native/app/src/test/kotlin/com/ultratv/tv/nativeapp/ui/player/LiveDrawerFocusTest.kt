package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parcours D-pad de la colonne des catégories du tiroir : le focus charge la catégorie sans OK, Droite entre dans la liste. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LiveDrawerFocusTest {
    @get:Rule val rule = createComposeRule()

    private val cats = listOf(
        DrawerCategory(CATEGORY_ALL, "Toutes les chaînes", 120),
        DrawerCategory("sport", "Sport", 14),
        DrawerCategory("news", "Actualités", 9),
    )

    private fun show(highlight: String, rtl: Boolean, focus: FocusRequester, focused: MutableList<String>, entered: IntArray) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = AppLang.French
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    DrawerCategoryColumn(
                        cats = cats, highlightId = highlight, rtl = rtl, selectedFocus = focus,
                        onFocused = { focused += it }, onEnter = { entered[0]++ }, modifier = Modifier.width(300.dp),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun focusSurUneCategorie_laSignaleSansOK_etDroiteEntreDansLaListe() {
        val focus = FocusRequester()
        val focused = mutableListOf<String>()
        val entered = intArrayOf(0)
        show(highlight = "sport", rtl = false, focus = focus, focused = focused, entered = entered)
        rule.runOnIdle { focus.requestFocus() }
        rule.waitForIdle()
        rule.onNodeWithText("Sport").assertIsFocused()
        assertEquals("sport", focused.last())
        // Bas : la catégorie suivante prend le focus et est signalée, sans aucun OK.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithText("Actualités").assertIsFocused()
        assertEquals("news", focused.last())
        // Haut deux fois : « Toutes les chaînes » (première entrée).
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        rule.onNodeWithText("Toutes les chaînes").assertIsFocused()
        assertEquals(CATEGORY_ALL, focused.last())
        assertEquals(0, entered[0])
        // Droite : on passe dans la liste des chaînes.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(1, entered[0])
    }

    @Test fun lectureDroiteAGauche_GaucheEntreDansLaListe() {
        val focus = FocusRequester()
        val focused = mutableListOf<String>()
        val entered = intArrayOf(0)
        show(highlight = "sport", rtl = true, focus = focus, focused = focused, entered = entered)
        rule.runOnIdle { focus.requestFocus() }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals("Droite n'entre pas dans la liste en RTL", 0, entered[0])
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        assertEquals(1, entered[0])
    }
}

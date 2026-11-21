package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class StreamingTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `voiles aux valeurs de la maquette`() {
        val s = imageScrimStops()
        assertEquals(listOf(0f to 1f, 0.45f to 0.86f, 1f to 0f), s.startAlpha)
        assertEquals(listOf(0f to 0f, 1f to 1f), s.bottomAlpha)
    }

    @Test fun `voile du cote du texte et inverse en arabe`() {
        assertTrue(scrimSideIsLeft(rtl = false))
        assertFalse(scrimSideIsLeft(rtl = true))
    }

    @Test fun `progression bornee entre 0 et 1`() {
        assertEquals(0f, progressFraction(-1f), 0f)
        assertEquals(1f, progressFraction(7f), 0f)
        assertEquals(0.4f, progressFraction(0.4f), 0f)
        assertEquals(0f, progressFraction(Float.NaN), 0f)
    }

    private fun fill(parent: String) = rule.onNode(hasTestTag(PROGRESS_FILL_TAG) and hasAnyAncestor(hasTestTag(parent)))

    @Test fun `ligne de progression hors bornes ne deborde pas de la piste`() {
        rule.setContent {
            Column {
                ProgressLine(-1f, Modifier.width(100.dp).testTag("a"))
                ProgressLine(7f, Modifier.width(100.dp).testTag("b"))
            }
        }
        rule.waitForIdle()
        val a = rule.onNodeWithTag("a").fetchSemanticsNode().size.width
        val fa = fill("a").fetchSemanticsNode().size.width
        val b = rule.onNodeWithTag("b").fetchSemanticsNode().size.width
        val fb = fill("b").fetchSemanticsNode().size.width
        assertEquals(0, fa)
        assertEquals(b, fb)
        assertTrue(a > 0)
    }

    @Test fun `bouton sur image - primaire et secondaire ont des fonds differents au repos`() {
        assertTrue(onImageButtonColors(true, false).bg != onImageButtonColors(false, false).bg)
    }

    @Test fun `bouton sur image - couleurs identiques dans les deux themes`() {
        val sombre = listOf(true, false).flatMap { p -> listOf(true, false).map { f -> onImageButtonColors(p, f) } }
        Ux.themeLight = true
        try {
            val clair = listOf(true, false).flatMap { p -> listOf(true, false).map { f -> onImageButtonColors(p, f) } }
            assertEquals(sombre, clair)
        } finally { Ux.themeLight = false }
        // Au focus : blanc / texte sombre pour les deux boutons.
        assertEquals(androidx.compose.ui.graphics.Color.White, onImageButtonColors(false, true).bg)
        assertEquals(onImageButtonColors(true, true), onImageButtonColors(false, true))
    }

    @Test fun `voile bas - la part couverte est reglable`() {
        // 0,40 par defaut (maquette), 0,08 en theme clair : constante exposee pour l'appelant.
        assertEquals(0.40f, BOTTOM_SPAN, 0f)
    }

    @Test fun `bouton sur image - le focus passe de l'un a l'autre avec droite et gauche`() {
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.ultratv.tv.nativeapp.i18n.LocalDs provides com.ultratv.tv.nativeapp.i18n.designStringsFor(com.ultratv.tv.nativeapp.i18n.AppLang.French)) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                androidx.compose.foundation.layout.Row {
                    val r = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
                    OnImageButton("A", {}, Modifier.testTag("a").focusRequester(r), primary = true)
                    OnImageButton("B", {}, Modifier.testTag("b"))
                    androidx.compose.runtime.LaunchedEffect(Unit) { r.requestFocus() }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("a").assertIsFocused()
        rule.onRoot().performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("b").assertIsFocused()
        rule.onRoot().performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        rule.waitForIdle()
        rule.onNodeWithTag("a").assertIsFocused()
    }
}

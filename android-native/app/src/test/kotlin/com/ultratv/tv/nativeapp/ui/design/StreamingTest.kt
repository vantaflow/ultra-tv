package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
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
}

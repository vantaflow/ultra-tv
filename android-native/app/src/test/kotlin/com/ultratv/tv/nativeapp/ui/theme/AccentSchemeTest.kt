package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.LightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccentSchemeTest {
    @Test fun `l'accent ne teinte ni les surfaces ni le fond`() {
        for (palette in listOf(DarkPalette, LightPalette)) {
            val colors = AccentChoice.entries.map { uxColorScheme(palette, Color(it.argb), Color(it.onAccent)) }
            // Aucune surface ne dépend de l'accent : identiques d'un accent à l'autre.
            assertEquals(1, colors.map { it.background }.distinct().size)
            assertEquals(1, colors.map { it.surface }.distinct().size)
            assertEquals(1, colors.map { it.surfaceVariant }.distinct().size)
            colors.forEach {
                assertEquals(Color.Transparent, it.surfaceTint)
                assertEquals(palette.bg, it.background)
                assertEquals(palette.card2, it.surface)
                assertEquals(palette.button, it.surfaceVariant)
            }
        }
    }

    @Test fun `l'accent reste la couleur primaire`() {
        val a = AccentChoice.entries.first { it != AccentChoice.DEFAULT }
        val s = uxColorScheme(DarkPalette, Color(a.argb), Color(a.onAccent))
        assertEquals(Color(a.argb), s.primary)
        assertNotEquals(s.background, s.primary)
    }
}

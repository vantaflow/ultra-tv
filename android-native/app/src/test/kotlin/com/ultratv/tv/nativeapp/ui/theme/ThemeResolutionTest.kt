package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.LightPalette
import com.ultratv.tv.nativeapp.ui.design.OledPalette
import com.ultratv.tv.nativeapp.ui.design.Wcag
import com.ultratv.tv.nativeapp.ui.design.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Résolution du thème (Automatique / Sombre / Clair) et palettes v2. Pur : aucune dépendance Android. */
class ThemeResolutionTest {
    @Test fun sombre_resteSombre_quelQueSoitLeSysteme() {
        assertFalse(isLightTheme(AppTheme.DARK, systemDark = false))
        assertFalse(isLightTheme(AppTheme.DARK, systemDark = true, isTv = false))
    }

    @Test fun clair_resteClair_quelQueSoitLeSysteme() {
        assertTrue(isLightTheme(AppTheme.LIGHT, systemDark = true))
        assertTrue(isLightTheme(AppTheme.LIGHT, systemDark = false, isTv = true))
    }

    @Test fun automatique_suitLeSystemeSurTelephone() {
        assertTrue(isLightTheme(AppTheme.AUTO, systemDark = false, isTv = false))
        assertFalse(isLightTheme(AppTheme.AUTO, systemDark = true, isTv = false))
    }

    @Test fun automatique_surTV_estToujoursSombre() {
        assertFalse(isLightTheme(AppTheme.AUTO, systemDark = false, isTv = true))
        assertFalse(isLightTheme(AppTheme.AUTO, systemDark = true, isTv = true))
    }

    @Test fun lecteur_forceLaPaletteSombre() {
        assertFalse(isLightEffective(themeLight = true, playerShown = true))
        assertSame(DarkPalette, paletteFor(isLightEffective(themeLight = true, playerShown = true)))
        assertSame(LightPalette, paletteFor(isLightEffective(themeLight = true, playerShown = false)))
    }

    @Test fun oled_estSombre_quelQueSoitLeSysteme() {
        assertFalse(isLightTheme(AppTheme.OLED, systemDark = false))
        assertFalse(isLightTheme(AppTheme.OLED, systemDark = true, isTv = true))
        assertTrue(isOledTheme(AppTheme.OLED))
        for (t in listOf(AppTheme.DARK, AppTheme.LIGHT, AppTheme.AUTO)) assertFalse(isOledTheme(t))
    }

    @Test fun oled_fondNoirPur_surfacesTresSombres() {
        val p = paletteFor(light = false, oled = true)
        assertSame(OledPalette, p)
        assertEquals(Color(0xFF000000), p.bg)
        assertFalse(p.light)
        for (c in listOf(p.rail, p.card, p.card2, p.button)) assertTrue(Wcag.luminance(c.toArgb().toLong() and 0xFFFFFFFFL) < 0.01)
        // Texte, accent et focus : ceux du thème sombre.
        assertEquals(DarkPalette.text, p.text); assertEquals(DarkPalette.accent, p.accent); assertEquals(DarkPalette.focusFill, p.focusFill)
        assertTrue(Wcag.ratio(0xFFF4F5F7L, 0xFF000000L) > 15)
    }

    @Test fun oled_neChangePasLesAutresThemes() {
        assertSame(DarkPalette, paletteFor(light = false))
        assertSame(DarkPalette, paletteFor(light = false, oled = false))
        assertSame(LightPalette, paletteFor(light = true, oled = true))
    }

    @Test fun oled_lecteurResteSombre() {
        assertFalse(isLightEffective(themeLight = false, playerShown = true))
        assertSame(DarkPalette, paletteFor(isLightEffective(themeLight = true, playerShown = true)))
    }

    @Test fun themeParse_valeurInconnue_retombeSurSombre_jamaisDErreur() {
        assertEquals(AppTheme.DARK, AppTheme.parse(null))
        assertEquals(AppTheme.DARK, AppTheme.parse(""))
        assertEquals(AppTheme.DARK, AppTheme.parse("neon"))
        assertEquals(AppTheme.OLED, AppTheme.parse("oled"))
        assertEquals(AppTheme.OLED, AppTheme.parse("OLED"))
        assertEquals(AppTheme.OLED, AppTheme.parse("AMOLED"))
        for (t in AppTheme.entries) assertEquals(t, AppTheme.parse(t.name))
    }

    @Test fun paletteSombre_valeursDeLaMaquette() {
        val p = DarkPalette
        assertEquals(Color(0xFF0A0C10), p.bg); assertEquals(Color(0xFF0E1117), p.rail)
        assertEquals(Color(0xFF161A22), p.card); assertEquals(Color(0xFF12151C), p.card2)
        assertEquals(Color(0xFF1C2029), p.button); assertEquals(Color(0xFF2A3140), p.tile)
        assertEquals(Color(0xFFF4F5F7), p.text); assertEquals(Color(0xFFFF3B47), p.accent)
        assertEquals(Color(0xFFFF8088), p.accentText); assertEquals(Color(0xFF2A1418), p.nowTint)
        assertEquals(Color(0xFFF4F5F7), p.focusFill); assertEquals(Color(0xFF0A0C10), p.focusText)
    }

    @Test fun paletteClaire_valeursDeLaMaquette() {
        val p = LightPalette
        assertEquals(Color(0xFFF5F6F8), p.bg); assertEquals(Color(0xFFFFFFFF), p.rail)
        assertEquals(Color(0xFFE8EBF0), p.button); assertEquals(Color(0xFFDCE1EA), p.tile)
        assertEquals(Color(0xFF12141A), p.text); assertEquals(Color(0xFFE0202C), p.accent)
        assertEquals(Color(0xFFC4161F), p.accentText); assertEquals(Color(0xFFFBE3E5), p.nowTint)
        assertEquals(Color(0xFF12141A), p.focusFill); assertEquals(Color(0xFFF5F6F8), p.focusText)
    }

    private fun lum(c: androidx.compose.ui.graphics.Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val x = lum(a); val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test fun nouveauxThemes_contrastesLisibles() {
        val all = listOf(
            com.ultratv.tv.nativeapp.ui.design.MidnightPalette, com.ultratv.tv.nativeapp.ui.design.ForestPalette,
            com.ultratv.tv.nativeapp.ui.design.PlumPalette, com.ultratv.tv.nativeapp.ui.design.SandPalette,
            com.ultratv.tv.nativeapp.ui.design.ContrastPalette,
        )
        for (p in all) {
            assertTrue("texte sur fond", ratio(p.text, p.bg) >= 7.0)
            assertTrue("texte sur carte", ratio(p.text, p.card) >= 7.0)
            assertTrue("texte secondaire", ratio(p.text3, p.bg) >= 4.5)
            assertTrue("focus", ratio(p.focusText, p.focusFill) >= 7.0)
            assertTrue("accent visible sur le fond", ratio(p.accent, p.bg) >= 3.0)
            assertTrue("texte sur l'accent", ratio(p.onAccent, p.accent) >= 3.0)
        }
    }

    @Test fun nouveauxThemes_resolution() {
        assertTrue(isLightTheme(AppTheme.SAND, systemDark = true, isTv = true))
        assertFalse(isLightTheme(AppTheme.MIDNIGHT, systemDark = false))
        assertSame(com.ultratv.tv.nativeapp.ui.design.SandPalette, paletteFor(light = true, theme = AppTheme.SAND))
        // Le lecteur force le sombre : un thème clair y retombe sur la palette sombre.
        assertSame(DarkPalette, paletteFor(light = false, theme = AppTheme.SAND))
        assertSame(com.ultratv.tv.nativeapp.ui.design.ForestPalette, paletteFor(light = false, theme = AppTheme.FOREST))
        assertEquals(AppTheme.CONTRAST, AppTheme.parse("contrast"))
        assertEquals(AppTheme.DARK, AppTheme.parse("inconnu"))
    }
}

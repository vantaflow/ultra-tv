package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.LightPalette
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
}

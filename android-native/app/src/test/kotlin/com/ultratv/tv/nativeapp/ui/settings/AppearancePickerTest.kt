package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.LightPalette
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aperçus de l'apparence (tuiles de thème, grille) : règles pures. */
class AppearancePickerTest {
    @Test fun lesDixThemesSontProposes() {
        assertEquals(10, AppearancePicker.themes.size)
        assertEquals(AppTheme.entries.toSet(), AppearancePicker.themes.toSet())
    }

    @Test fun automatique_moitieSombreMoitieClaire() {
        assertEquals(listOf(DarkPalette, LightPalette), AppearancePicker.previewPalettes(AppTheme.AUTO))
    }

    @Test fun sableEstClair_lesAutresThemesDeCouleurSontSombres() {
        assertTrue(AppearancePicker.previewPalettes(AppTheme.SAND).single().light)
        assertTrue(AppearancePicker.previewPalettes(AppTheme.LIGHT).single().light)
        for (t in listOf(AppTheme.DARK, AppTheme.OLED, AppTheme.MIDNIGHT, AppTheme.FOREST, AppTheme.PLUM, AppTheme.CONTRAST, AppTheme.TEAL))
            assertFalse("$t", AppearancePicker.previewPalettes(t).single().light)
        // Chaque thème a son propre aperçu (aucune tuile identique à une autre).
        val bgs = AppTheme.entries.filter { it != AppTheme.AUTO }.map { AppearancePicker.previewPalettes(it).single() }
        assertEquals(bgs.size, bgs.toSet().size)
    }

    @Test fun accentDeLApercu_celuiDuThemeSaufChoixExplicite() {
        val p = AppearancePicker.previewPalettes(AppTheme.FOREST).single()
        assertEquals(p.accent, AppearancePicker.previewAccent(p, AccentChoice.DEFAULT))
        assertEquals(Color(AccentChoice.BLUE.argb), AppearancePicker.previewAccent(p, AccentChoice.BLUE))
    }

    @Test fun colonnes_entre2et5() {
        assertEquals(2, AppearancePicker.columns(150f, 96f, 10f))
        assertEquals(3, AppearancePicker.columns(336f, 96f, 10f))   // téléphone 360 dp
        assertEquals(5, AppearancePicker.columns(540f, 88f, 8f))    // volet des Réglages TV
        assertEquals(5, AppearancePicker.columns(2000f, 88f, 8f))
    }
}

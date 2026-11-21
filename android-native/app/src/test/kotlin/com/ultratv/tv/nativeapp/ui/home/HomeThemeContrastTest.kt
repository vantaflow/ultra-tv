package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.ui.graphics.Color
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.design.paletteFor
import com.ultratv.tv.nativeapp.ui.theme.isLightTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lisibilité de l'accueil TV dans CHAQUE thème (`AppTheme.entries` : un thème ajouté plus tard est couvert d'office).
 * Les couples testés sont ceux que dessinent les cartes de l'accueil : titre et horaire sur la carte, pastille « EN DIRECT »
 * (texte sur accent), anneau et progression (accent sur la carte). Les rendus de `HomeRenderTest` complètent à l'œil.
 */
class HomeThemeContrastTest {
    private fun lum(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun ratio(a: Color, b: Color): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test fun accueil_chaqueTheme_coupleDeCouleursDesCartesLisibles() {
        for (theme in AppTheme.entries) {
            // Comme ApplyUxTheme sur une box : Automatique = sombre.
            val p = paletteFor(light = isLightTheme(theme, systemDark = true, isTv = true), theme = theme)
            val n = theme.name
            assertTrue("$n : titre du programme sur la carte", ratio(p.text, p.card) >= 7.0)
            assertTrue("$n : horaire (texte atténué) sur la carte", ratio(p.text3, p.card) >= 4.5)
            // Texte gras de pastille : seuil « grand texte » (3:1), comme le test des palettes ; le rouge par défaut (blanc sur #FF3B47) est à ~3,4:1.
            assertTrue("$n : texte de la pastille EN DIRECT sur l'accent", ratio(p.onAccent, p.accent) >= 3.0)
            assertTrue("$n : anneau et progression (accent) sur la carte", ratio(p.accent, p.card) >= 3.0)
            assertTrue("$n : titre de rangée sur le fond", ratio(p.text, p.bg) >= 7.0)
        }
    }
}

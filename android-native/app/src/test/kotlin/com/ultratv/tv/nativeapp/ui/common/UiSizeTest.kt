package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.ui.unit.Density
import com.ultratv.tv.nativeapp.ui.design.UiSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taille de l'interface (Petite / Normale / Grande / Très grande) : toute la densité, « Normale » = rendu d'avant. */
class UiSizeTest {
    private val phone = Density(2.75f, 1.0f)

    @Test fun `preference absente ou inconnue - Normale, rendu d'avant`() {
        assertEquals(UiSize.NORMAL, UiSize.parse(null))
        assertEquals(UiSize.NORMAL, UiSize.parse("geant"))
        assertEquals(UiSize.XLARGE, UiSize.parse("xlarge"))
        // Téléphone, texte normal, interface normale : même objet densité qu'avant.
        assertSame(phone, scaledDensity(tv = false, heightPx = 2400, base = phone, textScale = 1f, ui = 1f))
        // TV : 1080 px → 540 dp, comme avant.
        assertEquals(2f, scaledDensity(tv = true, heightPx = 1080, base = phone, textScale = 1f, ui = 1f).density, 0.0001f)
    }

    @Test fun `toute l'interface grandit, la taille du texte s'y ajoute`() {
        val d = scaledDensity(tv = false, heightPx = 2400, base = phone, textScale = 1.15f, ui = UiSize.LARGE.scale(tv = false))
        assertEquals(2.75f * 1.1f, d.density, 0.0001f)
        assertEquals(1.15f, d.fontScale, 0.0001f)
        val tv = scaledDensity(tv = true, heightPx = 1080, base = phone, textScale = 1.3f, ui = UiSize.SMALL.scale(tv = true))
        assertEquals(2f * 0.92f, tv.density, 0.0001f)
        assertEquals(1.3f, tv.fontScale, 0.0001f)
    }

    @Test fun `TV - ecart borne pour garder la maquette de 540 dp lisible`() {
        // « Très grande » sur TV laisse au moins 480 dp de haut (cadres fixes du Guide, des fiches et du lecteur).
        val h = 1080 / scaledDensity(tv = true, heightPx = 1080, base = phone, textScale = 1f, ui = UiSize.XLARGE.scale(tv = true)).density
        assertTrue("hauteur utile $h dp", h >= 480f)
        assertTrue(UiSize.entries.zipWithNext().all { (a, b) -> a.tvScale < b.tvScale && a.touchScale < b.touchScale })
    }
}

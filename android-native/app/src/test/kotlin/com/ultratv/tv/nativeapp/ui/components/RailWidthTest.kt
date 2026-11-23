package com.ultratv.tv.nativeapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailWidthTest {
    @Test fun `taille normale ou plus petite - largeur d'avant`() {
        assertEquals(RAIL_EXPANDED_PX, railExpandedPx(1f))
        assertEquals(RAIL_EXPANDED_PX, railExpandedPx(0.85f))
    }

    @Test fun `le menu s'elargit avec le texte`() {
        assertTrue(railExpandedPx(1.15f) > railExpandedPx(1f))
        assertEquals((RAIL_EXPANDED_PX * 1.3f).toInt(), railExpandedPx(1.3f))
    }
}

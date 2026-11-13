package com.ultratv.tv.nativeapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RailBadgeTest {
    private val label: (Int) -> String = { "$it en cours" }

    @Test fun favoris_nombreAffiche_saufZero() {
        assertEquals("12", railBadge("favorites", 12, 0, label))
        assertNull(railBadge("favorites", 0, 0, label))
    }

    @Test fun enregistrements_enCours() {
        assertEquals("1 en cours", railBadge("recordings", 0, 1, label))
        assertNull(railBadge("recordings", 5, 0, label))
        assertTrue(railDot("recordings", 1))
        assertFalse(railDot("recordings", 0))
        assertFalse(railDot("favorites", 3))
    }

    @Test fun autresEntrees_rien() {
        assertNull(railBadge("home", 3, 2, label))
        assertNull(railBadge("downloads", 3, 2, label))
    }
}

package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelGroupsTest {
    @Test fun portee_allerRetour_etRefusDesAutresListes() {
        assertEquals("__grp:12", ChannelGroups.scope(12))
        assertEquals(12L, ChannelGroups.idOf("__grp:12"))
        assertTrue(ChannelGroups.isScope("__grp:3"))
        assertFalse(ChannelGroups.isScope("__grp:"))
        assertFalse(ChannelGroups.isScope("__fav__"))
        assertFalse(ChannelGroups.isScope("42"))
        assertNull(ChannelGroups.idOf("__grp:abc"))
        assertNull(ChannelGroups.idOf(null))
    }

    @Test fun nom_nettoye_espacesResserres_plafonneA40() {
        assertEquals("Sport FR", ChannelGroups.cleanName("  Sport   FR "))
        assertEquals("", ChannelGroups.cleanName("   "))
        assertEquals(40, ChannelGroups.cleanName("x".repeat(80)).length)
    }

    @Test fun categoriesExclues_seulementPourUnProfilEnfants_etLaSourceDonnee() {
        val hidden = setOf("LIVE:1:adultes", "LIVE:2:autre", "MOVIE:1:films", "LIVE:1:cinema")
        assertEquals(emptyList<String>(), ChannelGroups.excludedCategories(false, hidden, 1))
        assertEquals(listOf("adultes", "cinema"), ChannelGroups.excludedCategories(true, hidden, 1))
    }
}

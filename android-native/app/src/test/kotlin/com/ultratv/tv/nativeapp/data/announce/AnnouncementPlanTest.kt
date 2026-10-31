package com.ultratv.tv.nativeapp.data.announce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementPlanTest {
    private val info = Announcement("a", "Info", "m", "info")
    private val urgent = Announcement("b", "", "panne", "urgent")

    @Test fun parse_ignoreLesEntreesInvalides() {
        val items = AnnouncementPlan.parse("""{"items":[{"id":"a","title":" T ","message":" m ","level":"warning"},{"id":"","message":"x"},{"id":"c","message":""}]}""")
        assertEquals(listOf(Announcement("a", "T", "m", "warning")), items)
        assertTrue(AnnouncementPlan.parse("pas du json").isEmpty())
    }

    @Test fun next_infoUneSeuleFois_urgentAChaqueLancement() {
        assertEquals(info, AnnouncementPlan.next(listOf(info), emptySet(), emptySet()))
        assertNull(AnnouncementPlan.next(listOf(info), setOf("a"), emptySet()))
        // Urgente déjà « vue » sur cet appareil : reproposée tant qu'elle n'a pas été montrée depuis ce lancement.
        assertEquals(urgent, AnnouncementPlan.next(listOf(urgent), setOf("b"), emptySet()))
        assertNull(AnnouncementPlan.next(listOf(urgent), emptySet(), setOf("b")))
    }

    @Test fun pruneSeen_gardeSeulementLesAlertesEncoreDiffusees_saufEchecReseau() {
        assertEquals(setOf("a"), AnnouncementPlan.pruneSeen(setOf("a", "vieux"), listOf(info), fetchedOk = true))
        assertEquals(setOf("a", "vieux"), AnnouncementPlan.pruneSeen(setOf("a", "vieux"), emptyList(), fetchedOk = false))
    }
}

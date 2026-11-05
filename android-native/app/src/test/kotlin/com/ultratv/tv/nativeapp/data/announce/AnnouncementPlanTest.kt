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
        val act = AnnouncementPlan.parse("""{"items":[{"id":"u","message":"maj","level":"warning","action":"update"},{"id":"v","message":"x","action":"pirate"}]}""")
        assertEquals(listOf("update", null), act.map { it.action })
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

    @Test fun affichage_bandeauPourLesInfosSansBouton_urgentesSeulesPendantLaLecture() {
        assertTrue(AnnouncementPlan.isBanner(info))
        assertEquals(false, AnnouncementPlan.isBanner(info.copy(action = "update")))
        assertEquals(false, AnnouncementPlan.isBanner(urgent))
        assertTrue(AnnouncementPlan.showDuringPlayback(urgent))
        assertEquals(false, AnnouncementPlan.showDuringPlayback(info))
    }

    @Test fun versionParam_sansSuffixe() {
        assertEquals("1.2.79", AnnouncementPlan.versionParam("1.2.79-debug"))
        assertEquals("", AnnouncementPlan.versionParam("dev"))
    }

    @Test fun historique_plusRecentEnTete_sansDoublon_borne() {
        var h = emptyList<ReceivedAnnouncement>()
        repeat(AnnouncementPlan.HISTORY_MAX + 5) { i -> h = AnnouncementPlan.addToHistory(h, Announcement("id$i", "", "m", "info"), i.toLong()) }
        assertEquals(AnnouncementPlan.HISTORY_MAX, h.size)
        assertEquals("id${AnnouncementPlan.HISTORY_MAX + 4}", h.first().id)
        h = AnnouncementPlan.addToHistory(h, Announcement("id10", "", "m", "info"), 99)
        assertEquals("id10", h.first().id)
        assertEquals(1, h.count { it.id == "id10" })
    }
}

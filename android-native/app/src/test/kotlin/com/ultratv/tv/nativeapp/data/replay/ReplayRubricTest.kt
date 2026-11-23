package com.ultratv.tv.nativeapp.data.replay

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Rubrique Replay : chaînes à archive visibles, catégories, jours proposés et programmes passés (hôtes fictifs example.test). */
class ReplayRubricTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L
    private val day = 24 * hour
    // 2027-01-15 14:30 UTC
    private val now = Calendar.getInstance(utc).apply { clear(); set(2027, 0, 15, 14, 30) }.timeInMillis
    private val todayStart = Calendar.getInstance(utc).apply { clear(); set(2027, 0, 15) }.timeInMillis

    private fun ch(id: Long, days: Int = 3, cat: String? = "c1", name: String = "Chaine $id", url: String = "http://example.test:8080/live/u/p/$id.ts", src: String? = null) =
        ChannelEntity(id = id, providerId = 7, remoteId = "$id", name = name, logo = null, categoryId = cat, streamUrl = url, catchupDays = days, catchupSource = src)

    private fun cat(id: String, name: String = "Cat $id", locked: Boolean = false, enabled: Boolean = true) =
        CategoryEntity(providerId = 7, kind = "LIVE", remoteId = id, name = name, locked = locked, enabled = enabled)

    private fun prog(start: Long, minutes: Int = 60, title: String = "P") =
        EpgEntity(channelId = 1, title = title, description = null, startMs = start, endMs = start + minutes * 60_000L)

    // ───────────── Chaînes ─────────────

    @Test fun chaines_sansArchiveOuSourceNonPrisEnCharge_exclues() {
        val list = listOf(
            ch(1), ch(2, days = 0),
            ch(3, url = "stalker://portail/3"),                        // ni gabarit ni adresse Xtream : pas de replay possible
            ch(4, url = "http://example.test/hls/4.m3u8", src = "http://example.test/arc/{utc}"),   // gabarit M3U : pris en charge
        )
        assertEquals(listOf(1L, 4L), ReplayRubric.visibleChannels(list, 7, emptySet(), emptySet(), kids = false, nowMs = now).map { it.id })
    }

    @Test fun chaines_categorieMasqueeParLeProfil_exclues() {
        val list = listOf(ch(1, cat = "c1"), ch(2, cat = "c2"))
        assertEquals(listOf(2L), ReplayRubric.visibleChannels(list, 7, setOf("LIVE:7:c1", "LIVE:8:c2"), emptySet(), kids = false, nowMs = now).map { it.id })
    }

    @Test fun chaines_categorieDesactivee_exclues() {
        val list = listOf(ch(1, cat = "c1"), ch(2, cat = "c2"))
        assertEquals(listOf(1L), ReplayRubric.visibleChannels(list, 7, emptySet(), setOf("c2"), kids = false, nowMs = now).map { it.id })
    }

    @Test fun chaines_adultes_excluesDuProfilEnfants_seulement() {
        val list = listOf(ch(1), ch(2, name = "XXX Nuit"))
        assertEquals(listOf(1L), ReplayRubric.visibleChannels(list, 7, emptySet(), emptySet(), kids = true, nowMs = now).map { it.id })
        assertEquals(listOf(1L, 2L), ReplayRubric.visibleChannels(list, 7, emptySet(), emptySet(), kids = false, nowMs = now).map { it.id })
    }

    // ───────────── Catégories ─────────────

    @Test fun categories_dansLOrdreDuDirect_seulementCellesAvecUneChaineAArchive() {
        val cats = listOf(cat("c3"), cat("c1", locked = true), cat("c2"))
        val chans = listOf(ch(1, cat = "c1"), ch(2, cat = "c1"), ch(3, cat = "c3"), ch(4, cat = null))
        val out = ReplayRubric.categories(chans, cats)
        assertEquals(listOf("c3", "c1"), out.map { it.id })
        assertEquals(listOf(1, 2), out.map { it.count })
        assertEquals(listOf(false, true), out.map { it.locked })
    }

    @Test fun filtre_parCategorie_ouTout() {
        val chans = listOf(ch(1, cat = "c1"), ch(2, cat = "c2"), ch(3, cat = "c1"))
        assertEquals(listOf(1L, 3L), ReplayRubric.filter(chans, "c1").map { it.id })
        assertEquals(listOf(1L, 2L, 3L), ReplayRubric.filter(chans, ReplayRubric.ALL).map { it.id })
        // Catégorie disparue (masquée depuis) : toutes les chaînes plutôt qu'une liste vide.
        assertEquals(listOf(1L, 2L, 3L), ReplayRubric.filter(chans, "absente").map { it.id })
    }

    // ───────────── Jours ─────────────

    @Test fun jours_aujourdhuiPuisJusquALaDureeDArchive() {
        val days = ReplayRubric.days(3, now, utc)
        assertEquals(listOf(0, 1, 2, 3), days.map { it.offset })
        assertEquals(todayStart, days[0].startMs)
        assertEquals(todayStart + day, days[0].endMs)
        assertEquals(todayStart - day, days[1].startMs)
        assertEquals(todayStart - 3 * day, days[3].startMs)
    }

    @Test fun jours_plafonnesA7_commeLesProgrammesGardes() {
        assertEquals(8, ReplayRubric.days(30, now, utc).size)
    }

    @Test fun jours_sansArchive_aucun() {
        assertTrue(ReplayRubric.days(0, now, utc).isEmpty())
    }

    @Test fun jours_unJour_aujourdhuiEtHier() {
        assertEquals(listOf(0, 1), ReplayRubric.days(1, now, utc).map { it.offset })
    }

    // ───────────── Programmes ─────────────

    @Test fun programmes_duJour_dejaCommences_triesEtSansDoublon() {
        val c = ch(1, days = 3)
        val d0 = ReplayRubric.days(3, now, utc)[0]
        val list = listOf(
            prog(todayStart + 13 * hour, title = "B"),
            prog(todayStart + 8 * hour, title = "A"),
            prog(todayStart + 8 * hour, title = "A bis"),          // même début : un seul
            prog(todayStart + 14 * hour, title = "En cours"),
            prog(todayStart + 16 * hour, title = "À venir"),       // pas encore commencé
            prog(todayStart - 2 * hour, title = "Hier soir"),      // commencé la veille
        )
        val rows = ReplayRubric.rows(c, list, d0, now)
        assertEquals(listOf("A", "B", "En cours"), rows.map { it.prog.title })
        assertEquals(listOf(false, false, true), rows.map { it.onNow })
        assertTrue(rows.all { it.playable })
    }

    @Test fun programmes_horsFenetreDArchive_indisponibles() {
        val c = ch(1, days = 1)
        val yesterday = ReplayRubric.days(1, now, utc)[1]
        val rows = ReplayRubric.rows(c, listOf(prog(todayStart - 20 * hour), prog(todayStart - 5 * hour)), yesterday, now)
        assertEquals(listOf(ReplayAvailability.OUT_OF_WINDOW, ReplayAvailability.AVAILABLE), rows.map { it.availability })
        assertFalse(rows[0].playable)
    }

    // ───────────── Écran vide ─────────────

    @Test fun ecranVide_raisons() {
        assertEquals(ReplayEmpty.NO_SOURCE, ReplayRubric.emptyReason(hasSource = false, archived = 0, visible = 0))
        assertEquals(ReplayEmpty.NO_ARCHIVE, ReplayRubric.emptyReason(hasSource = true, archived = 0, visible = 0))
        assertEquals(ReplayEmpty.ALL_HIDDEN, ReplayRubric.emptyReason(hasSource = true, archived = 4, visible = 0))
        assertNull(ReplayRubric.emptyReason(hasSource = true, archived = 4, visible = 2))
    }
}

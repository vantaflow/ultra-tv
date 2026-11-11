package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammeInfoTest {
    private fun p(id: Long, start: Long, end: Long, title: String = "P$id") = EpgEntity(id = id, channelId = 1, title = title, description = null, startMs = start, endMs = end)
    private val items = listOf(p(1, 0, 100), p(2, 100, 200), p(3, 200, 300), p(4, 300, 400))

    @Test fun suivants_apres_le_programme_en_cours() {
        assertEquals(listOf(2L, 3L), ProgrammeInfo.after(items, items[0], 50, 2).map { it.id })
        assertEquals(listOf(4L), ProgrammeInfo.after(items, items[2], 250, 2).map { it.id })
    }

    @Test fun sans_programme_en_cours_on_part_de_maintenant() {
        assertEquals(listOf(3L), ProgrammeInfo.after(items, null, 150, 1).map { it.id })
    }

    @Test fun ordre_chronologique_meme_si_liste_desordonnee() {
        assertEquals(listOf(2L, 3L), ProgrammeInfo.after(items.reversed(), items[0], 10, 2).map { it.id })
    }

    @Test fun rien_apres_le_dernier() { assertNull(ProgrammeInfo.after(items, items[3], 350, 1).firstOrNull()) }

    @Test fun annee_et_duree() {
        assertEquals(2019, ProgrammeInfo.yearOf("2019-05-12")); assertEquals(1998, ProgrammeInfo.yearOf("12/05/1998"))
        assertNull(ProgrammeInfo.yearOf(null)); assertNull(ProgrammeInfo.yearOf("inconnu"))
        assertEquals("1 h 35", ProgrammeInfo.durationLabel(95 * 60_000L)); assertEquals("45 min", ProgrammeInfo.durationLabel(45 * 60_000L))
        assertNull(ProgrammeInfo.durationLabel(0)); assertNull(ProgrammeInfo.durationLabel(-1))
    }

    @Test fun ligne_ensuite() {
        assertEquals("Ensuite 21:30 · Film", ProgrammeInfo.thenLine("Ensuite", p(1, 5, 9, "Film")) { "21:30" })
        assertNull(ProgrammeInfo.thenLine("Ensuite", null) { "x" })
        assertNull(ProgrammeInfo.thenLine("Ensuite", p(1, 5, 9, " ")) { "x" })
    }
}

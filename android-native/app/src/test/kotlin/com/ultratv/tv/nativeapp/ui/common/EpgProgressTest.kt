package com.ultratv.tv.nativeapp.ui.common

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgProgressTest {
    private val min = 60_000L
    private fun e(s: Long, en: Long, t: String) = EpgEntity(channelId = 1, title = t, description = null, startMs = s, endMs = en)

    @Test fun progression_milieu_debut_fin_et_hors_bornes() {
        assertEquals(0.5f, EpgProgress.fraction(0, 100, 50), 0.0001f)
        assertEquals(0f, EpgProgress.fraction(100, 200, 50), 0f)
        assertEquals(1f, EpgProgress.fraction(0, 100, 500), 0f)
        assertEquals(0f, EpgProgress.fraction(10, 10, 10), 0f)   // durée nulle : pas de division par zéro
    }

    @Test fun minutesRestantes_arrondiesAuDessus() {
        assertEquals(1, EpgProgress.remainingMinutes(10 * min, 10 * min - 1_000))
        assertEquals(2, EpgProgress.remainingMinutes(10 * min, 8 * min))
        assertEquals(0, EpgProgress.remainingMinutes(10 * min, 10 * min))
        assertEquals(0, EpgProgress.remainingMinutes(10 * min, 20 * min))
    }

    @Test fun enCoursEtSuivant_listeDansLeDesordre() {
        val now = 100 * min
        val l = listOf(e(160 * min, 200 * min, "c"), e(60 * min, 120 * min, "a"), e(120 * min, 160 * min, "b"))
        val (cur, next) = EpgProgress.currentAndNext(l, now)
        assertEquals("a", cur?.title); assertEquals("b", next?.title)
    }

    @Test fun enCoursEtSuivant_finExclue_debutInclus() {
        val l = listOf(e(0, 100, "a"), e(100, 200, "b"))
        assertEquals("b", EpgProgress.currentAndNext(l, 100).first?.title)
        assertNull(EpgProgress.currentAndNext(l, 100).second)
        assertNull(EpgProgress.currentAndNext(l, 500).first)
        assertNull(EpgProgress.currentAndNext(emptyList(), 5).first)
    }

    @Test fun prochains_ordreEtLimite() {
        val l = listOf(e(300, 400, "d"), e(100, 200, "b"), e(200, 300, "c"), e(0, 100, "a"))
        assertEquals(listOf("b", "c"), EpgProgress.upcoming(l, 50, 2).map { it.title })
    }

    @Test fun ligneDeLHeure_dansEtHorsFenetre() {
        assertEquals(0.5f, EpgProgress.nowLineFraction(150, 100, 100)!!, 0.0001f)
        assertEquals(0f, EpgProgress.nowLineFraction(100, 100, 100)!!, 0f)
        assertNull(EpgProgress.nowLineFraction(99, 100, 100))
        assertNull(EpgProgress.nowLineFraction(201, 100, 100))
        assertNull(EpgProgress.nowLineFraction(150, 100, 0))
    }

    @Test fun duree() = assertEquals(60, EpgProgress.durationMinutes(0, 60 * min))
}

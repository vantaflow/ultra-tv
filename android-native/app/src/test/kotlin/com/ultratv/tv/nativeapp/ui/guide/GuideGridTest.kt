package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideGridTest {
    private val h = 3_600_000L
    private fun e(s: Long, en: Long, t: String = "p") = EpgEntity(channelId = 1, title = t, description = null, startMs = s, endMs = en)

    @Test fun normalizeRow_programmesRepetes_neGardentQuUn() {
        val r = normalizeRow(listOf(e(0, h, "a"), e(0, h, "a2"), e(0, h, "a3")))
        assertEquals(1, r.size)
    }
    @Test fun normalizeRow_chevauchement_rogneLeDebutDuSuivant() {
        val r = normalizeRow(listOf(e(0, 2 * h, "a"), e(h, 3 * h, "b")))
        assertEquals(listOf(0L to 2 * h, 2 * h to 3 * h), r.map { it.startMs to it.endMs })
    }
    @Test fun normalizeRow_ordreChronologique() {
        val r = normalizeRow(listOf(e(2 * h, 3 * h, "b"), e(0, h, "a")))
        assertEquals(listOf("a", "b"), r.map { it.title })
    }
    @Test fun normalizeRow_programmeSansDuree_estIgnore() = assertEquals(0, normalizeRow(listOf(e(h, h))).size)

    @Test fun slotFor_dansLaFenetre() {
        val s = slotFor(h, 2 * h, windowStart = 0, windowMs = 4 * h)!!
        assertEquals(0.25f, s.startFrac, 0.0001f); assertEquals(0.25f, s.widthFrac, 0.0001f)
    }
    @Test fun slotFor_debordeAGauche_estTronque() {
        val s = slotFor(-h, h, windowStart = 0, windowMs = 4 * h)!!
        assertEquals(0f, s.startFrac, 0.0001f); assertEquals(0.25f, s.widthFrac, 0.0001f)
    }
    @Test fun slotFor_debordeADroite_estTronque() {
        val s = slotFor(3 * h, 9 * h, windowStart = 0, windowMs = 4 * h)!!
        assertEquals(0.75f, s.startFrac, 0.0001f); assertEquals(0.25f, s.widthFrac, 0.0001f)
    }
    @Test fun slotFor_horsFenetre_estNul() { assertNull(slotFor(5 * h, 6 * h, 0, 4 * h)); assertNull(slotFor(-2 * h, -h, 0, 4 * h)) }
    @Test fun guideWindowStart_aujourdhui_demiHeureCourante() {
        val now = 10 * h + 17 * 60_000L
        assertEquals(10 * h + 0L, guideWindowStart(now, 0))
    }
}

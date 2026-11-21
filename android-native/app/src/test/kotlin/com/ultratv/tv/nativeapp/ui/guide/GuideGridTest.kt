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
    // ── Bord de fenêtre borné aux données (I3) ──
    private val m = 60_000L
    @Test fun edgeShift_avecVoisin_decale_de_90_minutes() {
        assertEquals(10 * h + 90 * m, guideEdgeShift(10 * h, true, e(11 * h, 13 * h), neighbour = e(13 * h, 14 * h)))
        assertEquals(10 * h - 90 * m, guideEdgeShift(10 * h, false, e(9 * h, 11 * h), neighbour = e(8 * h, 9 * h)))
    }
    @Test fun edgeShift_trou_de_cinq_heures_la_fenetre_saute_jusqu_au_voisin() {
        // Fin à 01:00, reprise à 06:00 (fenêtre 00:30) : la nouvelle fenêtre (05:30) montre 06:00 ; en arrière, symétrique.
        assertEquals(5 * h + 30 * m, guideEdgeShift(30 * m, true, e(0, h), neighbour = e(6 * h, 7 * h)))
        assertEquals(h - 3 * h + 30 * m, guideEdgeShift(6 * h, false, e(6 * h, 7 * h), neighbour = e(0, h)))
    }
    @Test fun edgeShift_sansVoisin_programme_dans_la_fenetre_butee() {
        assertEquals(10 * h, guideEdgeShift(10 * h, true, e(12 * h, 13 * h), neighbour = null))
        assertEquals(10 * h, guideEdgeShift(10 * h, false, e(10 * h, 11 * h), neighbour = null))
    }
    @Test fun edgeShift_sansVoisin_long_programme_va_jusqu_a_sa_fin_arrondie_a_la_demi_heure() {
        // Dernier programme 12:00 – 14:10, fenêtre 10:00 – 13:00 : 90 min puis 14:30 − 3 h = 11:30 au plus.
        assertEquals(11 * h + 30 * m, guideEdgeShift(10 * h, true, e(12 * h, 14 * h + 10 * m), neighbour = null))
        // Premier programme 08:20 – 11:00, fenêtre 10:00 : jusqu'à 08:00 au plus (la demi-heure qui le contient).
        assertEquals(8 * h + 30 * m, guideEdgeShift(10 * h, false, e(8 * h + 20 * m, 11 * h), neighbour = null))
        assertEquals(8 * h, guideEdgeShift(9 * h, false, e(8 * h + 20 * m, 11 * h), neighbour = null))
    }
}

class ProgramActionStateTest {
    private fun prog(start: Long, end: Long) = com.ultratv.tv.nativeapp.data.db.EpgEntity(id = 1, channelId = 1, title = "t", description = null, startMs = start, endMs = end)

    @org.junit.Test fun programmeFutur_rappelEtEnregistrementPossibles_pasDeReplay() {
        val s = com.ultratv.tv.nativeapp.ui.guide.programActionState(prog(2_000, 3_000), replayUrl = null, nowMs = 1_000)
        org.junit.Assert.assertEquals(com.ultratv.tv.nativeapp.ui.guide.ProgramActionState(replayEnabled = false, remindEnabled = true, recordEnabled = true), s)
    }

    @org.junit.Test fun programmePasse_avecReplay_ni_rappel_ni_enregistrement() {
        val s = com.ultratv.tv.nativeapp.ui.guide.programActionState(prog(100, 500), replayUrl = "x", nowMs = 1_000)
        org.junit.Assert.assertEquals(com.ultratv.tv.nativeapp.ui.guide.ProgramActionState(replayEnabled = true, remindEnabled = false, recordEnabled = false), s)
    }

    @org.junit.Test fun programmeEnCours_enregistrementPossibleMaisPasDeRappel() {
        val s = com.ultratv.tv.nativeapp.ui.guide.programActionState(prog(500, 5_000), replayUrl = null, nowMs = 1_000)
        org.junit.Assert.assertFalse(s.remindEnabled); org.junit.Assert.assertTrue(s.recordEnabled)
    }

}

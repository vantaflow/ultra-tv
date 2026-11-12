package com.ultratv.tv.nativeapp.ui.player.osd

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ruban de la soirée et petits calculs de texte de l'OSD (purs). */
class OsdRibbonTest {
    private val min = 60_000L
    private val t0 = 1_000_000_000_000L
    private fun prog(title: String, startMin: Int, endMin: Int) = EpgEntity(channelId = 1, title = title, description = null, startMs = t0 + startMin * min, endMs = t0 + endMin * min)
    private val hm: (Long) -> String = { ms -> "m${(ms - t0) / min}" }

    private val guide = listOf(
        prog("Avant-hier", -300, -240),
        prog("Le 18h30", -90, 0),
        prog("Journal", 0, 90),
        prog("Grand débat", 90, 160),
        prog("Le Club", 160, 220),
        prog("Nuit info", 220, 280),
        prog("Trop loin", 280, 340),
    )

    @Test fun precedentEnCoursEtTroisSuivants() {
        val s = OsdRibbon.slots(guide, t0 + 36 * min, hm, "EN COURS")
        assertEquals(listOf(RibbonKind.PAST, RibbonKind.NOW, RibbonKind.NEXT, RibbonKind.NEXT, RibbonKind.NEXT), s.map { it.kind })
        assertEquals(listOf("Le 18h30", "Journal", "Grand débat", "Le Club", "Nuit info"), s.map { it.title })
    }

    @Test fun libelleDuProgrammeEnCours_porteHeureEtMot() {
        val s = OsdRibbon.slots(guide, t0 + 36 * min, hm, "EN COURS")
        assertEquals("m0 · EN COURS", s[1].label)
        assertEquals("m-90", s[0].label)
    }

    @Test fun avancementDuProgrammeEnCours() {
        val s = OsdRibbon.slots(guide, t0 + 36 * min, hm, "x").first { it.kind == RibbonKind.NOW }
        assertEquals(0.4f, s.fraction, 0.001f)
        assertTrue(OsdRibbon.slots(guide, t0 + 36 * min, hm, "x").filter { it.kind != RibbonKind.NOW }.all { it.fraction == 0f })
    }

    @Test fun largeursProportionnellesALaDuree_bornees() {
        assertEquals(3f, OsdRibbon.weightOf(t0, t0 + 90 * min), 0.001f)
        assertEquals(2f, OsdRibbon.weightOf(t0, t0 + 60 * min), 0.001f)
        // Bornes : 2 minutes comptent pour 20, un film de 4 h pour 120.
        assertEquals(20f / 30f, OsdRibbon.weightOf(t0, t0 + 2 * min), 0.001f)
        assertEquals(4f, OsdRibbon.weightOf(t0, t0 + 240 * min), 0.001f)
    }

    @Test fun sansProgrammeEnCours_rubanMasque() {
        assertTrue(OsdRibbon.slots(emptyList(), t0, hm, "x").isEmpty())
        assertTrue(OsdRibbon.slots(guide, t0 + 600 * min, hm, "x").isEmpty())
    }

    @Test fun sansPrecedent_ouSansSuivant_resteCoherent() {
        val s = OsdRibbon.slots(listOf(prog("Seul", 0, 30)), t0 + 10 * min, hm, "x")
        assertEquals(listOf(RibbonKind.NOW), s.map { it.kind })
        val onlyPrev = OsdRibbon.slots(listOf(prog("A", -30, 0), prog("B", 0, 30)), t0 + 5 * min, hm, "x")
        assertEquals(listOf(RibbonKind.PAST, RibbonKind.NOW), onlyPrev.map { it.kind })
    }

    @Test fun entreesInvalidesIgnorees() {
        val s = OsdRibbon.slots(listOf(prog("", 0, 30), prog("Durée nulle", 0, 0), prog("Ok", 0, 30), prog("Doublon", 0, 45)), t0 + 5 * min, hm, "x")
        assertEquals(1, s.size)
        assertEquals("Ok", s[0].title)
    }

    @Test fun initiales() {
        assertEquals("FI", OsdText.initials("France Info"))
        assertEquals("TF", OsdText.initials("TF1"))
        assertEquals("BS", OsdText.initials("beIN Sports 1"))
        assertEquals("M6", OsdText.initials("M6"))
        assertEquals("·", OsdText.initials("  "))
    }

    @Test fun ligneMaintenant() {
        assertEquals("MAINTENANT · 20:00 – 21:30 · RESTE 54 MIN", OsdText.nowLine("Maintenant", "20:00 – 21:30", "reste 54 min"))
        assertEquals("MAINTENANT", OsdText.nowLine("Maintenant", null, null))
        assertEquals("MAINTENANT · RESTE 5 MIN", OsdText.nowLine("Maintenant", "", "reste 5 min"))
    }

    @Test fun qualiteEtHdr() {
        assertEquals("4K", OsdText.qualityLabel(4)); assertEquals("FHD", OsdText.qualityLabel(3))
        assertEquals("HD", OsdText.qualityLabel(2)); assertEquals("SD", OsdText.qualityLabel(1))
        assertEquals(null, OsdText.qualityLabel(0))
        assertEquals("HDR10", OsdText.hdrLabel("hdr10")); assertEquals("HLG", OsdText.hdrLabel("hlg"))
        assertEquals("Dolby Vision", OsdText.hdrLabel("dv")); assertEquals(null, OsdText.hdrLabel(null))
    }
}

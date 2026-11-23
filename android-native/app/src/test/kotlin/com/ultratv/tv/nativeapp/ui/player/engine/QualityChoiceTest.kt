package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityChoiceTest {
    private fun t(id: String, h: Int, kbps: Int, sel: Boolean = false) = VideoQuality(id, h, kbps, sel)

    @Test fun variantes_trieesDeLaPlusHaute_sansDoublon_autoParDefaut() {
        val m = QualityChoice.menu(listOf(t("0:0", 720, 2500), t("0:1", 1080, 5000), t("0:2", 480, 1200), t("1:0", 1080, 5000)), null, null)
        m as QualityChoice.Menu.Choices
        assertTrue(m.autoSelected)
        assertEquals(listOf("1080p", "720p", "480p"), m.options.map { it.label })
        assertTrue(m.options.all { it.enabled && !it.selected })
    }

    @Test fun auDessusDuPlafond_grisees() {
        val m = QualityChoice.menu(listOf(t("a", 2160, 15000), t("b", 1080, 5000), t("c", 720, 2500)), 1080, null) as QualityChoice.Menu.Choices
        assertEquals(listOf(false, true, true), m.options.map { it.enabled })
    }

    @Test fun choixManuel_coche_etAutoDecoche() {
        val m = QualityChoice.menu(listOf(t("a", 1080, 5000), t("b", 720, 2500)), null, "b") as QualityChoice.Menu.Choices
        assertEquals(false, m.autoSelected)
        assertEquals(listOf(false, true), m.options.map { it.selected })
        // Piste choisie disparue (autre flux) : retour à Auto.
        assertTrue((QualityChoice.menu(listOf(t("a", 1080, 5000), t("c", 720, 2500)), null, "b") as QualityChoice.Menu.Choices).autoSelected)
    }

    @Test fun memeHauteur_debitAjoute() {
        val m = QualityChoice.menu(listOf(t("a", 1080, 6000), t("b", 1080, 3000), t("c", 720, 2000)), null, null) as QualityChoice.Menu.Choices
        assertEquals(listOf("1080p · 6.0 Mb/s", "1080p · 3.0 Mb/s", "720p"), m.options.map { it.label })
    }

    @Test fun uneSeuleQualite_ouAucune_qualiteUnique() {
        assertEquals(QualityChoice.Menu.Single("1080p"), QualityChoice.menu(listOf(t("a", 1080, 0)), null, null))
        assertEquals(QualityChoice.Menu.Single(null), QualityChoice.menu(emptyList(), null, null))
        // Deux pistes identiques = une seule qualité.
        assertEquals(QualityChoice.Menu.Single("720p"), QualityChoice.menu(listOf(t("a", 720, 2000), t("b", 720, 2000)), null, null))
        // Pistes sans aucune information : rien à proposer.
        assertEquals(QualityChoice.Menu.Single(null), QualityChoice.menu(listOf(t("a", 0, 0), t("b", 0, 0)), null, null))
    }

    @Test fun repriseDeSession_memeHauteur_sinonEnDessous_jamaisAuDessusDuPlafond() {
        val l = listOf(t("a", 1080, 5000), t("b", 720, 2500), t("c", 480, 1000))
        assertEquals("b", QualityChoice.matchHeight(l, 720, null)?.id)
        assertEquals("b", QualityChoice.matchHeight(l, 900, null)?.id)
        assertEquals("b", QualityChoice.matchHeight(l, 1080, 720)?.id)
        assertNull(QualityChoice.matchHeight(l, 360, null))
        assertNull(QualityChoice.matchHeight(listOf(t("a", 1080, 5000)), 1080, null))
    }
}

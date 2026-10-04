package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.adaptive.Tier
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPlanTest {
    private fun cat(id: String, name: String, enabled: Boolean = true, position: Int = 0, locked: Boolean = false) =
        CategoryEntity(providerId = 1, kind = "LIVE", remoteId = id, name = name, locked = locked, enabled = enabled, position = position)

    @Test fun strategie_toutActif_globale() = assertEquals(SyncStrategy.GLOBAL, CatalogPlan.strategy(879, 879, 1))
    @Test fun strategie_peuActif_parCategorie() = assertEquals(SyncStrategy.PER_CATEGORY, CatalogPlan.strategy(879, 100, 1))
    @Test fun strategie_majoriteActive_globaleFiltree() = assertEquals(SyncStrategy.GLOBAL_FILTERED, CatalogPlan.strategy(879, 600, 1))
    @Test fun strategie_serveurSansFiltre_globaleFiltree() = assertEquals(SyncStrategy.GLOBAL_FILTERED, CatalogPlan.strategy(879, 10, 0))
    @Test fun strategie_capaciteInconnue_essaieParCategorie() = assertEquals(SyncStrategy.PER_CATEGORY, CatalogPlan.strategy(879, 10, -1))
    @Test fun strategie_seuilExactDeQuarantePourCent_globaleFiltree() = assertEquals(SyncStrategy.GLOBAL_FILTERED, CatalogPlan.strategy(100, 40, 1))
    @Test fun parallelisme_selonLeNiveau() { assertEquals(2, CatalogPlan.parallelism(Tier.LOW)); assertEquals(3, CatalogPlan.parallelism(Tier.MID)); assertEquals(4, CatalogPlan.parallelism(Tier.HIGH)) }

    @Test fun fusion_conserveActivationOrdreEtVerrou() {
        val m = CatalogPlan.merge(listOf(cat("1", "A"), cat("2", "B")), listOf(cat("1", "A", enabled = false, position = 7, locked = true)), emptySet())
        val one = m.first { it.remoteId == "1" }
        assertFalse(one.enabled); assertEquals(7, one.position); assertTrue(one.locked)
    }
    @Test fun fusion_nouvelleCategorie_estActive_sansFiltreDeLangue() = assertTrue(CatalogPlan.merge(listOf(cat("9", "FR| FILMS")), emptyList(), emptySet()).single().enabled)
    @Test fun fusion_nouvelleCategorieDeLangueExclue_estDesactivee() {
        val m = CatalogPlan.merge(listOf(cat("1", "FR| FILMS"), cat("2", "DE| FILME"), cat("3", "NETFLIX MOVIES"), cat("4", "TOP MOVIES")), emptyList(), setOf("fr"))
        assertEquals(listOf(true, false, true, true), m.map { it.enabled })
    }
    @Test fun fusion_inconnueExclueSurDemande() = assertFalse(CatalogPlan.merge(listOf(cat("4", "TOP MOVIES")), emptyList(), setOf("fr"), includeUnknown = false).single().enabled)
    @Test fun fusion_multiExclueSurDemande() = assertFalse(CatalogPlan.merge(listOf(cat("3", "NETFLIX MOVIES")), emptyList(), setOf("fr"), includeMulti = false).single().enabled)
    @Test fun fusion_ladesactivationUtilisateurSurvitALaResynchro() {
        val m = CatalogPlan.merge(listOf(cat("1", "FR| FILMS")), listOf(cat("1", "FR| FILMS", enabled = false)), setOf("fr"))
        assertFalse(m.single().enabled)
    }

    @Test fun langue_regle_enCasDeDouteOnAffiche() {
        assertTrue(LanguageFilter.allows("", setOf("fr"))); assertTrue(LanguageFilter.allows("MULTI", setOf("fr"))); assertTrue(LanguageFilter.allows("fr", setOf("fr")))
        assertFalse(LanguageFilter.allows("de", setOf("fr"))); assertTrue(LanguageFilter.allows("de", emptySet()))
    }
}

package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryCount
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryManagerTest {
    private fun cat(id: String, name: String, enabled: Boolean = true) = CategoryEntity(providerId = 1, kind = "LIVE", remoteId = id, name = name, enabled = enabled)
    private val cats = listOf(cat("1", "FR| Généralistes"), cat("2", "AR| Sport"), cat("3", "4K| Événements", enabled = false), cat("4", "AFRI Canal+"))
    private val counts = listOf(CategoryCount("1", 142), CategoryCount("2", 88), CategoryCount("4", 123))

    @Test fun lignes_nomDuFournisseurEtBadge() { val r = CategoryManager.rows(cats, counts, "").first(); assertEquals("FR| Généralistes", r.label); assertEquals("FR", r.badge); assertEquals(142, r.count) }
    @Test fun lignes_categorieDesactivee_sansCompteur() = assertNull(CategoryManager.rows(cats, counts, "").first { it.remoteId == "3" }.count)
    @Test fun lignes_regionAfricaine_badgeAFR() = assertEquals("AFR", CategoryManager.rows(cats, counts, "").first { it.remoteId == "4" }.badge)
    @Test fun filtre_parNom() = assertEquals(listOf("2"), CategoryManager.rows(cats, counts, "sport").map { it.remoteId })
    @Test fun filtre_parBadgeEtLangue() { assertEquals(listOf("1"), CategoryManager.rows(cats, counts, "FR").map { it.remoteId }); assertEquals(listOf("2"), CategoryManager.rows(cats, counts, "ar").map { it.remoteId }) }
    @Test fun filtreCourt_motEntier() { assertEquals(true, CategoryManager.matches("FR| SPORT", "fr")); assertEquals(false, CategoryManager.matches("AFRICA SPORT", "fr")); assertEquals(true, CategoryManager.matches("AFRICA SPORT", "afric")) }
    @Test fun filtre_vide_toutAfficher() = assertEquals(4, CategoryManager.rows(cats, counts, "  ").size)
}

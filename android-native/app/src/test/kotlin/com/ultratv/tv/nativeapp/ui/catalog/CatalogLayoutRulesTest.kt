package com.ultratv.tv.nativeapp.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLayoutRulesTest {
    @Test fun colonnes_autoEtExplicites() {
        assertEquals(5, CatalogLayoutRules.columns(0, split = true, touch = false, touchColumns = 3))
        assertEquals(7, CatalogLayoutRules.columns(0, split = false, touch = false, touchColumns = 3))
        assertEquals(4, CatalogLayoutRules.columns(4, split = true, touch = false, touchColumns = 3))
        assertEquals(6, CatalogLayoutRules.columns(6, split = false, touch = false, touchColumns = 3))
        // Tactile : toujours adaptatif, jamais le réglage TV.
        assertEquals(3, CatalogLayoutRules.columns(6, split = false, touch = true, touchColumns = 3))
        // Valeur invalide mémorisée : retombe sur l'automatique.
        assertEquals(5, CatalogLayoutRules.columns(9, split = true, touch = false, touchColumns = 3))
    }

    @Test fun rotationDesChoix_boucle() {
        assertEquals(4, CatalogLayoutRules.next(CatalogLayoutRules.COLUMN_CHOICES, 0))
        assertEquals(0, CatalogLayoutRules.next(CatalogLayoutRules.COLUMN_CHOICES, 6))
        assertEquals(CatalogFilter.Sort.RECENT, CatalogLayoutRules.next(CatalogFilter.Sort.entries, CatalogFilter.Sort.PROVIDER))
        assertEquals(CatalogFilter.Sort.PROVIDER, CatalogLayoutRules.next(CatalogFilter.Sort.entries, CatalogFilter.Sort.RATING))
        assertEquals(CatalogLayout.ROWS, CatalogLayoutRules.next(CatalogLayout.entries, CatalogLayout.SPLIT))
    }

    @Test fun premiereColonne_pourLeFocusVersLaListe() {
        assertTrue(CatalogLayoutRules.isFirstColumn(0, 5))
        assertTrue(CatalogLayoutRules.isFirstColumn(10, 5))
        assertTrue(!CatalogLayoutRules.isFirstColumn(4, 5))
    }

    @Test fun preferences_valeursInconnuesRetombentSurLesDefauts() {
        assertEquals(CategorySource.PROVIDER, CategorySource.parse(null))
        assertEquals(CategorySource.PROVIDER, CategorySource.parse("provider"))
        assertEquals(CatalogLayout.SPLIT, CatalogLayout.parse("zzz"))
        assertEquals(CatalogLayout.ROWS, CatalogLayout.parse("rows"))
    }

    @Test fun triExistants_codesStables() {
        assertEquals(listOf(0, 1, 2), CatalogFilter.Sort.entries.map { it.code })
        assertTrue(!CatalogFilter.DEFAULT.narrows)
        assertTrue(CatalogFilter(sort = CatalogFilter.Sort.RATING).narrows)
    }
}

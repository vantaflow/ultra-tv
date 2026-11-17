package com.ultratv.tv.nativeapp.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSourceChoiceTest {
    private val xt = Triple(1L, "STRONG", "XTREAM" to true)
    private val plex = Triple(2L, "JDM Plex", "PLEX" to false)
    private val m3u = Triple(3L, "Liste", "M3U" to false)

    @Test fun iptvSeul_aucunFiltre() = assertTrue(CatalogSourceChoice.choices(listOf(xt, m3u)).isEmpty())

    @Test fun avecPlex_activeEtBibliotheques() =
        assertEquals(listOf(1L to "STRONG", 2L to "JDM Plex"), CatalogSourceChoice.choices(listOf(xt, m3u, plex)))

    @Test fun resolve_choixValide_sinonActive() {
        val ps = listOf(1L to true, 2L to false)
        assertEquals(2L, CatalogSourceChoice.resolve(ps, 2L))
        assertEquals(1L, CatalogSourceChoice.resolve(ps, null))
        assertEquals(1L, CatalogSourceChoice.resolve(ps, 99L))   // source supprimée
        assertEquals(null, CatalogSourceChoice.resolve(emptyList(), 2L))
    }
}

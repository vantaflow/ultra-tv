package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchDedupTest {
    private fun movie(name: String, poster: String? = null, year: Int? = null, id: Long = 0) =
        MovieEntity(id = id, providerId = 1, remoteId = "r$id", name = name, poster = poster, categoryId = null, streamUrl = "u", container = null, year = year, rating = null, plot = null)

    @Test fun key_prefixesLangueQualiteEtAnnee_memeOeuvre() {
        val keys = listOf("FR - Inception (2010)", "|FR| Inception (2010)", "[EN] Inception (2010)", "Inception 4K (2010)", "Inception MULTI (2010)", "FR| inception (2010) FHD")
            .map { SearchDedup.key(it, null) }.toSet()
        assertEquals(1, keys.size)
    }
    @Test fun key_accentsEtCasseIgnores() = assertEquals(SearchDedup.key("Amélie", 2001), SearchDedup.key("AMELIE", 2001))
    @Test fun key_anneesDifferentes_oeuvresDifferentes() = assertNotEquals(SearchDedup.key("Dune (1984)", null), SearchDedup.key("Dune (2021)", null))
    @Test fun key_etiquetteFinale_multiVostfr() = assertEquals(SearchDedup.key("Avatar", 2009), SearchDedup.key("Avatar VOSTFR", 2009))
    @Test fun dedupe_gardeCelleAvecAffiche_etLOrdre() {
        val out = SearchDedup.movies(listOf(
            movie("FR - Inception (2010)", null, id = 1), movie("Matrix", "p", id = 2),
            movie("|FR| Inception (2010)", "http://p", id = 3), movie("Inception 4K (2010)", "http://q", id = 4),
        ))
        assertEquals(listOf(3L, 2L), out.map { it.id }.sortedDescending())
        assertEquals(2, out.size); assertEquals(3L, out.first { it.name.contains("Inception") }.id)
    }
    @Test fun dedupe_sansAffiche_garderLaPremiere() {
        val out = SearchDedup.movies(listOf(movie("A (2000)", id = 1), movie("A (2000) HD", id = 2)))
        assertEquals(listOf(1L), out.map { it.id })
    }
    @Test fun dedupe_titreSeulDeTag_nEstPasVide() = assertEquals("hd|0", SearchDedup.key("HD", null))
}

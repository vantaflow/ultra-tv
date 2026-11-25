package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.data.tmdb.PersonSearch.Occ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonSearchTest {
    private val movie = Occ(1, 'M', "10")
    private val series = Occ(1, 'S', "20")

    private fun index() = PersonSearch.build(listOf(
        movie to "Tom Hanks, Tim Allen, N/A",
        movie to "Tom Hanks | Penélope Cruz",
        series to "Tom Hardy, Hanks Junior",
        series to "Penelope Cruz",
    ))

    @Test fun names_separeEtNettoie() {
        assertEquals(listOf("Tom Hanks", "Tim Allen"), PersonSearch.names("Tom Hanks, Tim Allen, N/A, ,"))
        assertEquals(emptyList<String>(), PersonSearch.names(null))
        assertEquals(emptyList<String>(), PersonSearch.names("None"))
    }

    @Test fun matches_ignoreCasseEtAccents() {
        assertTrue(PersonSearch.matches("Penélope Cruz", "PENELOPE"))
        assertTrue(PersonSearch.matches("Penélope Cruz", "cruz pen"))
        assertTrue(PersonSearch.matches("Tom Hanks", "tom han"))
        assertFalse(PersonSearch.matches("Tom Hanks", "tim"))
    }

    @Test fun matches_saisieTropCourteOuVide() {
        assertFalse(PersonSearch.matches("Tom Hanks", "to"))
        assertFalse(PersonSearch.matches("Tom Hanks", "  "))
    }

    @Test fun search_classeParQualitePuisNombreDeTitres() {
        val hits = PersonSearch.search(index(), "hanks", 10)
        // « Tom Hanks » (2 titres) avant « Hanks Junior » (commence par la saisie : mieux classé).
        assertEquals(listOf("Hanks Junior", "Tom Hanks"), hits.map { it.name })
        assertEquals(2, hits.first { it.name == "Tom Hanks" }.titles)
    }

    @Test fun search_nomExactEnPremier() {
        val hits = PersonSearch.search(index(), "tom hanks", 10)
        assertEquals("Tom Hanks", hits.first().name)
        assertEquals(3, hits.first().score)
    }

    @Test fun search_fusionneAccentsEtNeCompteQueLesCitationsVisibles() {
        val all = PersonSearch.search(index(), "penelope", 10)
        assertEquals(1, all.size)
        assertEquals(2, all[0].titles)
        // Catégorie des séries masquée (profil Enfants) : une seule citation reste.
        val kids = PersonSearch.search(index(), "penelope", 10) { it.kind != 'S' }
        assertEquals(1, kids[0].titles)
        // Toutes les citations masquées : personne n'est rendu.
        assertTrue(PersonSearch.search(index(), "penelope", 10) { false }.isEmpty())
    }

    @Test fun search_respecteLaLimite() {
        assertEquals(1, PersonSearch.search(index(), "tom", 1).size)
    }

    @Test fun merge_localEnPremier_sansDoublonSurAccentsEtCasse() {
        val out = PersonSearch.merge(listOf("Penélope Cruz"), listOf("PENELOPE CRUZ", "Penelope Ann Miller"), 10)
        assertEquals(listOf("Penélope Cruz", "Penelope Ann Miller"), out)
    }

    @Test fun merge_plafonne() {
        assertEquals(listOf("A One", "B Two"), PersonSearch.merge(listOf("A One", "B Two"), listOf("C Three"), 2))
    }

    @Test fun results_litLaReponseTmdb() {
        val json = """{"results":[{"id":31,"name":"Tom Hanks"},{"id":0,"name":"X"},{"id":5,"name":" "},{"id":7,"name":"Tim Allen"}]}"""
        assertEquals(listOf(31 to "Tom Hanks", 7 to "Tim Allen"), PersonSearch.results(json))
        assertEquals(emptyList<Pair<Int, String>>(), PersonSearch.results("nope"))
    }
}

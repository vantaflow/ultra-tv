package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.tmdb.PersonCredit
import com.ultratv.tv.nativeapp.data.tmdb.TmdbParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedMoviesTest {
    private data class M(val id: Long, val title: String, val year: Int?, val poster: Boolean = true)

    private val catalog = listOf(
        M(1, "Interstellar", 2014), M(2, "Inception", 2010), M(3, "Le Prestige", 2006), M(4, "Gravity", 2013),
        M(5, "Gravity", 1990), M(6, "Dunkerque", 2017, poster = false), M(7, "Intersection", 2014),
    )
    private fun credit(title: String, year: Int?, original: String? = null) = PersonCredit(false, title, original, year)

    private fun run(credits: List<PersonCredit>, seed: M = catalog[0], limit: Int = 10, searched: MutableList<String>? = null) = runBlocking {
        RelatedMovies.match(credits, seed.id, seed.title, limit, { it.id }, { it.title }, { it.year }, accept = { it.poster }) { q ->
            searched?.add(q)
            catalog.filter { it.title.equals(q, ignoreCase = true) }
        }
    }

    @Test fun `titre accentue ou en majuscules rapproche du catalogue, ordre de TMDB conserve`() {
        val r = run(listOf(credit("LE PRESTIGE", 2006), credit("Inception", 2010), credit("Gravity", 2013)))
        assertEquals(listOf(3L, 2L, 4L), r.map { it.id })
    }

    @Test fun `annee a plus ou moins un an acceptee, au-dela refusee`() {
        assertEquals(listOf(2L), run(listOf(credit("Inception", 2011))).map { it.id })
        assertTrue(run(listOf(credit("Inception", 2015))).isEmpty())
    }

    @Test fun `deux films de meme titre, l'annee departage`() {
        assertEquals(listOf(5L), run(listOf(credit("Gravity", 1990))).map { it.id })
    }

    @Test fun `le film de depart et ses doublons de titre sont exclus`() {
        assertTrue(run(listOf(credit("Interstellar", 2014))).isEmpty())
        val autre = M(9, "interstellar", 2014)
        val r = runBlocking { RelatedMovies.match(listOf(credit("Interstellar", 2014)), 1L, "Interstellar", 5, { it.id }, { it.title }, { it.year }) { listOf(autre) } }
        assertTrue(r.isEmpty())
    }

    @Test fun `sans doublon meme si TMDB repete un titre, et limite respectee`() {
        val r = run(listOf(credit("Inception", 2010), credit("Inception", 2010, "Inception"), credit("Le Prestige", 2006), credit("Gravity", 2013)), limit = 2)
        assertEquals(listOf(2L, 3L), r.map { it.id })
    }

    @Test fun `film sans affiche ecarte, titre original utilise en repli`() {
        assertTrue(run(listOf(credit("Dunkerque", 2017))).isEmpty())
        val searched = mutableListOf<String>()
        val r = run(listOf(credit("Gravité", 2013, original = "Gravity")), searched = searched)
        assertEquals(listOf("Gravité", "Gravity"), searched)
        assertEquals(listOf(4L), r.map { it.id })
    }

    @Test fun `titre original TMDB rapproche un titre du catalogue en version originale`() {
        val r = run(listOf(credit("Le Prestige FR", 2006, original = "Le Prestige")))
        assertEquals(listOf(3L), r.map { it.id })
    }

    @Test fun `une erreur de recherche est ignoree pour ce titre`() {
        val r = runBlocking {
            RelatedMovies.match(listOf(credit("Boom", 2000), credit("Inception", 2010)), 1L, "X", 5, { it.id }, { it.title }, { it.year }) { q ->
                if (q == "Boom") error("fts") else catalog.filter { it.title == q }
            }
        }
        assertEquals(listOf(2L), r.map { it.id })
    }

    @Test fun `repli local - TMDB d'abord puis le classement local sans doublon`() {
        val tmdb = listOf(M(2, "Inception", 2010), M(3, "Le Prestige", 2006))
        val local = listOf(M(3, "Le Prestige", 2006), M(4, "Gravity", 2013), M(7, "Intersection", 2014))
        assertEquals(listOf(2L, 3L, 4L), RelatedMovies.complete(tmdb, local, 3) { it.id }.map { it.id })
        assertEquals(listOf(4L, 3L), RelatedMovies.complete(emptyList(), listOf(local[1], local[0]), 5) { it.id }.map { it.id })
        assertTrue(RelatedMovies.complete(emptyList<M>(), emptyList(), 5) { it.id }.isEmpty())
    }

    @Test fun `analyse de la reponse TMDB recommendations`() {
        val json = """{"results":[{"id":1,"title":"Inception","original_title":"Inception","release_date":"2010-07-16"},
            {"id":2,"title":"Le Prestige","original_title":"The Prestige","release_date":"2006-10-17"},
            {"id":3,"title":"Inception","release_date":"2010-01-01"},{"id":4,"title":"","release_date":"2000-01-01"}]}"""
        val l = TmdbParser.related(json, tv = false)
        assertEquals(listOf("Inception", "Le Prestige"), l.map { it.title })
        assertEquals("The Prestige", l[1].originalTitle); assertEquals(2006, l[1].year)
        assertTrue(TmdbParser.related("pas du json", false).isEmpty())
        assertEquals("Dark", TmdbParser.related("""{"results":[{"name":"Dark","first_air_date":"2017-12-01"}]}""", true).single().title)
    }
}

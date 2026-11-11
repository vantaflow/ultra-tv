package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarMoviesTest {
    private fun f(genres: String, cast: String = "", year: Int? = null) =
        SimilarMovies.Facts(SimilarMovies.genres(genres), SimilarMovies.people(cast, 6), null, year)

    private val seed = SimilarMovies.Facts(
        SimilarMovies.genres("Science-Fiction, Aventure, Drame"), listOf("Matthew McConaughey", "Anne Hathaway"), "Christopher Nolan", 2014,
    )

    @Test fun `un seul genre large en commun ne suffit plus`() {
        val r = SimilarMovies.rank("Interstellar", seed, listOf("Comédie d'action" to f("Action, Comédie, Aventure")), 10) { it }
        assertTrue(r.isEmpty())
    }

    @Test fun `deux genres en commun, mieux classe s'il en partage plus`() {
        val r = SimilarMovies.rank("Interstellar", seed, listOf(
            "Gravity" to f("Science-Fiction, Drame", year = 2013),
            "Seul sur Mars" to f("Science Fiction, Aventure, Drame", year = 2015),
        ), 10) { it }
        assertEquals(listOf("Seul sur Mars", "Gravity"), r)
    }

    @Test fun `meme realisateur ou acteur commun garde le film meme hors genres`() {
        val r = SimilarMovies.rank("Interstellar", seed, listOf(
            "Oppenheimer" to f("Histoire, Biographie", cast = "Cillian Murphy, Christopher Nolan"),
            "Le Loup de Wall Street" to f("Comédie, Crime", cast = "Leonardo DiCaprio, Matthew McConaughey"),
            "Autre" to f("Comédie, Romance"),
        ), 10) { it }
        assertEquals(setOf("Oppenheimer", "Le Loup de Wall Street"), r.toSet())
    }

    @Test fun `le film lui-meme (autre version) est ecarte`() {
        val r = SimilarMovies.rank("Interstellar", seed, listOf("Interstellar" to f("Science-Fiction, Aventure, Drame")), 10) { it }
        assertTrue(r.isEmpty())
    }

    @Test fun `genres normalises sans accents ni tirets`() {
        assertEquals(setOf("science fiction", "comedie"), SimilarMovies.genres("Science-Fiction / Comédie"))
    }
}

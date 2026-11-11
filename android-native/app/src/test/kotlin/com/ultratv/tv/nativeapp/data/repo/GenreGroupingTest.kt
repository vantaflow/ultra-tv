package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreGroupingTest {
    @Test fun texteXtream_plusieursLangues() {
        assertEquals(listOf(Genre.ACTION, Genre.ADVENTURE, Genre.SCIFI), GenreGrouping.fromText("Action, Adventure, Sci-Fi"))
        assertEquals(listOf(Genre.COMEDY, Genre.DRAMA), GenreGrouping.fromText("Comédie / Drame"))
        assertEquals(listOf(Genre.HORROR, Genre.THRILLER), GenreGrouping.fromText("Terror | Suspense"))
        assertEquals(listOf(Genre.DOCUMENTARY), GenreGrouping.fromText("DOCUMENTAIRE"))
        assertEquals(listOf(Genre.SCIFI, Genre.FANTASY), GenreGrouping.fromText("Science-Fiction & Fantastique"))
        assertEquals(listOf(Genre.SCIFI), GenreGrouping.fromText("Ciencia ficción"))
        assertEquals(listOf(Genre.ACTION, Genre.DRAMA), GenreGrouping.fromText("أكشن، دراما".replace('،', ',')))
    }

    @Test fun scienceFiction_nEstPasUneFantasy() {
        assertEquals(listOf(Genre.SCIFI), GenreGrouping.fromText("خيال علمي"))
        assertEquals(listOf(Genre.FANTASY), GenreGrouping.fromText("Fantasy"))
    }

    @Test fun motEntier_pasDeFauxPositif() {
        assertTrue(GenreGrouping.fromText("Interaction").isEmpty())
        assertTrue(GenreGrouping.fromText("Warner").isEmpty())
        assertTrue(GenreGrouping.fromText("").isEmpty())
        assertTrue(GenreGrouping.fromText(null).isEmpty())
    }

    @Test fun identifiantsTmdb_filmsEtSeries() {
        assertEquals(listOf(Genre.ACTION, Genre.THRILLER), GenreGrouping.fromTmdb("28, 53"))
        assertEquals(listOf(Genre.ACTION, Genre.ADVENTURE, Genre.DRAMA), GenreGrouping.fromTmdb("10759,18"))
        assertEquals(listOf(Genre.SCIFI, Genre.FANTASY), GenreGrouping.fromTmdb("10765"))
        assertTrue(GenreGrouping.fromTmdb("99999,abc").isEmpty())
    }

    @Test fun anime_animationJaponaiseOuMention() {
        val r = GenreGrouping.classify("16,28", "ja", null)
        assertEquals(listOf(Genre.ANIME, Genre.ANIMATION, Genre.ACTION), r)
        assertEquals(listOf(Genre.ANIMATION), GenreGrouping.classify("16", "en", null))
        assertEquals(listOf(Genre.ANIME), GenreGrouping.classify(null, null, "Anime"))
    }

    @Test fun classify_neRetourneJamaisVide_etBorneLeNombre() {
        assertEquals(listOf(Genre.OTHER), GenreGrouping.classify(null, null, null, "Inconnu"))
        val r = GenreGrouping.classify("28,12,35,18,53", null, null)
        assertEquals(GenreGrouping.MAX_PER_TITLE, r.size)
        // Priorité à TMDB, puis aux textes du fournisseur, sans doublon.
        assertEquals(listOf(Genre.COMEDY, Genre.HORROR), GenreGrouping.classify("35", null, "Comedy, Horror"))
    }

    @Test fun tri_plusFourniDabord_autresEnDernier() {
        val l = listOf("OTHER" to 5000, "DRAMA" to 1887, "ACTION" to 1665, "COMEDY" to 1763, "ANIME" to 90)
        assertEquals(listOf("DRAMA", "COMEDY", "ACTION", "ANIME", "OTHER"), GenreGrouping.sortedByCount(l, { it.first }, { it.second }).map { it.first })
    }
}

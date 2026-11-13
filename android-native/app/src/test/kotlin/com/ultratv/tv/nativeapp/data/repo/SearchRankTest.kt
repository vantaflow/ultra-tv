package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankTest {
    private data class T(val title: String, val year: Int? = null, val rating: Double? = null)

    private fun titles(xs: List<T>) = xs.map { it.title }

    @Test fun titleKeyDropsLeadingArticlesAccentsAndPunctuation() {
        assertEquals("office", SearchRank.titleKey("The Office"))
        assertEquals("odyssee", SearchRank.titleKey("L'Odyssée"))
        assertEquals("nuits courtes", SearchRank.titleKey("Les Nuits Courtes"))
        assertEquals("casa de papel", SearchRank.titleKey("La Casa de Papel"))
        assertEquals("boot", SearchRank.titleKey("Das Boot"))
        assertEquals("cette nuit la", SearchRank.titleKey("Cette Nuit-là"))
        // « À » n'est pas un article ; un titre réduit à un article reste tel quel.
        assertEquals("a bout de souffle", SearchRank.titleKey("À bout de souffle"))
        assertEquals("les", SearchRank.titleKey("Les"))
        assertEquals("beautiful mind", SearchRank.titleKey("A Beautiful Mind"))
    }

    @Test fun initialIsUppercaseLetterOrHash() {
        assertEquals('O', SearchRank.initial("The Office"))
        assertEquals('E', SearchRank.initial("été"))
        assertEquals('#', SearchRank.initial("24"))
        assertEquals('#', SearchRank.initial("!!!"))
    }

    @Test fun relevanceLevels() {
        assertEquals(4, SearchRank.relevance("nuit blanche", "Nuit Blanche"))
        assertEquals(4, SearchRank.relevance("office", "The Office"))
        assertEquals(3, SearchRank.relevance("nuit", "Nuit Polaire"))
        assertEquals(2, SearchRank.relevance("nuit", "Avant la Nuit"))
        assertEquals(1, SearchRank.relevance("nuit", "Minuit Pile"))
        assertEquals(0, SearchRank.relevance("  ", "Minuit Pile"))
    }

    @Test fun relevanceOrderThenNewestFirst() {
        val xs = listOf(T("Minuit Pile", 2023), T("Avant la Nuit", 2014), T("Nuit Polaire", 2020), T("Nuit", 2001), T("Nuit Blanche", 2021))
        val out = SearchRank.order(xs, "nuit", SearchSort.RELEVANCE, { it.title }, { it.year }, { it.rating })
        assertEquals(listOf("Nuit", "Nuit Blanche", "Nuit Polaire", "Avant la Nuit", "Minuit Pile"), titles(out))
    }

    @Test fun alphabeticalIgnoresArticlesAndPutsDigitsFirst() {
        val xs = listOf(T("The Office"), T("Les Nuits Courtes"), T("24"), T("Avant la Nuit"), T("Ébène"), T("Minuit Pile"))
        val out = SearchRank.order(xs, "", SearchSort.TITLE, { it.title }, { it.year }, { it.rating })
        assertEquals(listOf("24", "Avant la Nuit", "Ébène", "Minuit Pile", "Les Nuits Courtes", "The Office"), titles(out))
    }

    @Test fun yearAndRatingSorts() {
        val xs = listOf(T("B", 2010, 7.0), T("A", null, 0.0), T("C", 2020, 11.0), T("D", 2015, 8.5))
        assertEquals(listOf("C", "D", "B", "A"), titles(SearchRank.order(xs, "", SearchSort.YEAR, { it.title }, { it.year }, { it.rating })))
        // 0 et 11 ne sont pas des notes valides : en fin de liste, par titre.
        assertEquals(listOf("D", "B", "A", "C"), titles(SearchRank.order(xs, "", SearchSort.RATING, { it.title }, { it.year }, { it.rating })))
    }

    @Test fun letterIndexPointsToFirstTitleOfEachLetter() {
        val sorted = listOf(T("24"), T("Avant"), T("Avril"), T("The Office"), T("Orage"))
        val idx = SearchRank.letterIndex(sorted) { it.title }
        assertEquals(mapOf('#' to 0, 'A' to 1, 'O' to 3), idx)
    }

    @Test fun visibleRangeIsClampedAndOneBased() {
        assertEquals(1 to 6, SearchRank.visibleRange(0, 5, 18))
        assertEquals(17 to 18, SearchRank.visibleRange(16, 30, 18))
        assertNull(SearchRank.visibleRange(0, -1, 18))
        assertNull(SearchRank.visibleRange(0, 3, 0))
        assertTrue(SearchRank.visibleRange(5, 2, 18)!!.second >= 6)
    }
}

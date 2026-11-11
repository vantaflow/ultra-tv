package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchTest {
    private val titles = listOf("Breaking Bad", "Bad Boys", "Better Call Saul", "La Casa de Papel", "Le Bureau des légendes", "The Office")

    @Test fun candidateMatchUsesThreeLetterPrefixesWithOr() {
        assertEquals("bre* OR bad*", FuzzySearch.candidateMatch("Brekin bad"))
        assertEquals("leg*", FuzzySearch.candidateMatch("légendez"))
        assertNull(FuzzySearch.candidateMatch("a b"))
    }

    @Test fun typosStillFindTheTitle() {
        assertEquals("Breaking Bad", FuzzySearch.rank("brekin bad", titles, { it }, 3).first())
        assertEquals("Le Bureau des légendes", FuzzySearch.rank("bureau des legendez", titles, { it }, 3).first())
        assertEquals("La Casa de Papel", FuzzySearch.rank("casa de papl", titles, { it }, 3).first())
        assertEquals("Better Call Saul", FuzzySearch.rank("beter call saul", titles, { it }, 3).first())
    }

    @Test fun unrelatedQueryFindsNothing() {
        assertTrue(FuzzySearch.rank("zzzz qqqq", titles, { it }, 5).isEmpty())
        assertTrue(FuzzySearch.rank("matrix", titles, { it }, 5).isEmpty())
    }

    @Test fun transpositionCountsAsOneEdit() {
        assertTrue(FuzzySearch.similarity("ofifce", "office") >= 0.8)
    }
}

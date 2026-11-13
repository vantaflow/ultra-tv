package com.ultratv.tv.nativeapp.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchScopeTest {
    @Test fun rubriqueVersFiltreDuDepot() {
        assertEquals("movie", SearchScope.onlyOf(SearchTab.MOVIES))
        assertEquals("series", SearchScope.onlyOf(SearchTab.SERIES))
        assertNull(SearchScope.onlyOf(null))
        assertNull(SearchScope.onlyOf(SearchTab.ALL))
    }
}

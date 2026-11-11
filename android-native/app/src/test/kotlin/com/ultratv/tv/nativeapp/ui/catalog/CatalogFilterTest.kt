package com.ultratv.tv.nativeapp.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFilterTest {
    @Test fun defaultDoesNotNarrow() {
        assertFalse(CatalogFilter.DEFAULT.narrows)
        assertEquals(0, CatalogFilter.DEFAULT.sortCode)
    }

    @Test fun periodBoundsAndNarrowing() {
        val f = CatalogFilter(period = CatalogFilter.Period.Y2010S, minRating = 7)
        assertEquals(2010, f.yearFrom); assertEquals(2019, f.yearTo)
        assertTrue(f.narrows)
        assertEquals(0, CatalogFilter(period = CatalogFilter.Period.OLDER).yearFrom)
        assertEquals(1989, CatalogFilter(period = CatalogFilter.Period.OLDER).yearTo)
        assertTrue(CatalogFilter(sort = CatalogFilter.Sort.RATING).narrows)
    }
}

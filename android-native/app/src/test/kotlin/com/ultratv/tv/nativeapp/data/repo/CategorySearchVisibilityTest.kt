package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySearchVisibilityTest {
    @Test fun combinesDisabledAndProfileHiddenCategoriesForOnlyTheRequestedProviderAndKind() {
        val result = CategorySearchVisibility.excludedRemoteIds(
            kind = "MOVIE",
            providerId = 3L,
            profileHiddenKeys = setOf(
                "MOVIE:3:42",
                "MOVIE:3:adult",
                "MOVIE:4:other-provider",
                "SERIES:3:series-hidden",
            ),
            disabledRemoteIds = listOf("disabled", "42"),
        )

        assertEquals(listOf("42", "adult", "disabled"), result)
    }

    @Test fun returnsNoExcludedIdsWhenNothingIsHiddenOrDisabled() {
        assertEquals(
            emptyList<String>(),
            CategorySearchVisibility.excludedRemoteIds("SERIES", 9L, emptySet(), emptyList()),
        )
    }

    @Test fun visibleIn_milliersDExclusions_sansParametresSql() {
        val excluded = (0 until 5000).map { "c$it" }.toHashSet()
        val items = listOf("c1" to "a", "keep" to "b", null to "c")
        assertEquals(listOf("b", "c"), items.visibleIn(excluded) { it.first }.map { it.second })
        assertEquals(items, items.visibleIn(emptySet()) { it.first })
    }
}

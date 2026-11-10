package com.ultratv.tv.nativeapp.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroRotationTest {
    @Test fun interleavesSeriesFirstWithoutDuplicates() {
        assertEquals(listOf("s1", "m1", "s2", "m2", "m3"), interleaveHeroes(listOf("s1", "s2"), listOf("m1", "m2", "m3"), 6))
        assertEquals(listOf("s1", "m1", "s2"), interleaveHeroes(listOf("s1", "s2", "s3"), listOf("m1"), 3))
        assertEquals(listOf("m1"), interleaveHeroes(emptyList(), listOf("m1"), 6))
    }
}

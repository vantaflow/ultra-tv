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

class HeroPickTest {
    private data class H(val name: String, val landscape: Boolean)
    private val has = { h: H -> h.landscape }

    @Test fun `seuls les titres avec image paysage sont retenus, alternes serie film`() {
        val ss = listOf(H("s1", true), H("s2", false), H("s3", true))
        val ms = listOf(H("m1", false), H("m2", true), H("m3", true))
        assertEquals(listOf("s1", "m2", "s3", "m3"), pickHeroes(ss, ms, 6, false, has).map { it.name })
    }

    @Test fun `limite respectee`() {
        val ms = (1..10).map { H("m$it", true) }
        assertEquals(6, pickHeroes(emptyList(), ms, 6, false, has).size)
    }

    @Test fun `aucun titre paysage - vide, ou ancien affichage en dernier recours`() {
        val ms = listOf(H("m1", false), H("m2", false))
        assertEquals(emptyList<H>(), pickHeroes(emptyList(), ms, 6, false, has))
        assertEquals(listOf("m1", "m2"), pickHeroes(emptyList(), ms, 6, true, has).map { it.name })
    }

    @Test fun `le dernier recours ne s'applique pas s'il existe un titre paysage`() {
        assertEquals(listOf("m2"), pickHeroes(emptyList(), listOf(H("m1", false), H("m2", true)), 6, true, has).map { it.name })
    }

    @Test fun `resolution TMDB - seulement les films sans paysage, au plus le lot, rien si la selection est complete`() {
        val ms = (1..20).map { H("m$it", it % 2 == 0) }
        assertEquals(listOf("m1", "m3", "m5"), heroesToResolve(ms, 2, 6, 3, has).map { it.name })
        assertEquals(emptyList<H>(), heroesToResolve(ms, 6, 6, 3, has))
        assertEquals(8, heroesToResolve(ms, 0, 6, HERO_RESOLVE_MAX, has).size)
    }
}

package com.ultratv.tv.nativeapp.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowThrottleTest {
    @Test
    fun atMostEvery_premiereToutDeSuite_puisLaPlusRecente() = runTest {
        // 0..9 toutes les 100 ms (comme Room pendant une synchro), limité à une valeur par seconde.
        val got = flow { repeat(10) { emit(it); delay(100) } }.atMostEvery(1_000).toList()
        assertEquals(0, got.first())          // la première, sans attendre
        assertEquals(9, got.last())           // la dernière n'est jamais perdue
        assertEquals(true, got.size <= 3)     // au lieu de 10
    }
}

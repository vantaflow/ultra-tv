package com.ultratv.tv.nativeapp.data.trakt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachedFlagTest {
    private fun TestScope.flag(ttl: Long, now: () -> Long, load: () -> Boolean) =
        CachedFlag(ttl, CoroutineScope(StandardTestDispatcher(testScheduler)), now, load)

    @Test fun get_premierAppel_neLitJamaisSurLeFilAppelant_etRenvoieFaux() = runTest {
        var loads = 0
        val f = flag(30_000, { 0L }) { loads++; true }
        assertFalse(f.get())        // lecture seulement planifiée, pas exécutée ici
        assertEquals(0, loads)
    }

    @Test fun get_apresLaLectureEnFond_renvoieLaValeur() = runTest {
        val f = flag(30_000, { 0L }) { true }
        f.get(); advanceUntilIdle()
        assertTrue(f.get())
    }

    @Test fun get_dansLeTtl_nerelitPas_puisRelitApresLeTtl() = runTest {
        var now = 0L; var loads = 0
        val f = flag(30_000, { now }) { loads++; true }
        f.get(); advanceUntilIdle()
        repeat(100) { now += 100; f.get() }     // 10 s de ticks : aucune relecture
        advanceUntilIdle()
        assertEquals(1, loads)
        now = 31_000; f.get(); advanceUntilIdle()
        assertEquals(2, loads)
    }

    @Test fun get_appelsRepetesPendantLaLecture_neLancentQuUneLecture() = runTest {
        var loads = 0
        val f = flag(30_000, { 0L }) { loads++; true }
        repeat(50) { f.get() }
        advanceUntilIdle()
        assertEquals(1, loads)
    }

    @Test fun get_lectureEnEchec_donneFauxSansPropager() = runTest {
        val f = flag(30_000, { 0L }) { error("keystore") }
        f.get(); advanceUntilIdle()
        assertFalse(f.get())
    }
}

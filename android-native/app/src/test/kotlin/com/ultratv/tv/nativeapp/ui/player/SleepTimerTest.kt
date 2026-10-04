package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.sleep.SleepChoice
import com.ultratv.tv.nativeapp.ui.player.sleep.SleepPhase
import com.ultratv.tv.nativeapp.ui.player.sleep.SleepTimer
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {
    private val min = 60_000L

    @Test fun inactive_par_defaut() = assertEquals(SleepPhase.IDLE, SleepTimer().phase(0))

    @Test fun trente_minutes_avertit_a_une_minute_puis_expire() {
        val t = SleepTimer(); t.arm(SleepChoice.Minutes(30), 0)
        assertEquals(SleepPhase.RUNNING, t.phase(28 * min))
        assertEquals(SleepPhase.WARNING, t.phase(29 * min))
        assertEquals(30, t.secondsLeft(29 * min + 30_000))
        assertEquals(SleepPhase.EXPIRED, t.phase(30 * min))
    }

    @Test fun fin_de_programme_vise_lheure_de_fin() {
        val t = SleepTimer(); t.arm(SleepChoice.UntilMs(10 * min), 2 * min)
        assertEquals(10 * min, t.deadlineMs)
        assertEquals(SleepPhase.WARNING, t.phase(9 * min + 1))
    }

    @Test fun confirmer_relance_la_meme_duree_sauf_fin_de_programme() {
        val t = SleepTimer(); t.arm(SleepChoice.Minutes(60), 0)
        t.confirmPresence(59 * min)
        assertEquals(119 * min, t.deadlineMs)
        t.arm(SleepChoice.UntilMs(5 * min), 0); t.confirmPresence(4 * min)
        assertEquals(SleepPhase.IDLE, t.phase(4 * min))
    }

    @Test fun annuler_remet_a_idle() {
        val t = SleepTimer(); t.arm(SleepChoice.Minutes(90), 0); t.cancel()
        assertEquals(SleepPhase.IDLE, t.phase(100 * min)); assertEquals(0, t.secondsLeft(0))
    }
}

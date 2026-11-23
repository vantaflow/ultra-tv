package com.ultratv.tv.nativeapp.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHistoryPolicyTest {
    @Test fun chaineDuDirectQuiJoue_arme() = assertTrue(LiveHistoryPolicy.shouldArm("1:42", playing = true))
    @Test fun chargementOuZapEnCours_pasArme() = assertFalse(LiveHistoryPolicy.shouldArm("1:42", playing = false))
    @Test fun horsDirect_pasArme() = assertFalse(LiveHistoryPolicy.shouldArm(null, playing = true))
    @Test fun seuilDeCinqSecondes() = assertEquals(5_000L, LiveHistoryPolicy.MIN_WATCH_MS)
}

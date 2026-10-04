package com.ultratv.tv.nativeapp.ui.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovieDurationTest {
    @Test fun horloge_devientHeuresMinutes() = assertEquals("1 h 54", movieDuration("01:54:00"))
    @Test fun minutesBrutes() { assertEquals("1 h 52", movieDuration("112")); assertEquals("45 min", movieDuration("45")) }
    @Test fun absentOuNul_estMasque() { assertNull(movieDuration(null)); assertNull(movieDuration("")); assertNull(movieDuration("0")); assertNull(movieDuration("00:00:00")) }
    @Test fun formatClock_reprise() { assertEquals("1:12:40", formatClock(4_360_000)); assertEquals("5:03", formatClock(303_000)) }
}

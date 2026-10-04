package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.EpgTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeshiftChannelsTest {
    @Test fun parse_chaineDecalee() { assertEquals("TF1" to 1, TimeshiftChannels.parse("TF1 +1")); assertEquals("M6" to 2, TimeshiftChannels.parse("M6+2")) }
    @Test fun parse_chaineOrdinaire_null() { assertNull(TimeshiftChannels.parse("CANAL+")); assertNull(TimeshiftChannels.parse("TF1")); assertNull(TimeshiftChannels.parse("+1")) }
    @Test fun plan_rattacheAuCanalPorteurDeLaBase() {
        val chans = listOf(EpgTitle(1, "TF1", "TF1.fr"), EpgTitle(2, "TF1", "TF1.fr"), EpgTitle(3, "TF1 +1", null), EpgTitle(4, "W9 +1", null))
        assertEquals(mapOf(1L to listOf(3L to 1)), TimeshiftChannels.plan(chans, mapOf("TF1.fr" to 1L)))
    }
}

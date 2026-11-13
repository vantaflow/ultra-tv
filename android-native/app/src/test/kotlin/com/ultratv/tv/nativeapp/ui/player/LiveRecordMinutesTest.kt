package com.ultratv.tv.nativeapp.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRecordMinutesTest {
    private val now = 1_000_000_000L
    @Test fun finDuProgramme_plusMarge() = assertEquals(55, liveRecordMinutes(now, now + 45 * 60_000L))
    @Test fun programmeQuiFinit_auMoins15() = assertEquals(15, liveRecordMinutes(now, now + 60_000L))
    @Test fun programmeTresLong_plafonne() = assertEquals(360, liveRecordMinutes(now, now + 10 * 3_600_000L))
    @Test fun sansGuide_4h() { assertEquals(240, liveRecordMinutes(now, null)); assertEquals(240, liveRecordMinutes(now, now - 1)) }
}

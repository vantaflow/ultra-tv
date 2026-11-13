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

class LiveRecordOptionsTest {
    private val utc = java.util.TimeZone.getTimeZone("UTC")
    // 20:40 UTC
    private val now = 1_800_000_000_000L - (1_800_000_000_000L % 3_600_000L) + 40 * 60_000L

    @Test fun avecGuide_finDuProgrammeEnPremier() {
        val o = liveRecordOptions(now, now + 50 * 60_000L, utc)
        assertEquals(LiveRecordOption.Kind.PROGRAMME_END, o.first().kind)
        assertEquals(60, o.first().minutes)
        assertEquals(LiveRecordOption.Kind.MANUAL, o.last().kind)
        assertEquals(LIVE_RECORD_MANUAL_MAX_MIN, o.last().minutes)
    }

    @Test fun sansGuide_pasDeFinDeProgramme_heuresPleines() {
        val o = liveRecordOptions(now, null, utc)
        assertEquals(LiveRecordOption.Kind.DURATION, o.first().kind)
        val until = o.filter { it.kind == LiveRecordOption.Kind.UNTIL }
        assertEquals(3, until.size)
        // 20:40 → 21:00 (20 min, plus de 15), 22:00, 23:00.
        assertEquals(listOf(20, 80, 140), until.map { it.minutes })
    }

    @Test fun libellesDeDuree() {
        assertEquals("30 min", liveRecordDurationLabel(30))
        assertEquals("2 h", liveRecordDurationLabel(120))
    }
}

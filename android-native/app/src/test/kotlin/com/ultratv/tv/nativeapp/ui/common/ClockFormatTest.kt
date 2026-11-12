package com.ultratv.tv.nativeapp.ui.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Format de l'heure 12 h / 24 h : logique pure (ClockFormat, TimeFormatPref) et branchement dans EpgClock. */
class ClockFormatTest {
    @After fun reset() {
        EpgClock.timeFormat = "auto"; EpgClock.system24 = true; EpgClock.zoneId = ""; EpgClock.langCode = "system"; EpgClock.offsetMinutes = 0
    }

    @Test fun vingtQuatreHeures() {
        assertEquals("16:30", ClockFormat.format(16, 30, true))
        assertEquals("00:05", ClockFormat.format(0, 5, true))
        assertEquals("09:00", ClockFormat.format(9, 0, true))
        assertEquals("23:59", ClockFormat.format(23, 59, true))
    }

    @Test fun douzeHeures() {
        assertEquals("4:30 PM", ClockFormat.format(16, 30, false))
        assertEquals("12:00 AM", ClockFormat.format(0, 0, false))
        assertEquals("12:00 PM", ClockFormat.format(12, 0, false))
        assertEquals("12:15 AM", ClockFormat.format(0, 15, false))
        assertEquals("1:05 AM", ClockFormat.format(1, 5, false))
        assertEquals("11:59 PM", ClockFormat.format(23, 59, false))
    }

    @Test fun langueDuMatinEtDeLApresMidi_chiffresLatins() {
        assertEquals("4:30 م", ClockFormat.format(16, 30, false, "ص", "م"))
        assertEquals("9:05 ص", ClockFormat.format(9, 5, false, "ص", "م"))
        assertEquals("4:30 p. m.", ClockFormat.format(16, 30, false, "a. m.", "p. m."))
        // 24 h : aucune mention, chiffres latins aussi en arabe.
        assertEquals("16:30", ClockFormat.format(16, 30, true, "ص", "م"))
    }

    @Test fun heurePleine() {
        assertEquals("03:00", ClockFormat.formatHour(3, true))
        assertEquals("3:00 AM", ClockFormat.formatHour(3, false))
        assertEquals("12:00 PM", ClockFormat.formatHour(12, false))
    }

    @Test fun plage_isoleeEnLtr() {
        assertEquals("⁦${"16:00"} – ${"17:30"}⁩", ClockFormat.range("16:00", "17:30"))
        assertTrue(ClockFormat.range("4:00 PM", "5:30 PM").contains("4:00 PM – 5:30 PM"))
    }

    @Test fun reglage_automatiqueSuitLeSysteme() {
        assertTrue(TimeFormatPref.AUTO.use24(true)); assertFalse(TimeFormatPref.AUTO.use24(false))
        assertFalse(TimeFormatPref.H12.use24(true)); assertTrue(TimeFormatPref.H24.use24(false))
    }

    @Test fun reglage_valeurInconnueOuAbsente_retombeSurAutomatique() {
        assertEquals(TimeFormatPref.AUTO, TimeFormatPref.parse(null))
        assertEquals(TimeFormatPref.AUTO, TimeFormatPref.parse(""))
        assertEquals(TimeFormatPref.AUTO, TimeFormatPref.parse("36"))
        assertEquals(TimeFormatPref.H12, TimeFormatPref.parse("12"))
        assertEquals(TimeFormatPref.H24, TimeFormatPref.parse(" 24 "))
        for (p in TimeFormatPref.entries) assertEquals(p, TimeFormatPref.parse(p.key))
    }

    // 2024-01-01 16:30 UTC
    private val at1630Utc = 1_704_126_600_000L

    @Test fun epgClock_defautAutomatique_systeme24h_nChangePas() {
        EpgClock.zoneId = "UTC"; EpgClock.system24 = true
        assertEquals("16:30", EpgClock.wall(at1630Utc))
        assertEquals("16:30", EpgClock.hm(at1630Utc))
        assertEquals("⁦16:30 – 17:00⁩", EpgClock.range(at1630Utc, at1630Utc + 30 * 60_000L))
    }

    @Test fun epgClock_automatiqueSurSysteme12h() {
        EpgClock.zoneId = "UTC"; EpgClock.system24 = false; EpgClock.langCode = "en"
        assertEquals("4:30 PM", EpgClock.wall(at1630Utc))
    }

    @Test fun epgClock_reglageExplicite_l_emporteSurLeSysteme() {
        EpgClock.zoneId = "UTC"; EpgClock.system24 = true; EpgClock.langCode = "en"
        EpgClock.timeFormat = "12"
        assertEquals("4:30 PM", EpgClock.wall(at1630Utc))
        EpgClock.system24 = false; EpgClock.timeFormat = "24"
        assertEquals("16:30", EpgClock.wall(at1630Utc))
    }

    @Test fun epgClock_decalageEpgEtFuseauRestentAppliques() {
        EpgClock.zoneId = "UTC"; EpgClock.timeFormat = "12"; EpgClock.langCode = "en"; EpgClock.offsetMinutes = 60
        assertEquals("5:30 PM", EpgClock.hm(at1630Utc))
        assertEquals("4:30 PM", EpgClock.wall(at1630Utc))
        EpgClock.offsetMinutes = 0; EpgClock.zoneId = "Asia/Tokyo"
        assertEquals("1:30 AM", EpgClock.wall(at1630Utc))
    }

    @Test fun epgClock_heurePleinePlanifiee() {
        EpgClock.timeFormat = "12"; EpgClock.langCode = "en"
        assertEquals("3:00 AM", EpgClock.hourLabel(3))
        EpgClock.timeFormat = "24"
        assertEquals("03:00", EpgClock.hourLabel(3))
    }
}

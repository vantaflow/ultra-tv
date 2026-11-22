package com.ultratv.tv.nativeapp.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Test

/** #248 : jours passés du guide selon l'archive des chaînes de la source. */
class GuideDaysTest {
    @Test fun premierJour_selonLArchive_plafond7_hierSansArchive() {
        assertEquals(-1, GuideDays.firstDay(0))
        assertEquals(-1, GuideDays.firstDay(1))
        assertEquals(-3, GuideDays.firstDay(3))
        assertEquals(-7, GuideDays.firstDay(7))
        assertEquals(-7, GuideDays.firstDay(30))
        assertEquals(-1..2, GuideDays.days(-1))
        assertEquals(-7..2, GuideDays.days(GuideDays.firstDay(14)))
    }

    @Test fun touchesRetourAvance_bornees() {
        assertEquals(-2, GuideDays.step(-1, forward = false, firstDay = -5))
        assertEquals(-5, GuideDays.step(-5, forward = false, firstDay = -5))
        assertEquals(-1, GuideDays.step(-1, forward = false, firstDay = -1))
        assertEquals(2, GuideDays.step(2, forward = true, firstDay = -5))
        assertEquals(1, GuideDays.step(0, forward = true, firstDay = -1))
    }

    @Test fun jourHorsBornes_retourAujourdhui() {
        assertEquals(-4, GuideDays.valid(-4, -7))
        assertEquals(0, GuideDays.valid(-4, -1))
        assertEquals(2, GuideDays.valid(2, -1))
    }
}

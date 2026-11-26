package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** Maroc revenu à GMT le 20 septembre 2026 : une base de fuseaux encore à GMT+1 est corrigée, une base à jour laissée telle quelle. */
class MoroccoZoneTest {
    private val after = EpgClock.MOROCCO_GMT_FROM_MS + 86_400_000L
    private val before = EpgClock.MOROCCO_GMT_FROM_MS - 86_400_000L

    /** Casablanca « à l'ancienne » : GMT+1 toute l'année (base de fuseaux pas mise à jour). */
    private val oldCasa = object : java.util.SimpleTimeZone(3_600_000, "Africa/Casablanca") {}

    @Test fun `apres le 20 septembre 2026 - Casablanca a GMT+1 devient GMT`() {
        assertEquals(0, EpgClock.effectiveZone(oldCasa, after).getOffset(after))
    }

    @Test fun `avant la date - inchange`() {
        assertEquals(3_600_000, EpgClock.effectiveZone(oldCasa, before).getOffset(before))
    }

    @Test fun `autres fuseaux et base a jour - inchanges`() {
        val paris = TimeZone.getTimeZone("Europe/Paris")
        assertEquals("Europe/Paris", EpgClock.effectiveZone(paris, after).id)
        val upToDate = java.util.SimpleTimeZone(0, "Africa/Casablanca")
        assertEquals("Africa/Casablanca", EpgClock.effectiveZone(upToDate, after).id)
    }
}

package com.ultratv.tv.nativeapp.data.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultHeuristicTest {
    @Test fun categoriesAdultes_detectees() {
        listOf("XXX", "ADULT 18+", "FR| Adulte", "Erotic", "EROTIQUE", "Porn", "للكبار").forEach { assertTrue(it, AdultHeuristic.isAdult(it)) }
    }

    @Test fun faussesAlertes_ero_dansUnMot_ignorees() {
        listOf("Heroes", "SUPER HERO", "Zero Dark", "Pero", "Numero 23", "VERONICA").forEach { assertFalse(it, AdultHeuristic.isAdult(it)) }
    }
}

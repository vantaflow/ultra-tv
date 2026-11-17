package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.ui.focus.FocusDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetailFocusTest {
    @Test fun droiteQuitteLaColonneGaucheVersLesEpisodes() {
        assertTrue(leftColumnExitsToEpisodes(FocusDirection.Right, ltr = true, hasEpisodes = true))
        assertTrue(leftColumnExitsToEpisodes(FocusDirection.Left, ltr = false, hasEpisodes = true))
    }
    @Test fun autresDirectionsRestentGeometriques() {
        for (d in listOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left)) assertFalse(leftColumnExitsToEpisodes(d, true, true))
        assertFalse(leftColumnExitsToEpisodes(FocusDirection.Right, ltr = false, hasEpisodes = true))
    }
    @Test fun sansEpisodePasDeSaut() {
        assertFalse(leftColumnExitsToEpisodes(FocusDirection.Right, ltr = true, hasEpisodes = false))
    }
}

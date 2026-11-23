package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferingProgressTest {
    @Test fun imagesRendues_progression() = assertTrue(BufferingProgress.moved(ProgressMark(100, 5_000), ProgressMark(125, 5_000)))
    @Test fun positionQuiAvance_progression() = assertTrue(BufferingProgress.moved(ProgressMark(-1, 5_000), ProgressMark(-1, 6_000)))
    @Test fun reculDHorodatage_progression() = assertTrue(BufferingProgress.moved(ProgressMark(-1, 25_000), ProgressMark(-1, 1_000)))
    @Test fun rienNeBouge_pasDeProgression() = assertFalse(BufferingProgress.moved(ProgressMark(100, 5_000), ProgressMark(100, 5_100)))
    @Test fun imageIsolee_pasDeProgression() = assertFalse(BufferingProgress.moved(ProgressMark(100, 5_000), ProgressMark(101, 5_000)))
    @Test fun imagesInconnues_seulLaPositionCompte() = assertFalse(BufferingProgress.moved(ProgressMark(-1, 5_000), ProgressMark(40, 5_000)))
    @Test fun sansMoteur_pasDeProgression() = assertFalse(BufferingProgress.moved(null, ProgressMark(1, 1)))
}

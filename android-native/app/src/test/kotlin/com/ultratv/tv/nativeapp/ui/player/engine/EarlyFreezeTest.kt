package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyFreezeTest {
    @Test fun `gel juste après le démarrage en matériel = décodeur`() {
        assertTrue(PlaybackPlanner.frozenMeansDecoder(11_000, DecoderMode.HARDWARE))
        assertTrue(PlaybackPlanner.frozenMeansDecoder(11_000, DecoderMode.AUTO))
    }

    @Test fun `en logiciel ou après une longue lecture = reconnexion`() {
        assertFalse(PlaybackPlanner.frozenMeansDecoder(11_000, DecoderMode.SOFTWARE))
        assertFalse(PlaybackPlanner.frozenMeansDecoder(PlaybackPlanner.EARLY_FREEZE_MS, DecoderMode.HARDWARE))
        assertFalse(PlaybackPlanner.frozenMeansDecoder(-1, DecoderMode.AUTO))
    }

    @Test fun `après le matériel, on essaie Exo auto puis le logiciel`() {
        val tried = setOf(Combo(EngineKind.EXO, DecoderMode.HARDWARE))
        assertEquals(Combo(EngineKind.EXO, DecoderMode.AUTO), PlaybackPlanner.next(EngineChoice.EXO, tried))
        assertEquals(Combo(EngineKind.EXO, DecoderMode.SOFTWARE), PlaybackPlanner.next(EngineChoice.EXO, tried + Combo(EngineKind.EXO, DecoderMode.AUTO)))
    }
}

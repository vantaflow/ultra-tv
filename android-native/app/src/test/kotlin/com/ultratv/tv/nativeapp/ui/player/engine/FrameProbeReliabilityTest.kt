package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameProbeReliabilityTest {
    /** Fire TV / Amlogic : la vidéo d'une SurfaceView est sur un plan séparé, la copie revient noire. Ne rien conclure. */
    @Test fun surfaceView_nonFiable_textureView_fiable() {
        assertFalse(FrameProbe.reliable(isTextureView = false))
        assertTrue(FrameProbe.reliable(isTextureView = true))
    }
}

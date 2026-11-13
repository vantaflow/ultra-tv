package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.ui.text.style.TextOverflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFitTest {
    @Test fun seulementTexteUneLigneAvecPointsDeSuspensionSurTactile() {
        assertTrue(AutoFit.applies(touch = true, maxLines = 1, overflow = TextOverflow.Ellipsis, sizeKnown = true))
        assertFalse(AutoFit.applies(touch = false, maxLines = 1, overflow = TextOverflow.Ellipsis, sizeKnown = true))   // TV : inchangé
        assertFalse(AutoFit.applies(touch = true, maxLines = 2, overflow = TextOverflow.Ellipsis, sizeKnown = true))
        assertFalse(AutoFit.applies(touch = true, maxLines = 1, overflow = TextOverflow.Clip, sizeKnown = true))
        assertFalse(AutoFit.applies(touch = true, maxLines = 1, overflow = TextOverflow.Ellipsis, sizeKnown = false))
    }

    @Test fun reduitParPasSansDescendreSousLeMinimum() {
        assertEquals(1f, AutoFit.nextScale(1f, overflowing = false), 0f)
        assertEquals(0.95f, AutoFit.nextScale(1f, overflowing = true), 0.001f)
        var s = 1f
        repeat(20) { s = AutoFit.nextScale(s, overflowing = true) }
        assertEquals(AutoFit.MIN_SCALE, s, 0.001f)
    }
}

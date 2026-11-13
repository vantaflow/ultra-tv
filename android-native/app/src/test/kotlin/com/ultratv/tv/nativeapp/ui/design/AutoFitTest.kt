package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.ui.text.style.TextOverflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFitTest {
    @Test fun concerneLesTextesUneLigneAvecPointsDeSuspension() {
        assertTrue(AutoFit.applies(maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = true))
        assertFalse(AutoFit.applies(maxLines = 2, overflow = TextOverflow.Ellipsis, softWrap = true))
        assertFalse(AutoFit.applies(maxLines = 1, overflow = TextOverflow.Clip, softWrap = true))
        assertFalse(AutoFit.applies(maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false))   // pastilles
    }

    @Test fun passeALaLigneSaufSiLeConteneurEstTropBas() {
        assertNull(AutoFit.next(2, 1f, cut = false, tooTall = false, touch = false))      // tient sur deux lignes : fini
        assertEquals(1 to 1f, AutoFit.next(2, 1f, cut = false, tooTall = true, touch = false))   // trop bas : une ligne
        assertNull(AutoFit.next(1, 1f, cut = true, tooTall = false, touch = false))       // TV : « … » en dernier recours
    }

    @Test fun telephoneReduitAvantDeCouper() {
        assertEquals(1 to 0.95f, AutoFit.next(1, 1f, cut = true, tooTall = false, touch = true))
        var s = 1f
        while (true) { val n = AutoFit.next(1, s, cut = true, tooTall = false, touch = true) ?: break; s = n.second }
        assertEquals(AutoFit.MIN_SCALE, s, 0.001f)
    }
}

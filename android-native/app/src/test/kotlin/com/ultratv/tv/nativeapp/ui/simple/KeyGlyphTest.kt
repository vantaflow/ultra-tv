package com.ultratv.tv.nativeapp.ui.simple

import org.junit.Assert.assertEquals
import org.junit.Test

/** Rappels de touches du mode simple : les symboles sont dessinés (la police des TV n'a pas « ↩ » ni « ⏵ »). */
class KeyGlyphTest {
    @Test fun touches_symbolesDessines() {
        assertEquals("back", keyGlyphKind("↩"))
        assertEquals("hold", keyGlyphKind("OK ⏵"))
        assertEquals("text", keyGlyphKind("OK"))
    }
}

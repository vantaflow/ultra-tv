package com.ultratv.tv.nativeapp.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacInputTest {
    @Test fun saisieNormale_apresLePrefixe() {
        assertEquals("001A79AABBCC", macDigits("001A79aa:bb:cc"))
        assertEquals("001A79", macDigits("00:1A:79"))
        assertEquals("001A79AABBCC", macDigits("001A79AABBCCDD"))   // 14 chiffres : les suivants sont ignorés, comme avant
    }

    @Test fun macCompleteTapeeDerriereLePrefixe() {
        assertEquals("001A79123456", macDigits("001A79" + "00:1A:79:12:34:56"))
        assertEquals("D46A6ABCDEF0", macDigits("001A79" + "D4:6A:6A:BC:DE:F0"))
    }

    @Test fun clavierFireTv() {
        assertTrue(keepsImeTextArea("Amazon"))
        assertTrue(keepsImeTextArea("amazon"))
        assertFalse(keepsImeTextArea("Google"))
        assertFalse(keepsImeTextArea(null))
    }
}

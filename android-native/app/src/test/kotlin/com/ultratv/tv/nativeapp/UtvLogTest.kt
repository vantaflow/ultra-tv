package com.ultratv.tv.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtvLogTest {
    @Test fun maskUrl_retireIdentifiantsRequeteEtSegmentsXtream() {
        assertEquals("http://h.example:8080/live/<redacted>/<redacted>/12.ts", UtvLog.maskUrl("http://h.example:8080/live/user/pass/12.ts"))
        assertEquals("https://plex.example:32400/library/sections?…", UtvLog.maskUrl("https://anna:secret@plex.example:32400/library/sections?X-Plex-Token=abc123"))
        assertEquals("-", UtvLog.maskUrl(null))
    }

    @Test fun mask_jetonsEtMotsDePasse() {
        val m = UtvLog.mask("auth token=abc123 password=hunter2 Bearer xyz.abc utv_abcdefghijklmnopqrstu")
        assertFalse(m.contains("abc123") || m.contains("hunter2") || m.contains("xyz.abc") || m.contains("abcdefghijklmnopqrstu"))
    }

    @Test fun why_classeEtMessageMasque() {
        val w = UtvLog.why(java.io.IOException("GET http://u:p@h/x?token=zzz failed"))
        assertTrue(w.startsWith("IOException: "))
        assertFalse(w.contains("zzz") || w.contains("u:p@"))
    }

    @Test fun tagsStables() = assertEquals(listOf("UtvCloud", "UtvMedia", "UtvSync", "UtvSport", "UtvPlay"), listOf(UtvLog.CLOUD, UtvLog.MEDIA, UtvLog.SYNC, UtvLog.SPORT, UtvLog.PLAY))
}

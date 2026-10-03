package com.ultratv.tv.nativeapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateChecksumTest {
    private val hello = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" // sha256("hello")

    @Test fun sha256Hex_calculeLeCondensat() {
        val f = File.createTempFile("apk", ".bin").apply { writeText("hello"); deleteOnExit() }
        assertEquals(hello, UpdateChecker.sha256Hex(f))
    }

    @Test fun parseExpectedDigest_accepteLeFormatSha256sum() {
        assertEquals(hello, UpdateChecker.parseExpectedDigest("$hello  UltraTV-debug.apk\n"))
        assertEquals(hello, UpdateChecker.parseExpectedDigest(hello.uppercase()))
    }

    @Test fun parseExpectedDigest_refuseLeContenuIllisible() {
        assertEquals("", UpdateChecker.parseExpectedDigest("<html>404</html>"))
        assertEquals("", UpdateChecker.parseExpectedDigest(""))
    }

    @Test fun digestMatches_refuseLeVideEtLesDifferences() {
        assertTrue(UpdateChecker.digestMatches(hello, hello.uppercase()))
        assertFalse(UpdateChecker.digestMatches("", ""))
        assertFalse(UpdateChecker.digestMatches(hello, hello.replaceRange(0, 1, "0")))
    }
}

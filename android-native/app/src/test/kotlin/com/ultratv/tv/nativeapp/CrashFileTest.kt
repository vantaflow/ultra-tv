package com.ultratv.tv.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CrashFileTest {
    @Test fun `la version qui a plante est gardee, pas celle qui envoie`() {
        val raw = CrashFile.entry("1.2.87", 187, "main: java.lang.IllegalStateException: boom\n\tat x.y")
        val (name, code, stack) = CrashFile.parse(raw, "1.2.92", 192)
        assertEquals("1.2.87", name)
        assertEquals(187, code)
        assertFalse(stack.contains("@@ultratv-version"))
        assertEquals(true, stack.contains("IllegalStateException"))
    }

    @Test fun `ancien fichier sans en-tete, version courante`() {
        val (name, code, stack) = CrashFile.parse("main: java.lang.RuntimeException\n", "1.2.92", 192)
        assertEquals("1.2.92", name)
        assertEquals(192, code)
        assertEquals(true, stack.startsWith("main: java.lang.RuntimeException"))
    }

    @Test fun `plusieurs entrees, la premiere fait foi`() {
        val raw = CrashFile.entry("1.2.90", 190, "a") + CrashFile.entry("1.2.91", 191, "b")
        assertEquals("1.2.90", CrashFile.parse(raw, "1.2.92", 192).first)
    }
}

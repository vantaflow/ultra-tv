package com.ultratv.tv.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashFileTest {
    private val max = 30_000

    @Test fun `la version qui a plante est gardee, pas celle qui envoie`() {
        val raw = CrashFile.entry("1.2.87", 187, "main: java.lang.IllegalStateException: boom\n\tat x.y")
        val (name, code, stack) = CrashFile.latest(raw, "1.2.92", 192, max)
        assertEquals("1.2.87", name)
        assertEquals(187, code)
        assertFalse(stack.contains("@@ultratv-version"))
        assertTrue(stack.contains("IllegalStateException"))
    }

    @Test fun `ancien fichier sans en-tete, version courante`() {
        val (name, code, stack) = CrashFile.latest("main: java.lang.RuntimeException\n", "1.2.92", 192, max)
        assertEquals("1.2.92", name)
        assertEquals(192, code)
        assertTrue(stack.startsWith("main: java.lang.RuntimeException"))
    }

    @Test fun `plusieurs versions - le rapport porte la derniere version qui a plante et ses seuls plantages`() {
        val raw = CrashFile.entry("1.2.90", 190, "ancien") + CrashFile.entry("1.2.91", 191, "avant-dernier") + CrashFile.entry("1.2.91", 191, "dernier")
        val (name, code, stack) = CrashFile.latest(raw, "1.2.92", 192, max)
        assertEquals("1.2.91", name)
        assertEquals(191, code)
        assertTrue(stack.contains("avant-dernier") && stack.contains("dernier"))
        assertFalse(stack.contains("ancien"))
    }

    /**
     * Un appareil sans compte n'envoyait jamais son fichier, qui grossissait à chaque plantage. Trop gros pour le Worker,
     * il était refusé à chaque démarrage et plus aucun plantage ne remontait : seuls les plus récents partent, dans la limite.
     */
    @Test fun `fichier enorme - seuls les plantages les plus recents partent, dans la limite`() {
        val raw = (1..400).joinToString("") { CrashFile.entry("1.3.71", 10371, "plantage $it\n" + "\tat a.b(c:1)\n".repeat(20)) }
        assertTrue(raw.length > 100_000)
        val (name, _, stack) = CrashFile.latest(raw, "1.3.72", 10372, max)
        assertEquals("1.3.71", name)
        assertTrue(stack.length <= max)
        assertTrue(stack.contains("plantage 400"))
        assertFalse(stack.contains("plantage 1\n"))
    }

    @Test fun `un seul plantage plus long que la limite - son debut est garde`() {
        val raw = CrashFile.entry("1.3.71", 10371, "java.lang.OutOfMemoryError: debut\n" + "x".repeat(50_000))
        val (_, _, stack) = CrashFile.latest(raw, "1.3.72", 10372, max)
        assertTrue(stack.length <= max)
        assertTrue(stack.startsWith("java.lang.OutOfMemoryError: debut"))
    }

    @Test fun `fichier vide`() {
        assertEquals(Triple("1.3.72", 10372, ""), CrashFile.latest("", "1.3.72", 10372, max))
    }
}

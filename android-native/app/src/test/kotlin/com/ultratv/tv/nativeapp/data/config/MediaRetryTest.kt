package com.ultratv.tv.nativeapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRetryTest {
    @Test fun `délais croissants puis toutes les 2 h`() {
        val r = MediaRetry()
        assertTrue(r.allowed("j", 0))
        r.failed("j", 0)
        assertFalse(r.allowed("j", 60_000))
        assertTrue(r.allowed("j", 2 * 60_000))
        r.failed("j", 2 * 60_000)
        assertFalse(r.allowed("j", 2 * 60_000 + 9 * 60_000))
        assertTrue(r.allowed("j", 12 * 60_000))
        repeat(5) { r.failed("j", 0) }
        assertFalse(r.allowed("j", 2 * 3_600_000L - 1))
        assertTrue(r.allowed("j", 2 * 3_600_000L))
    }

    @Test fun `succès et serveur retiré oubliés`() {
        val r = MediaRetry()
        r.failed("a", 0); r.failed("b", 0)
        r.succeeded("a")
        assertEquals(setOf("b"), r.pendingIds())
        assertTrue(r.anyDue(10 * 60_000))
        r.retain(emptySet())
        assertFalse(r.anyDue(10 * 60_000))
    }
}

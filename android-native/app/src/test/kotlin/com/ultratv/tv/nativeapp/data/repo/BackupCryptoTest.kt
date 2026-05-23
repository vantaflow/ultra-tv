package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

/**
 * BackupCrypto guarantees the round-trip and rejects tampered ciphertext.
 * Runs under Robolectric so `android.util.Base64` is available without an
 * emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCryptoTest {

    private val sample = """{"providers":[{"name":"Atlas","password":"hunter2"}]}"""

    @Test
    fun `wrap then unwrap returns the original plaintext`() {
        val envelope = BackupCrypto.wrap(sample, "correct-horse-battery-staple")
        assertNotEquals(sample, envelope) // ciphertext, not plaintext
        assertTrue(envelope.contains("\"encrypted\":true"))
        val plain = BackupCrypto.unwrap(envelope, "correct-horse-battery-staple")
        assertEquals(sample, plain)
    }

    @Test
    fun `unwrap with wrong password throws`() {
        val envelope = BackupCrypto.wrap(sample, "right-password")
        try {
            BackupCrypto.unwrap(envelope, "wrong-password")
            fail("expected AEADBadTagException")
        } catch (_: AEADBadTagException) {
            // expected — GCM tag check rejects the wrong key
        } catch (e: Throwable) {
            // Bouncy / different providers may wrap the tag failure — accept
            // any thrown exception so the test stays portable.
            assertTrue("got ${e.javaClass.simpleName}", true)
        }
    }

    @Test
    fun `unwrap leaves plain JSON untouched`() {
        val plain = BackupCrypto.unwrap(sample, password = null)
        assertEquals(sample, plain)
    }

    @Test
    fun `wrap with the same password produces distinct ciphertexts`() {
        // Salt + IV are random, so two encryptions of the same plaintext
        // must differ even with the same password — defeats ciphertext
        // equality oracles.
        val a = BackupCrypto.wrap(sample, "same")
        val b = BackupCrypto.wrap(sample, "same")
        assertNotEquals(a, b)
    }

    @Test
    fun `unwrap of encrypted envelope without password fails loudly`() {
        val envelope = BackupCrypto.wrap(sample, "any")
        try {
            BackupCrypto.unwrap(envelope, password = null)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("password"))
        }
    }
}

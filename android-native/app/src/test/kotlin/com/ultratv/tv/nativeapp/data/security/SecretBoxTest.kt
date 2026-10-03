package com.ultratv.tv.nativeapp.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretBoxTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val k = key()
    private val box = SecretBox { k }

    @Test fun roundTrip_restitueLeClair() {
        val enc = box.encrypt("hunter2")
        assertTrue(enc.startsWith(SecretBox.PREFIX))
        assertNotEquals("hunter2", enc)
        assertEquals("hunter2", box.decrypt(enc))
    }

    @Test fun encrypt_estNonDeterministe_parIv() =
        assertNotEquals(box.encrypt("x"), box.encrypt("x"))

    @Test fun donneeHeriteeEnClair_estLueTelleQuelle() =
        assertEquals("ancien", box.decrypt("ancien"))

    @Test fun vide_resteVide_etPasDeDoubleChiffrement() {
        assertEquals("", box.encrypt(""))
        val enc = box.encrypt("a")
        assertEquals(enc, box.encrypt(enc))
    }

    @Test fun cleInvalide_renvoieVideSansPlanter() {
        val enc = box.encrypt("secret")
        assertEquals("", SecretBox { key() }.decrypt(enc))
    }

    @Test fun donneeAltérée_renvoieVide() {
        val enc = box.encrypt("secret")
        val tampered = enc.dropLast(3) + (if (enc.endsWith("AAA")) "BBB" else "AAA")
        assertEquals("", box.decrypt(tampered))
    }
}

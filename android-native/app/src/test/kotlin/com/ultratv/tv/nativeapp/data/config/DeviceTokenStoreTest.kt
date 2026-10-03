package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Chiffre comme le Keystore (AES-GCM) mais avec une clé en mémoire : le Keystore n'existe pas en JVM. */
class FakeCipher(private val keyBytes: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }) : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv)) }
        return iv + c.doFinal(plain)
    }
    override fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, blob, 0, 12)) }
        return c.doFinal(blob, 12, blob.size - 12)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceTokenStoreTest {
    private lateinit var ctx: Context
    private var now = 1_700_000_000_000L

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store(cipher: TokenCipher = FakeCipher()) =
        DeviceTokenStore(ctx.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE), cipher) { now }

    @Test fun `sans jeton l'appareil n'est pas appaire`() {
        assertFalse(store().isPaired)
        assertNull(store().token())
    }

    @Test fun `save puis token restitue le jeton`() {
        val s = store()
        s.save("utv_secret-token", "dev1")
        assertEquals("utv_secret-token", s.token())
        assertEquals("dev1", s.deviceId())
        assertTrue(s.isPaired)
    }

    @Test fun `le jeton n'est jamais ecrit en clair dans les preferences`() {
        store().save("utv_secret-token", "dev1")
        val raw = ctx.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE).all.values.joinToString { it.toString() }
        assertFalse(raw.contains("utv_secret-token"))
    }

    @Test fun `un blob illisible vaut non appaire sans planter`() {
        // clé perdue (restauration de sauvegarde, réinstallation) : on retombe sur « non appairé »
        store(FakeCipher()).save("utv_secret-token", "dev1")
        val other = store(FakeCipher()) // autre clé
        assertNull(other.token())
        assertFalse(other.isPaired)
    }

    @Test fun `clear efface tout`() {
        val s = store()
        s.save("utv_x", "d")
        s.clear()
        assertNull(s.token()); assertNull(s.deviceId())
    }

    @Test fun `ageDays compte depuis l'enregistrement`() {
        val s = store()
        s.save("utv_x", "d")
        assertEquals(0L, s.ageDays())
        now += 91L * 24 * 3600 * 1000
        assertEquals(91L, s.ageDays())
    }
}

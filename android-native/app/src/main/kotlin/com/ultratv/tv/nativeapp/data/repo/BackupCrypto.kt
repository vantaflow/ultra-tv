package com.ultratv.tv.nativeapp.data.repo

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import java.security.SecureRandom

/**
 * AES-GCM envelope for backup files. When the user picks a password we wrap
 * the plain JSON in:
 *
 *   {
 *     "ultraTvBackup": 1,
 *     "encrypted": true,
 *     "kdf": "PBKDF2WithHmacSHA256",
 *     "iter": 120000,
 *     "salt": "<base64>",
 *     "iv":   "<base64>",
 *     "ct":   "<base64 ciphertext+tag>"
 *   }
 *
 * Plain JSON exports are passed through untouched; `unwrap()` auto-detects
 * the envelope by the `encrypted` marker and either decrypts or returns the
 * input as-is.
 */
object BackupCrypto {

    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val KDF_ITER = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Wrap [plainJson] in an AES-GCM envelope keyed by [password]. */
    fun wrap(plainJson: String, password: String): String {
        val rng = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("ultraTvBackup", 1)
            put("encrypted", true)
            put("kdf", KDF)
            put("iter", KDF_ITER)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("ct", b64(ct))
        }.toString()
    }

    /**
     * Decrypt an envelope if it is one, else return [text] unchanged. Throws
     * IllegalArgumentException when the envelope is malformed or the password
     * is wrong (AES-GCM tag check fails → AEADBadTagException, surfaced).
     */
    fun unwrap(text: String, password: String?): String {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) return text
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return text
        if (!obj.optBoolean("encrypted")) return text
        require(!password.isNullOrEmpty()) { "Backup is encrypted — password required." }
        val salt = unb64(obj.getString("salt"))
        val iv = unb64(obj.getString("iv"))
        val ct = unb64(obj.getString("ct"))
        val iter = obj.optInt("iter", KDF_ITER)
        val key = deriveKey(password, salt, iter)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val plain = cipher.doFinal(ct)
        return plain.toString(Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray, iter: Int = KDF_ITER): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iter, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}

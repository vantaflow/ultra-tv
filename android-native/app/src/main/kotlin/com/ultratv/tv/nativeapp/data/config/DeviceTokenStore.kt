package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Chiffrement symétrique d'un petit secret. Abstrait pour pouvoir tester sans Keystore. */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM avec une clé non exportable de l'AndroidKeyStore (matérielle quand
 * l'appareil le permet). Le blob est `iv (12 octets) || texte chiffré+tag`.
 */
class KeystoreTokenCipher(private val alias: String = "ultratv_device_token_v1") : TokenCipher {

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_LEN) { "blob trop court" }
        val c = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_LEN)) }
        return c.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

/**
 * Jeton d'appareil délivré par le Worker après appairage. Il n'est jamais écrit
 * en clair : seul le blob chiffré par le Keystore vit dans les préférences, et
 * `allowBackup` ne peut donc pas l'exfiltrer sous une forme utilisable (la clé
 * reste dans le Keystore de l'appareil).
 *
 * Un blob illisible (clé Keystore perdue après restauration, par ex.) équivaut à
 * « non appairé » : l'utilisateur ré-appaire, rien ne plante.
 */
@Singleton
class DeviceTokenStore internal constructor(
    private val prefs: SharedPreferences,
    private val cipher: TokenCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(@ApplicationContext ctx: Context) :
        this(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE), KeystoreTokenCipher())

    fun save(token: String, deviceId: String) {
        val blob = Base64.encodeToString(cipher.encrypt(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit()
            .putString(K_TOKEN, blob)
            .putString(K_DEVICE, deviceId)
            .putLong(K_SINCE, clock())
            .apply()
    }

    fun token(): String? {
        val blob = prefs.getString(K_TOKEN, null) ?: return null
        return runCatching { String(cipher.decrypt(Base64.decode(blob, Base64.NO_WRAP)), Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    val isPaired: Boolean get() = token() != null
    fun deviceId(): String? = prefs.getString(K_DEVICE, null)

    /** Ancienneté du jeton en jours, pour déclencher la rotation. */
    fun ageDays(): Long {
        val since = prefs.getLong(K_SINCE, 0L)
        return if (since == 0L) 0L else (clock() - since) / DAY_MS
    }

    fun clear() {
        prefs.edit().remove(K_TOKEN).remove(K_DEVICE).remove(K_SINCE).apply()
    }

    private companion object {
        const val PREFS = "cloud_sync"
        const val K_TOKEN = "device_token_enc"
        const val K_DEVICE = "device_id"
        const val K_SINCE = "token_since"
        const val DAY_MS = 24L * 3600 * 1000
    }
}

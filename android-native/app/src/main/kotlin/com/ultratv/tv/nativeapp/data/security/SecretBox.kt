package com.ultratv.tv.nativeapp.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement AES-256-GCM des secrets stockés (mots de passe Xtream).
 * La clé vit dans l'Android Keystore : elle n'est ni exportable ni incluse dans
 * une sauvegarde, donc un dump de la base ne révèle plus les mots de passe.
 *
 * Format : `enc1:` + base64(iv[12] || chiffré+tag). Toute valeur sans ce préfixe
 * est une donnée héritée en clair, renvoyée telle quelle puis rechiffrée par
 * [ProviderSecretsMigrator].
 */
class SecretBox(private val keyProvider: () -> SecretKey = ::keystoreKey) {

    // Clé lue UNE fois (avant : KeyStore.load + getKey — appels au démon keystore — à CHAQUE déchiffrement). Un échec
    // n'est pas mémorisé (lazy relance l'initialisation), la valeur suivante retente.
    private val key: SecretKey by lazy { keyProvider() }

    /**
     * Derniers déchiffrements (texte chiffré → clair) : la liste des sources est relue et déchiffrée à chaque émission
     * de Room (plusieurs écrans l'observent dès le démarrage), chaque déchiffrement étant un aller-retour avec le
     * keystore. Le clair vit déjà en mémoire dans les entités ; borné à quelques sources.
     */
    private val plainCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun encrypt(plain: String): String {
        if (plain.isEmpty() || plain.startsWith(PREFIX)) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    /** Renvoie "" si la clé est perdue/invalide (ex. données restaurées sur un autre appareil). */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        plainCache[stored]?.let { return it }
        return runCatching {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        }.onSuccess { if (plainCache.size < 64) plainCache[stored] = it }.getOrDefault("")
    }

    fun isEncrypted(stored: String) = stored.startsWith(PREFIX)

    companion object {
        const val PREFIX = "enc1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val ALIAS = "ultratv_provider_secrets"

        /** Instance utilisée par le convertisseur Room (qui n'a pas accès à Hilt). */
        @Volatile var shared: SecretBox = SecretBox()

        private fun keystoreKey(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }
    }
}

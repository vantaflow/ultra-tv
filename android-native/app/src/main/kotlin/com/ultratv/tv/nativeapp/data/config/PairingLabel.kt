package com.ultratv.tv.nativeapp.data.config

/**
 * Étiquette lisible de la box (« UTV-3F9A2C ») : 3 octets du SHA-256 de l'identifiant d'appareil. Elle sert UNIQUEMENT de nom
 * affiché sur le tableau de bord, jamais de clé ni d'identité (l'appareil s'authentifie par son jeton).
 */
object PairingLabel {
    fun of(deviceId: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray(Charsets.UTF_8))
        return "UTV-" + d.take(3).joinToString("") { "%02X".format(java.util.Locale.ROOT, it) }
    }
}

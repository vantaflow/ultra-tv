package com.ultratv.tv.nativeapp.data.security

import com.ultratv.tv.nativeapp.data.db.ProviderDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migration des données existantes : les mots de passe écrits en clair par les
 * versions précédentes sont rechiffrés une fois au démarrage. Idempotent
 * (les valeurs déjà préfixées `enc1:` et les mots de passe vides sont ignorés)
 * et sans effet de bord si interrompu : la lecture accepte les deux formes.
 */
@Singleton
class ProviderSecretsMigrator @Inject constructor(private val dao: ProviderDao) {
    suspend fun migrate(box: SecretBox = SecretBox.shared): Int {
        var n = 0
        for (row in dao.rawPasswords()) {
            if (row.raw.isEmpty() || box.isEncrypted(row.raw)) continue
            dao.setRawPassword(row.id, box.encrypt(row.raw))
            n++
        }
        return n
    }
}

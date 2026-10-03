package com.ultratv.tv.nativeapp.data.config

import javax.inject.Inject
import javax.inject.Singleton

/** Aucun jeton stocké : l'appareil n'est pas (ou plus) appairé. */
class NotPairedException : RuntimeException("This device is not paired with the dashboard yet")

/**
 * Lecture authentifiée de la configuration cloud : jeton d'appareil (Keystore),
 * rotation périodique, effacement du jeton quand le Worker le révoque.
 */
@Singleton
class CloudConfigSource @Inject constructor(
    private val client: CloudPairingClient,
    private val tokens: DeviceTokenStore,
) {
    /** Jeton plus vieux que ça : on le fait tourner à la prochaine synchro. */
    private val rotateAfterDays = 90L

    /**
     * @throws NotPairedException aucun jeton stocké
     * @throws TokenRejectedException le Worker a révoqué le jeton ; il est effacé localement
     */
    suspend fun fetch(workerBase: String): String {
        var token = tokens.token() ?: throw NotPairedException()
        if (tokens.ageDays() >= rotateAfterDays) {
            // Une rotation ratée ne bloque pas la synchro : on réessaiera la fois suivante.
            runCatching { client.rotate(workerBase, token) }.onSuccess { (t, id) ->
                tokens.save(t, id)
                token = t
            }
        }
        return try {
            client.fetchConfig(workerBase, token)
        } catch (e: TokenRejectedException) {
            tokens.clear()
            throw e
        }
    }
}

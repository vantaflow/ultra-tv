package com.ultratv.tv.nativeapp.data.config

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PairingEvent {
    /** Le code à afficher sur la TV. */
    data class CodeReady(val code: String, val expiresInSec: Int) : PairingEvent
    data object Paired : PairingEvent
    data object Expired : PairingEvent
    data class Failed(val message: String) : PairingEvent
}

/**
 * Déroulé de l'appairage : demande un code, l'affiche, interroge le Worker
 * jusqu'à ce que l'utilisateur l'ait saisi dans le tableau de bord, puis range
 * le jeton dans le Keystore. Le flux se termine sur Paired, Expired ou Failed ;
 * annuler la collecte arrête l'interrogation.
 */
@Singleton
class CloudPairing @Inject constructor(
    private val client: CloudPairingClient,
    private val store: DeviceTokenStore,
) {
    internal var sleeper: suspend (Long) -> Unit = { delay(it) }

    fun run(base: String, label: String): Flow<PairingEvent> = flow {
        val session = try {
            client.start(base, label)
        } catch (e: RateLimitedException) {
            emit(PairingEvent.Failed("Too many attempts — retry in ${e.retryAfterSec}s")); return@flow
        } catch (e: Exception) {
            emit(PairingEvent.Failed(e.message ?: "Cannot reach the Worker")); return@flow
        }
        emit(PairingEvent.CodeReady(session.code, session.expiresInSec))
        var waitedMs = 0L
        val limitMs = session.expiresInSec * 1000L
        var intervalMs = session.intervalSec.coerceIn(1, 30) * 1000L
        while (waitedMs <= limitMs) {
            sleeper(intervalMs); waitedMs += intervalMs
            try {
                when (val r = client.poll(base, session)) {
                    PollResult.Pending -> Unit
                    PollResult.Gone -> { emit(PairingEvent.Expired); return@flow }
                    is PollResult.Paired -> { store.save(r.token, r.deviceId); emit(PairingEvent.Paired); return@flow }
                }
            } catch (e: RateLimitedException) {
                intervalMs = (e.retryAfterSec.coerceIn(1, 60)) * 1000L // on ralentit au lieu d'insister
            } catch (e: Exception) {
                emit(PairingEvent.Failed(e.message ?: "Network error")); return@flow
            }
        }
        emit(PairingEvent.Expired)
    }
}

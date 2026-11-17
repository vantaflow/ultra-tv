package com.ultratv.tv.nativeapp.data.mediaserver

import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Issue de l'attente du code PIN : jeton du compte, ou code expiré / refusé par plex.tv. */
sealed interface PlexLinkResult {
    data class Token(val value: String) : PlexLinkResult
    data object Expired : PlexLinkResult
}

/**
 * Boucle de connexion par code PIN (plex.tv/link) : demande un code, le fait afficher ([onCode]), puis sonde toutes les
 * [pollMs] ms jusqu'à l'arrivée du jeton, ou l'expiration ([ttlMs], ou 404 de plex.tv). Une erreur réseau passagère pendant
 * la sonde ne l'interrompt pas. Le temps est compté sur les attentes ([wait]) : testable sans horloge réelle.
 * La demande initiale qui échoue lève son erreur à l'appelant.
 */
suspend fun awaitPlexToken(
    create: suspend () -> Plex.Pin,
    poll: suspend (Long) -> String?,
    onCode: (String) -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) },
    ttlMs: Long = Plex.PIN_TTL_MS,
    pollMs: Long = Plex.PIN_POLL_MS,
): PlexLinkResult {
    val pin = create()
    onCode(pin.code)
    val limit = pin.expiresInSec?.takeIf { it > 0 }?.let { minOf(it * 1000, ttlMs) } ?: ttlMs
    var elapsed = 0L
    while (elapsed < limit) {
        wait(pollMs)
        elapsed += pollMs
        try {
            poll(pin.id)?.let { return PlexLinkResult.Token(it) }
        } catch (c: CancellationException) {
            throw c
        } catch (e: HttpStatusException) {
            if (e.code == 404) return PlexLinkResult.Expired
        } catch (_: Throwable) { /* réseau passager : on réessaie */ }
    }
    return PlexLinkResult.Expired
}

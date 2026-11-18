package com.ultratv.tv.nativeapp.data.config

/**
 * Serveurs multimédias du compte (Jellyfin, Emby, NAS…) injoignables depuis cet appareil : nouvel essai avec des délais
 * croissants (2 min, 10 min, 30 min, puis toutes les 2 h) au lieu d'un essai bloquant de 10 s à CHAQUE synchro. Avant,
 * l'échec empêchait aussi de retenir l'ETag : la configuration entière était relue et réappliquée toutes les 2 minutes.
 * En mémoire seulement : un nouveau lancement réessaie tout de suite. Pur, testable.
 */
class MediaRetry {
    private data class State(val failures: Int, val nextAt: Long)
    private val states = HashMap<String, State>()

    @Synchronized fun failed(id: String, now: Long) {
        val n = (states[id]?.failures ?: 0) + 1
        states[id] = State(n, now + DELAYS_MS[(n - 1).coerceAtMost(DELAYS_MS.lastIndex)])
    }

    @Synchronized fun succeeded(id: String) { states.remove(id) }

    /** Vrai si [id] peut être tenté maintenant (jamais échoué, ou délai écoulé). */
    @Synchronized fun allowed(id: String, now: Long) = states[id]?.let { now >= it.nextAt } ?: true

    /** Un serveur en attente dont le délai est écoulé : la prochaine synchro relit la configuration en entier. */
    @Synchronized fun anyDue(now: Long) = states.values.any { now >= it.nextAt }

    /** Minutes avant le prochain essai de [id] (au moins 1), ou null s'il n'est pas en attente. */
    @Synchronized fun minutesLeft(id: String, now: Long): Int? = states[id]?.let { ((it.nextAt - now + 59_999) / 60_000).toInt().coerceAtLeast(1) }

    @Synchronized fun pendingIds(): Set<String> = states.keys.toSet()

    /** Oublie les serveurs retirés du compte (sinon une relecture complète serait demandée indéfiniment). */
    @Synchronized fun retain(ids: Set<String>) { states.keys.retainAll(ids) }

    companion object {
        val DELAYS_MS = longArrayOf(2 * 60_000L, 10 * 60_000L, 30 * 60_000L, 2 * 3_600_000L)
    }
}

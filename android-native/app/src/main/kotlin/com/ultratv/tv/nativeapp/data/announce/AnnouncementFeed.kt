package com.ultratv.tv.nativeapp.data.announce

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'une lecture de GET /api/announcements. */
sealed interface FetchOutcome {
    data class Ok(val items: List<Announcement>) : FetchOutcome
    /** Aucun réseau sur l'appareil. */
    data object Offline : FetchOutcome
    /** Réseau présent mais Worker injoignable ou en erreur. */
    data object Unreachable : FetchOutcome
}

/**
 * Alertes diffusées, partagées entre la vérification automatique (lancement, retour au premier plan, toutes les
 * 15 minutes : [com.ultratv.tv.nativeapp.ui.common.AnnouncementViewModel]) et le bouton « Rafraîchir » de
 * Réglages › Messages. Une seule lecture à la fois ; une lecture réussie met à jour [items] et [lastCheckAt], un échec
 * garde les alertes connues.
 */
@Singleton
class AnnouncementFeed(
    private val fetcher: suspend (String) -> FetchOutcome,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFetched: (List<Announcement>) -> Unit = {},
) {
    @Inject constructor(repo: AnnouncementRepository) : this(
        fetcher = { repo.fetchOutcome(it) },
        // Identifiants « vus » encore utiles : ceux des alertes toujours diffusées.
        onFetched = { got -> repo.saveSeen(AnnouncementPlan.pruneSeen(repo.seen(), got, true)) },
    )

    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<Announcement>>(emptyList())
    val items: StateFlow<List<Announcement>> = _items.asStateFlow()
    private val _lastCheckAt = MutableStateFlow<Long?>(null)
    /** Heure de la dernière vérification réussie (automatique ou manuelle), null tant qu'aucune n'a abouti. */
    val lastCheckAt: StateFlow<Long?> = _lastCheckAt.asStateFlow()

    suspend fun check(audience: String): FetchOutcome = mutex.withLock {
        val r = fetcher(audience)
        if (r is FetchOutcome.Ok) {
            onFetched(r.items)
            _items.value = r.items
            _lastCheckAt.value = clock()
        }
        r
    }
}

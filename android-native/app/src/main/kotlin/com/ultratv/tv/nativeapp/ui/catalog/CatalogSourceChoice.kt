package com.ultratv.tv.nativeapp.ui.catalog

import com.ultratv.tv.nativeapp.data.mediaserver.MediaServer
import com.ultratv.tv.nativeapp.data.nas.Nas
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Source affichée dans Films et Séries : par défaut la source « active » (IPTV), sinon celle choisie par l'utilisateur parmi
 * les serveurs multimédias et partages NAS. Les serveurs Plex / Jellyfin / Emby / NAS ne dépendent donc plus de la source active.
 * Logique pure, testée à part.
 */
object CatalogSourceChoice {
    /** Choix de l'utilisateur pour cette session (null = source active). */
    val id = MutableStateFlow<Long?>(null)

    /** Bibliothèque (catégorie) à ouvrir à la prochaine arrivée sur Films / Séries (« Tout voir » d'une rangée de bibliothèque) ; consommée une fois. */
    val pendingCategory = MutableStateFlow<String?>(null)

    /** Source à afficher : le choix s'il existe encore, sinon la source active, sinon la première. [providers] = (id, active). */
    fun resolve(providers: List<Pair<Long, Boolean>>, choice: Long?): Long? =
        providers.firstOrNull { it.first == choice }?.first ?: (providers.firstOrNull { it.second } ?: providers.firstOrNull())?.first

    /**
     * Filtres de source proposés : vide s'il n'y a aucun serveur multimédia / NAS (rien ne change pour un utilisateur IPTV seul) ;
     * sinon la source active puis chaque bibliothèque. [providers] = (id, nom, (type, active)).
     */
    fun choices(providers: List<Triple<Long, String, Pair<String, Boolean>>>): List<Pair<Long, String>> {
        fun isLib(kind: String) = MediaServer.isMediaKind(kind) || Nas.isNasKind(kind)
        if (providers.none { isLib(it.third.first) }) return emptyList()
        val primary = providers.filter { !isLib(it.third.first) }.let { l -> l.firstOrNull { it.third.second } ?: l.firstOrNull() }
        return (listOfNotNull(primary) + providers.filter { isLib(it.third.first) }).map { it.first to it.second }
    }
}

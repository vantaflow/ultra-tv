package com.ultratv.tv.nativeapp.ui.search

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Recherche limitée à une rubrique (bouton « Recherche » de Films ou de Séries) : seule la table de la rubrique est
 * interrogée — plus rapide, et sans chaînes ni programmes du guide. Posé juste avant d'ouvrir l'écran Recherche, lu une
 * fois à son ouverture ; la recherche générale (menu, touche Recherche) n'en a pas.
 */
object SearchScope {
    val pending = MutableStateFlow<SearchTab?>(null)

    /** Valeur pour [com.ultratv.tv.nativeapp.data.repo.CatalogRepository.search] (`only`). */
    fun onlyOf(tab: SearchTab?): String? = when (tab) { SearchTab.MOVIES -> "movie"; SearchTab.SERIES -> "series"; else -> null }
}

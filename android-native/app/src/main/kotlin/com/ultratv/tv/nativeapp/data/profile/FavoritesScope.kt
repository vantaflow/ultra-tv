package com.ultratv.tv.nativeapp.data.profile

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity

/**
 * Écran Favoris : quelles sources lire, et que garder dans la vue « Toutes les sources ». Logique PURE (testée sur la JVM).
 */
object FavoritesScope {
    /** Le choix « Toutes les sources » n'a de sens qu'avec au moins deux sources visibles pour le profil. */
    fun canMix(visibleCount: Int) = visibleCount >= 2

    /** Sources dont on lit les favoris : toutes les sources visibles du profil si le choix est fait ET possible, sinon la seule source active. */
    fun providerIds(all: List<ProviderEntity>, names: List<String>?, chosen: Boolean): List<Long> {
        val visible = ProfileSourcesRules.visible(all, names)
        if (chosen && canMix(visible.size)) return visible.map { it.id }
        return listOfNotNull((all.firstOrNull { it.active } ?: all.firstOrNull())?.id)
    }

    /** Noms des sources par identifiant, SEULEMENT en vue mélangée (sinon null : aucune pastille de source). */
    fun sourceNames(all: List<ProviderEntity>, ids: List<Long>): Map<Long, String>? =
        if (ids.size < 2) null else all.filter { it.id in ids }.associate { it.id to it.name.trim() }

    /** Clé d'une catégorie masquée (même codage que HiddenCategoriesStore.keyFor) : « LIVE:fournisseur:catégorie ». */
    private fun hiddenKey(c: ChannelEntity, cat: String) = "LIVE:${c.providerId}:$cat"

    /** Retire les chaînes dont la catégorie est masquée pour le profil (comme dans le Direct) ; sans catégorie : conservée. */
    fun dropHidden(list: List<ChannelEntity>, hidden: Set<String>): List<ChannelEntity> =
        if (hidden.isEmpty()) list else list.filter { c -> c.categoryId?.let { hiddenKey(c, it) !in hidden } ?: true }
}

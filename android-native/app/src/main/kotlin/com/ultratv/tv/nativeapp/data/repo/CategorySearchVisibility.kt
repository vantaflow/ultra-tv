package com.ultratv.tv.nativeapp.data.repo

/**
 * Category IDs excluded from catalogue search for one provider and content kind.
 *
 * Profile keys are stored as KIND:providerId:remoteId. Provider-disabled category IDs
 * are global and are supplied separately by CategoryDao.
 */
internal object CategorySearchVisibility {
    fun excludedRemoteIds(
        kind: String,
        providerId: Long,
        profileHiddenKeys: Set<String>,
        disabledRemoteIds: Collection<String>,
        /** Catégories verrouillées par le code parental : jamais d'affiche ni de titre dans la recherche. */
        lockedRemoteIds: Collection<String> = emptyList(),
    ): List<String> {
        val prefix = "$kind:$providerId:"
        val profileHidden = profileHiddenKeys.mapNotNull { key ->
            key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
        }
        return (profileHidden + disabledRemoteIds + lockedRemoteIds).distinct()
    }
}

/** Garde les éléments dont la catégorie n'est pas exclue (catégorie inconnue : gardé). Sans exclusion : liste inchangée. */
internal inline fun <T> List<T>.visibleIn(excluded: Set<String>, category: (T) -> String?): List<T> =
    if (excluded.isEmpty()) this else filter { item -> category(item).let { it == null || it !in excluded } }

/** Profil Enfants : aucun titre qui ressemble à un titre adulte (recherche, Ma liste), même hors catégorie adulte. */
internal inline fun <T> List<T>.withoutAdultTitles(kids: Boolean, title: (T) -> String?): List<T> =
    if (!kids) this else filter { item -> title(item)?.let { !com.ultratv.tv.nativeapp.data.profile.AdultHeuristic.isAdult(it) } ?: true }

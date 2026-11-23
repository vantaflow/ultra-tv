package com.ultratv.tv.nativeapp.ui.home

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.profile.AdultHeuristic

/**
 * Ce que l'ACCUEIL ne montre jamais, sur TV comme sur téléphone et tablette (écran vu par toute la famille) : aucun titre
 * ni aucune chaîne d'une catégorie masquée pour le profil actif (catégories adultes comprises pour un profil enfant),
 * verrouillée par code, désactivée, ou dont le nom est celui d'une catégorie adulte ; aucun titre qui ressemble à un titre
 * adulte ; aucune chaîne verrouillée. Même règle que la chaîne « Nouveautés » d'Android TV (voir NewsChannelPlan.visible),
 * appliquée à toutes les rangées : Top 10, mieux notés, derniers ajouts, reprises, Trakt, bandeau « À la une »…
 *
 * Clés de catégorie : `KIND:source:catégorie` (KIND = LIVE, MOVIE ou SERIES), comme HiddenCategoriesStore.
 */
data class HomeGuard(
    val blockedCategories: Set<String> = emptySet(),
    /** Chaînes verrouillées une à une (`source:identifiant`, voir LockedChannelsStore). */
    val lockedChannels: Set<String> = emptySet(),
    /** Faux pour l'historique d'un profil adulte : un titre qui « ressemble » à de l'adulte reste dans ses récentes. */
    val adultTitles: Boolean = true,
) {
    fun allows(kind: String, providerId: Long, categoryId: String?, title: String?): Boolean =
        (categoryId.isNullOrEmpty() || key(kind, providerId, categoryId) !in blockedCategories) &&
            (!adultTitles || title.isNullOrBlank() || !AdultHeuristic.isAdult(title))

    fun allowsChannel(c: ChannelEntity): Boolean =
        "${c.providerId}:${c.remoteId}" !in lockedChannels && allows("LIVE", c.providerId, c.categoryId, c.name)

    companion object {
        val NONE = HomeGuard()

        /**
         * Historique de l'utilisateur (« Dernières chaînes regardées ») : il montre ce qu'il a regardé, même dans une catégorie
         * masquée ou désactivée (sur une box, 1 549 catégories désactivées pour 135 actives faisaient disparaître des chaînes
         * réellement regardées). Seuls le verrou parental (catégorie ou chaîne) et, pour un profil Enfants, l'adulte l'excluent.
         */
        fun forHistory(categories: List<CategoryEntity>, lockedChannels: Set<String>, kids: Boolean): HomeGuard = HomeGuard(
            blockedCategories = categories.filter { it.locked || (kids && AdultHeuristic.isAdult(it.name)) }
                .map { key(it.kind, it.providerId, it.remoteId) }.toSet(),
            lockedChannels = lockedChannels,
            adultTitles = kids,
        )

        fun key(kind: String, providerId: Long, categoryId: String) = "$kind:$providerId:$categoryId"

        /**
         * [hidden] = catégories masquées du profil actif (adultes déjà comprises pour un profil enfant) ; [categories] = catégories
         * de la source (tous types) : verrouillées, désactivées et adultes par leur nom sont écartées en plus.
         */
        fun of(hidden: Set<String>, categories: List<CategoryEntity>, lockedChannels: Set<String>): HomeGuard = HomeGuard(
            blockedCategories = hidden + categories.filter { it.locked || !it.enabled || AdultHeuristic.isAdult(it.name) }
                .map { key(it.kind, it.providerId, it.remoteId) },
            lockedChannels = lockedChannels,
        )
    }
}

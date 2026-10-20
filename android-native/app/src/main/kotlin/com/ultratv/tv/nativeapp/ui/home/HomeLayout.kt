package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.runtime.Immutable

/**
 * Rangées de l'accueil que l'utilisateur peut masquer et réordonner (Réglages › Affichage › Rangées de l'accueil).
 * Les cinq listes personnelles Trakt forment UN bloc (elles suivent l'ordre de Trakt). L'ordre de déclaration est
 * l'ordre par défaut, celui de la maquette.
 */
enum class HomeBlock(val sections: List<HomeSectionId>) {
    RESUME(listOf(HomeSectionId.RESUME)),
    TRAKT_UP_NEXT(listOf(HomeSectionId.TRAKT_UP_NEXT)),
    TRAKT_WATCHLIST(listOf(HomeSectionId.TRAKT_WATCHLIST)),
    TRAKT_RECOMMENDED(listOf(HomeSectionId.TRAKT_RECOMMENDED)),
    TRAKT_TRENDING(listOf(HomeSectionId.TRAKT_TRENDING)),
    TRAKT_POPULAR(listOf(HomeSectionId.TRAKT_POPULAR)),
    TRAKT_LISTS(TRAKT_LIST_SECTIONS),
    RECENT_CHANNELS(listOf(HomeSectionId.RECENT_CHANNELS)),
    LATEST_MOVIES(listOf(HomeSectionId.LATEST_MOVIES)),
    LATEST_SERIES(listOf(HomeSectionId.LATEST_SERIES)),
    CHANNELS(listOf(HomeSectionId.CHANNELS)),
}

/** Bloc réglable auquel appartient une rangée. */
val HomeSectionId.block: HomeBlock get() = HomeBlock.entries.first { this in it.sections }

/**
 * Disposition de l'accueil : ordre COMPLET des blocs (tous présents une fois) et blocs masqués.
 * Persistée en deux CSV de noms d'enum ; une valeur inconnue (version plus récente) est ignorée, un bloc absent
 * (ajouté par une mise à jour) est inséré à sa place par défaut, juste après son prédécesseur par défaut.
 */
@Immutable
data class HomeLayout(val order: List<HomeBlock>, val hidden: Set<HomeBlock>) {

    /** Rangées à afficher : [present] (rangées non vides, dans n'importe quel ordre) triées selon [order], sans les masquées. */
    fun apply(present: List<HomeSectionId>): List<HomeSectionId> {
        if (this == DEFAULT) return HomeSectionId.entries.filter { it in present }
        val set = present.toSet()
        return order.filter { it !in hidden }.flatMap { b -> b.sections.filter { it in set } }
    }

    fun isVisible(b: HomeBlock) = b !in hidden
    fun toggle(b: HomeBlock) = copy(hidden = if (b in hidden) hidden - b else hidden + b)

    /** Déplace [b] de [delta] rangs (négatif = vers le haut) ; sans effet hors des bornes. */
    fun move(b: HomeBlock, delta: Int): HomeLayout {
        val i = order.indexOf(b); val j = i + delta
        if (i < 0 || j !in order.indices) return this
        val m = order.toMutableList(); m.removeAt(i); m.add(j, b)
        return copy(order = m)
    }

    fun orderCsv(): String = if (order == HomeBlock.entries) "" else order.joinToString(",") { it.name }
    fun hiddenCsv(): String = hidden.sortedBy { it.ordinal }.joinToString(",") { it.name }

    companion object {
        val DEFAULT = HomeLayout(HomeBlock.entries.toList(), emptySet())

        private fun names(csv: String): List<HomeBlock> =
            csv.split(',').mapNotNull { n -> n.trim().takeIf { it.isNotEmpty() }?.let { t -> HomeBlock.entries.firstOrNull { it.name == t } } }.distinct()

        /** Lecture tolérante + fusion avec les blocs connus (voir la classe). */
        fun parse(orderCsv: String?, hiddenCsv: String?): HomeLayout {
            val saved = names(orderCsv.orEmpty())
            val order = saved.toMutableList()
            HomeBlock.entries.forEachIndexed { i, b ->
                if (b in order) return@forEachIndexed
                // Après le plus proche prédécesseur par défaut déjà placé ; en tête s'il n'y en a aucun.
                val prev = (i - 1 downTo 0).map { HomeBlock.entries[it] }.firstOrNull { it in order }
                order.add(if (prev == null) 0 else order.indexOf(prev) + 1, b)
            }
            return HomeLayout(order, names(hiddenCsv.orEmpty()).toSet())
        }
    }
}

package com.ultratv.tv.nativeapp.ui.catalog

/**
 * Tri et filtres de la grille Films / Séries (puces sous les catégories). Valeurs simples passées telles quelles aux
 * requêtes Room ([sortCode], [minRating], [yearFrom], [yearTo] : 0 = sans filtre). Logique pure, testée à part.
 */
data class CatalogFilter(val sort: Sort = Sort.PROVIDER, val minRating: Int = 0, val period: Period = Period.ALL) {
    enum class Sort(val code: Int) { PROVIDER(0), RECENT(1), RATING(2) }

    /** Décennies proposées ; [from] / [to] inclusifs, 0 = borne ouverte. */
    enum class Period(val from: Int, val to: Int) { ALL(0, 0), Y2020S(2020, 0), Y2010S(2010, 2019), Y2000S(2000, 2009), Y1990S(1990, 1999), OLDER(0, 1989) }

    val sortCode get() = sort.code
    val yearFrom get() = period.from
    val yearTo get() = period.to

    /** Un filtre restreint le catalogue : la vue « Tous » passe alors de rangées par catégorie à une grille filtrée. */
    val narrows get() = minRating > 0 || period != Period.ALL || sort != Sort.PROVIDER

    companion object {
        val DEFAULT = CatalogFilter()
        /** Seuils de note proposés (0 = toutes). */
        val RATINGS = listOf(0, 6, 7, 8)
    }
}

package com.ultratv.tv.nativeapp.ui.catalog

/** Source des catégories des écrans Films / Séries. */
enum class CategorySource(val key: String) {
    GENRES("genres"), PROVIDER("provider");

    companion object { fun parse(raw: String?) = entries.firstOrNull { it.key == raw } ?: GENRES }
}

/** Affichage : liste de catégories + grille (TV) ou rangées par catégorie (affichage d'origine, toujours utilisé en tactile). */
enum class CatalogLayout(val key: String) {
    SPLIT("split"), ROWS("rows");

    companion object { fun parse(raw: String?) = entries.firstOrNull { it.key == raw } ?: SPLIT }
}

/** Réglages mémorisés des écrans Films / Séries (hors tri, porté par [CatalogFilter]). */
data class CatalogLayoutPrefs(val source: CategorySource = CategorySource.GENRES, val layout: CatalogLayout = CatalogLayout.SPLIT, val columns: Int = 0)

/** Logique pure de l'écran : nombre de colonnes, rotation des choix de la barre. Testée à part. */
object CatalogLayoutRules {
    /** Colonnes proposées : 0 = Auto, sinon 4 / 5 / 6. */
    val COLUMN_CHOICES = listOf(0, 4, 5, 6)
    const val AUTO_SPLIT = 5
    const val AUTO_ROWS = 7

    /** Colonnes de la grille. Tactile : toujours adaptatif ([touchColumns]). Sinon réglage explicite, ou 5 (liste + grille) / 7 (rangées). */
    fun columns(setting: Int, split: Boolean, touch: Boolean, touchColumns: Int): Int = when {
        touch -> touchColumns
        setting in 4..6 -> setting
        split -> AUTO_SPLIT
        else -> AUTO_ROWS
    }

    /** Libellé « Auto (5) » : colonne choisie, ou celle que l'automatique retient. */
    fun effectiveForLabel(setting: Int, split: Boolean): Int = columns(setting, split, false, 0)

    /** Élément suivant d'une liste, en boucle (choix par pastilles à OK). Valeur absente : le premier. */
    fun <T> next(options: List<T>, current: T): T {
        val i = options.indexOf(current)
        return options[(i + 1).mod(options.size)]
    }

    /** Colonne vers laquelle la grille bascule le focus : vrai pour la première colonne (touche gauche → liste). */
    fun isFirstColumn(index: Int, columns: Int) = columns > 0 && index % columns == 0
}

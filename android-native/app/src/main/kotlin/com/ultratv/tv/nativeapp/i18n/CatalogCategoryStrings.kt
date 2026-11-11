package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.data.repo.Genre
import com.ultratv.tv.nativeapp.ui.catalog.CatalogLayout
import com.ultratv.tv.nativeapp.ui.catalog.CategorySource
import com.ultratv.tv.nativeapp.R

/** Textes des écrans Films / Séries « catégories + grille » et des genres automatiques (EN / FR / ES / AR). */

fun DesignStrings.catalogCategoriesTitle(movies: Boolean) =
    if (movies) tx(R.string.cat_catalog_categories_title_1)
    else tx(R.string.cat_catalog_categories_title_2)

fun DesignStrings.categoriesCount(n: Int) = tx(R.string.cat_categories_count, n)

fun DesignStrings.catalogSourceName(s: CategorySource) = when (s) {
    CategorySource.GENRES -> tx(R.string.cat_catalog_source_name_genres)
    CategorySource.PROVIDER -> tx(R.string.cat_catalog_source_name_provider)
}

fun DesignStrings.catalogLayoutName(l: CatalogLayout) = when (l) {
    CatalogLayout.SPLIT -> tx(R.string.cat_catalog_layout_name_split)
    CatalogLayout.ROWS -> tx(R.string.cat_catalog_layout_name_rows)
}

/** [setting] 0 = automatique ; [auto] = nombre de colonnes que l'automatique retient. */
fun DesignStrings.catalogColumnsName(setting: Int, auto: Int) =
    if (setting == 0) tx(R.string.cat_catalog_columns_name_1, auto)
    else tx(R.string.cat_catalog_columns_name_2, setting)

val DesignStrings.genreHint get() = tx(R.string.cat_genre_hint)

fun DesignStrings.genreDetecting(done: Int, total: Int, percent: Int): String {
    fun n(v: Int): String = String.format(java.util.Locale.ROOT, "%,d", v).replace(',', if (lang == AppLang.French || lang == AppLang.Spanish) ' ' else ',')
    val pct = if (lang == AppLang.French) "$percent %" else "$percent%"
    return tx(R.string.cat_n) + " ${n(done)} / ${n(total)} · $pct"
}

val DesignStrings.catalogEmptyDetecting get() = tx(R.string.cat_catalog_empty_detecting)

/** Nom localisé d'un genre automatique (code de [Genre]) ; un code inconnu est rendu tel quel. */
fun DesignStrings.genreName(code: String): String = when (runCatching { Genre.valueOf(code) }.getOrNull()) {
    Genre.ACTION -> tx(R.string.cat_genre_name_action)
    Genre.ADVENTURE -> tx(R.string.cat_genre_name_adventure)
    Genre.ANIMATION -> tx(R.string.cat_genre_name_animation)
    Genre.ANIME -> tx(R.string.cat_genre_name_anime)
    Genre.COMEDY -> tx(R.string.cat_genre_name_comedy)
    Genre.CRIME -> tx(R.string.cat_genre_name_crime)
    Genre.DOCUMENTARY -> tx(R.string.cat_genre_name_documentary)
    Genre.DRAMA -> tx(R.string.cat_genre_name_drama)
    Genre.FAMILY -> tx(R.string.cat_genre_name_family)
    Genre.FANTASY -> tx(R.string.cat_genre_name_fantasy)
    Genre.HISTORY -> tx(R.string.cat_genre_name_history)
    Genre.HORROR -> tx(R.string.cat_genre_name_horror)
    Genre.MUSIC -> tx(R.string.cat_genre_name_music)
    Genre.MYSTERY -> tx(R.string.cat_genre_name_mystery)
    Genre.ROMANCE -> tx(R.string.cat_genre_name_romance)
    Genre.SCIFI -> tx(R.string.cat_genre_name_scifi)
    Genre.THRILLER -> tx(R.string.cat_genre_name_thriller)
    Genre.WAR -> tx(R.string.cat_genre_name_war)
    Genre.WESTERN -> tx(R.string.cat_genre_name_western)
    Genre.OTHER -> tx(R.string.cat_genre_name_other)
    null -> code
}

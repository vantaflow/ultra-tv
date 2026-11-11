package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/**
 * Libellés du nouvel accueil (« À la une » pleine largeur, raccourcis, rangée Direct, Top 10).
 * Fichier à part (extensions de [DesignStrings]) : ni le constructeur de [Strings] ni [DesignStrings] ne grossissent.
 */

val DesignStrings.homeMoreInfo get() = tx(R.string.home2_home_more_info)
val DesignStrings.homeLiveNow get() = tx(R.string.home2_home_live_now)
val DesignStrings.homeTop10 get() = tx(R.string.home2_home_top10)
val DesignStrings.homeShortcutsRow get() = tx(R.string.home2_home_shortcuts_row)
val DesignStrings.homeGuideSubtitle get() = tx(R.string.home2_home_guide_subtitle)

fun DesignStrings.homeChannelsCount(n: Int, count: String) = tp(R.plurals.home2_home_channels_count, n, count)

fun DesignStrings.homeMoviesCount(n: Int, count: String) = tp(R.plurals.home2_home_movies_count, n, count)

fun DesignStrings.homeSeriesCount(n: Int, count: String) = tp(R.plurals.home2_home_series_count, n, count)

fun DesignStrings.homeFavoritesCount(n: Int, count: String) = tp(R.plurals.home2_home_favorites_count, n, count)

package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.nav.StartScreen
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.TextSize
import com.ultratv.tv.nativeapp.ui.home.HomeBlock
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.data.trakt.forTracker

/**
 * Libellés de la personnalisation (accent, taille du texte, rangées de l'accueil, écran d'ouverture) et de l'en-tête
 * de la liste Direct. Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */

private fun DesignStrings.count(n: Int): String = java.text.NumberFormat.getIntegerInstance(locale).format(n)

// ── En-tête de la liste Direct ──

/** « 40 chaînes » avec le bon pluriel (arabe : singulier, duel, pluriel 3–10, singulier accusatif au-delà). */
fun DesignStrings.channelCount(n: Int): String {
    val f = count(n)
    return tp(R.plurals.perso_channel_count, n, f)
}

fun DesignStrings.sectionCount(n: Int): String {
    val f = count(n)
    return tp(R.plurals.perso_section_count, n, f)
}

/**
 * Partie « compteur » de l'en-tête Direct, toujours dans la langue de l'interface : « 40 chaînes », suivie du nombre
 * de sections seulement s'il y en a plusieurs. Le nom de la catégorie est affiché à part, tel quel (jamais mêlé à ce
 * texte dans une même chaîne : un nom arabe dans une phrase française déplaçait les nombres à l'affichage).
 */
fun DesignStrings.liveHeaderCount(channels: Int, sections: Int): String =
    if (sections > 1) "${channelCount(channels)} · ${sectionCount(sections)}" else channelCount(channels)

// ── Réglages › Affichage ──

val DesignStrings.personalization get() = tx(R.string.perso_personalization)
val DesignStrings.accentColor get() = tx(R.string.perso_accent_color)
val DesignStrings.textSizeLabel get() = tx(R.string.perso_text_size_label)
val DesignStrings.homeRows get() = tx(R.string.perso_home_rows)
fun DesignStrings.homeRowsSummary(shown: Int, total: Int) = tx(R.string.perso_home_rows_summary, shown, total)
val DesignStrings.homeRowsHint get() = tx(R.string.perso_home_rows_hint)
val DesignStrings.rowShown get() = tx(R.string.perso_row_shown)
val DesignStrings.rowHidden get() = tx(R.string.perso_row_hidden)
val DesignStrings.moveUp get() = tx(R.string.perso_move_up)
val DesignStrings.moveDown get() = tx(R.string.perso_move_down)
val DesignStrings.resetDefault get() = tx(R.string.perso_reset_default)
val DesignStrings.doneLabel get() = tx(R.string.perso_done_label)
val DesignStrings.traktListsRow get() = tx(R.string.perso_trakt_lists_row).forTracker()

fun DesignStrings.accentName(a: AccentChoice): String = when (a) {
    AccentChoice.RED -> tx(R.string.perso_accent_name_red)
    AccentChoice.BLUE -> tx(R.string.perso_accent_name_blue)
    AccentChoice.VIOLET -> tx(R.string.perso_accent_name_violet)
    AccentChoice.GREEN -> tx(R.string.perso_accent_name_green)
    AccentChoice.ORANGE -> tx(R.string.perso_accent_name_orange)
    AccentChoice.PINK -> tx(R.string.perso_accent_name_pink)
    AccentChoice.OCEAN -> tx(R.string.perso_accent_name_ocean)
    AccentChoice.EMERALD -> tx(R.string.perso_accent_name_emerald)
    AccentChoice.AMBER -> tx(R.string.perso_accent_name_amber)
    AccentChoice.TEAL -> tx(R.string.perso_accent_name_teal)
    AccentChoice.INDIGO -> tx(R.string.perso_accent_name_indigo)
    AccentChoice.FUCHSIA -> tx(R.string.perso_accent_name_fuchsia)
}

val DesignStrings.uiSizeLabel get() = tx(R.string.perso_ui_size_label)

fun DesignStrings.uiSizeName(s: com.ultratv.tv.nativeapp.ui.design.UiSize): String = when (s) {
    com.ultratv.tv.nativeapp.ui.design.UiSize.SMALL -> tx(R.string.perso_ui_size_small)
    com.ultratv.tv.nativeapp.ui.design.UiSize.NORMAL -> tx(R.string.perso_ui_size_normal)
    com.ultratv.tv.nativeapp.ui.design.UiSize.LARGE -> tx(R.string.perso_ui_size_large)
    com.ultratv.tv.nativeapp.ui.design.UiSize.XLARGE -> tx(R.string.perso_ui_size_xlarge)
}

val DesignStrings.screenEdgesLabel get() = tx(R.string.perso_screen_edges_label)
val DesignStrings.screenEdgesHint get() = tx(R.string.perso_screen_edges_hint)

fun DesignStrings.screenEdgesName(s: com.ultratv.tv.nativeapp.ui.design.ScreenEdges): String = when (s) {
    com.ultratv.tv.nativeapp.ui.design.ScreenEdges.NONE -> tx(R.string.perso_screen_edges_none)
    com.ultratv.tv.nativeapp.ui.design.ScreenEdges.SMALL -> tx(R.string.perso_screen_edges_small)
    com.ultratv.tv.nativeapp.ui.design.ScreenEdges.MEDIUM -> tx(R.string.perso_screen_edges_medium)
    com.ultratv.tv.nativeapp.ui.design.ScreenEdges.LARGE -> tx(R.string.perso_screen_edges_large)
}

fun DesignStrings.textSizeName(s: TextSize): String = when (s) {
    TextSize.NORMAL -> tx(R.string.perso_text_size_name_normal)
    TextSize.LARGE -> tx(R.string.perso_text_size_name_large)
    TextSize.XLARGE -> tx(R.string.perso_text_size_name_xlarge)
}

fun DesignStrings.startScreenName(s: StartScreen): String = when (s) {
    StartScreen.HOME -> tx(R.string.perso_start_screen_name_home)
    StartScreen.LIVE -> tx(R.string.perso_start_screen_name_live)
    StartScreen.MOVIES -> tx(R.string.perso_start_screen_name_movies)
    StartScreen.SERIES -> tx(R.string.perso_start_screen_name_series)
    StartScreen.GUIDE -> tx(R.string.perso_start_screen_name_guide)
    StartScreen.LAST_CHANNEL -> tx(R.string.perso_start_screen_name_last_channel)
    StartScreen.LAST_SCREEN -> tx(R.string.perso_start_screen_name_last_screen)
}

/** Explication de « Ouvrir sur » : ce que chaque choix fait et ce qui n'est jamais rouvert. */
val DesignStrings.startScreenHint get() = tx(R.string.perso_start_screen_hint)

fun DesignStrings.homeBlockName(b: HomeBlock): String = when (b) {
    HomeBlock.RESUME -> continueWatching
    HomeBlock.TRAKT_UP_NEXT -> traktUpNext
    HomeBlock.TRAKT_AVAILABLE -> traktAvailable
    HomeBlock.NEW_EPISODES -> extNewEpisodes
    HomeBlock.LIBRARIES -> mlibHubTitle
    HomeBlock.TRAKT_WATCHLIST -> traktWatchlist
    HomeBlock.TRAKT_RECOMMENDED -> traktRecommended
    HomeBlock.TRAKT_TRENDING -> traktTrending
    HomeBlock.TRAKT_POPULAR -> traktPopular
    HomeBlock.TRAKT_LISTS -> traktListsRow
    HomeBlock.RECENT_CHANNELS -> recentlyWatched
    HomeBlock.LATEST_MOVIES -> latestMovies
    HomeBlock.LATEST_SERIES -> latestSeries
    HomeBlock.BECAUSE_WATCHED -> becauseWatchedBlock
    HomeBlock.TOP_MOVIES -> topRatedMovies
    HomeBlock.TOP_SERIES -> topRatedSeries
    HomeBlock.CHANNELS -> directTitle
    HomeBlock.SHORTCUTS -> homeShortcutsRow
    HomeBlock.TOP10 -> homeTop10
}

// ── Chaînes de l'écran d'accueil Android TV (Réglages › Affichage, TV seulement) ──

val DesignStrings.tvChannelsLabel get() = tx(R.string.tvch_label)
val DesignStrings.tvChannelsHint get() = tx(R.string.tvch_hint)
fun DesignStrings.tvChannelsCount(n: Int) = tx(R.string.tvch_count, n)
val DesignStrings.tvChannelsSportNeeds get() = tx(R.string.tvch_sport_needs)
val DesignStrings.tvChannelsCategories get() = tx(R.string.tvch_categories)
val DesignStrings.tvChannelsCategoriesHint get() = tx(R.string.tvch_categories_hint)
val DesignStrings.tvChannelsAddCategory get() = tx(R.string.tvch_add_category)
val DesignStrings.tvChannelsNoCategory get() = tx(R.string.tvch_no_category)

/** Nom d'une chaîne d'accueil dans Réglages (les trois historiques gardent leur nom publié). */
fun DesignStrings.tvChannelName(c: com.ultratv.tv.nativeapp.data.tv.TvHomeChannel): String = when (c) {
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.RECENT -> recentChannelsName
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.FAVORITES -> tx(R.string.tvch_favorites)
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.NEWS -> newsChannelName
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.NEW_MOVIES -> tx(R.string.tvch_new_movies)
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.NEW_SERIES -> tx(R.string.tvch_new_series)
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.TOP10 -> tx(R.string.tvch_top10)
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.TOP_RATED_MOVIES -> tx(R.string.tvch_top_movies)
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.MY_LIST -> tx(R.string.tvch_my_list)
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.SPORT_LIVE -> tx(R.string.tvch_sport)
}

/** Nom publié des chaînes activables (sans précision entre parenthèses : « Ma liste », pas « Ma liste (films…) »). */
fun DesignStrings.tvChannelPublishedName(c: com.ultratv.tv.nativeapp.data.tv.TvHomeChannel): String = when (c) {
    com.ultratv.tv.nativeapp.data.tv.TvHomeChannel.MY_LIST -> tx(R.string.tvch_my_list).substringBefore(" (").trim()
    else -> tvChannelName(c)
}

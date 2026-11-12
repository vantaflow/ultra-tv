package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.nav.StartScreen
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.TextSize
import com.ultratv.tv.nativeapp.ui.home.HomeBlock
import com.ultratv.tv.nativeapp.R

/**
 * Libellés de la personnalisation (accent, taille du texte, rangées de l'accueil, écran d'ouverture) et de l'en-tête
 * de la liste Direct. Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */

private fun count(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)

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
val DesignStrings.traktListsRow get() = tx(R.string.perso_trakt_lists_row)

fun DesignStrings.accentName(a: AccentChoice): String = when (a) {
    AccentChoice.RED -> tx(R.string.perso_accent_name_red)
    AccentChoice.BLUE -> tx(R.string.perso_accent_name_blue)
    AccentChoice.VIOLET -> tx(R.string.perso_accent_name_violet)
    AccentChoice.GREEN -> tx(R.string.perso_accent_name_green)
    AccentChoice.ORANGE -> tx(R.string.perso_accent_name_orange)
    AccentChoice.PINK -> tx(R.string.perso_accent_name_pink)
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
}

fun DesignStrings.homeBlockName(b: HomeBlock): String = when (b) {
    HomeBlock.RESUME -> continueWatching
    HomeBlock.TRAKT_UP_NEXT -> traktUpNext
    HomeBlock.NEW_EPISODES -> extNewEpisodes
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

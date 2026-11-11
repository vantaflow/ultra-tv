package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/**
 * Libellés Trakt « complets » (gestes depuis les fiches, À suivre, listes, réglages).
 * Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */

// Fiches : watchlist, vu / non vu, note
val DesignStrings.traktAddWatchlist get() = tx(R.string.trakt_trakt_add_watchlist)
val DesignStrings.traktRemoveWatchlist get() = tx(R.string.trakt_trakt_remove_watchlist)
val DesignStrings.traktInWatchlist get() = tx(R.string.trakt_trakt_in_watchlist)
val DesignStrings.traktMarkWatched get() = tx(R.string.trakt_trakt_mark_watched)
val DesignStrings.traktMarkUnwatched get() = tx(R.string.trakt_trakt_mark_unwatched)
val DesignStrings.traktRate get() = tx(R.string.trakt_trakt_rate)
val DesignStrings.traktRateTitle get() = tx(R.string.trakt_trakt_rate_title)
val DesignStrings.traktRemoveRating get() = tx(R.string.trakt_trakt_remove_rating)
fun DesignStrings.traktYourRating(n: Int) = tx(R.string.trakt_trakt_your_rating, n)
val DesignStrings.traktButton get() = "Trakt"
fun DesignStrings.traktSeasonWatched(n: Int) = tx(R.string.trakt_trakt_season_watched, n)
fun DesignStrings.traktSeasonUnwatched(n: Int) = tx(R.string.trakt_trakt_season_unwatched, n)
val DesignStrings.traktEpisodesHint get() = tx(R.string.trakt_trakt_episodes_hint)

// Retours
val DesignStrings.traktUpdated get() = tx(R.string.trakt_trakt_updated)
val DesignStrings.traktUnmatched get() = tx(R.string.trakt_trakt_unmatched)
val DesignStrings.traktFailed get() = tx(R.string.trakt_trakt_failed)
val DesignStrings.traktNotLinked get() = tx(R.string.trakt_trakt_not_linked)

// Accueil
val DesignStrings.traktUpNext get() = tx(R.string.trakt_trakt_up_next)
fun DesignStrings.traktListRow(name: String) = tx(R.string.trakt_trakt_list_row, name)

// Réglages
val DesignStrings.traktSettings get() = "Trakt"
val DesignStrings.traktAutoWatched get() = tx(R.string.trakt_trakt_auto_watched)
val DesignStrings.traktAutoWatchedHintOn get() = tx(R.string.trakt_trakt_auto_watched_hint_on)
val DesignStrings.traktAutoWatchedHint get() = tx(R.string.trakt_trakt_auto_watched_hint)
val DesignStrings.traktListsHome get() = tx(R.string.trakt_trakt_lists_home)
val DesignStrings.traktListsHomeHint get() = tx(R.string.trakt_trakt_lists_home_hint)

package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.data.trakt.forTracker

/**
 * Libellés Trakt « complets » (gestes depuis les fiches, À suivre, listes, réglages).
 * Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */

// Fiches : watchlist, vu / non vu, note
val DesignStrings.traktAddWatchlist get() = tx(R.string.trakt_trakt_add_watchlist).forTracker()
val DesignStrings.traktRemoveWatchlist get() = tx(R.string.trakt_trakt_remove_watchlist).forTracker()
val DesignStrings.traktInWatchlist get() = tx(R.string.trakt_trakt_in_watchlist).forTracker()
val DesignStrings.traktMarkWatched get() = tx(R.string.trakt_trakt_mark_watched).forTracker()
val DesignStrings.traktMarkUnwatched get() = tx(R.string.trakt_trakt_mark_unwatched).forTracker()
val DesignStrings.traktRate get() = tx(R.string.trakt_trakt_rate).forTracker()
val DesignStrings.traktRateTitle get() = tx(R.string.trakt_trakt_rate_title).forTracker()
val DesignStrings.traktRemoveRating get() = tx(R.string.trakt_trakt_remove_rating).forTracker()
fun DesignStrings.traktYourRating(n: Int) = tx(R.string.trakt_trakt_your_rating, n).forTracker()
val DesignStrings.traktButton get() = "Trakt".forTracker()
fun DesignStrings.traktSeasonWatched(n: Int) = tx(R.string.trakt_trakt_season_watched, n).forTracker()
fun DesignStrings.traktSeasonUnwatched(n: Int) = tx(R.string.trakt_trakt_season_unwatched, n).forTracker()
val DesignStrings.traktEpisodesHint get() = tx(R.string.trakt_trakt_episodes_hint).forTracker()

// Retours
val DesignStrings.traktUpdated get() = tx(R.string.trakt_trakt_updated).forTracker()
val DesignStrings.traktUnmatched get() = tx(R.string.trakt_trakt_unmatched).forTracker()
val DesignStrings.traktFailed get() = tx(R.string.trakt_trakt_failed).forTracker()
val DesignStrings.traktNotLinked get() = tx(R.string.trakt_trakt_not_linked).forTracker()

// Accueil
val DesignStrings.traktUpNext get() = tx(R.string.trakt_trakt_up_next).forTracker()
fun DesignStrings.traktListRow(name: String) = tx(R.string.trakt_trakt_list_row, name).forTracker()

// Réglages
val DesignStrings.traktSettings get() = "Trakt".forTracker()
val DesignStrings.traktAutoWatched get() = tx(R.string.trakt_trakt_auto_watched).forTracker()
val DesignStrings.traktAutoWatchedHintOn get() = tx(R.string.trakt_trakt_auto_watched_hint_on).forTracker()
val DesignStrings.traktAutoWatchedHint get() = tx(R.string.trakt_trakt_auto_watched_hint).forTracker()
val DesignStrings.traktListsHome get() = tx(R.string.trakt_trakt_lists_home).forTracker()
val DesignStrings.traktListsHomeHint get() = tx(R.string.trakt_trakt_lists_home_hint).forTracker()

// Service de suivi au choix (Trakt, Simkl ou aucun) et « disponible de ta liste » (watchlist devenue disponible)
val DesignStrings.trackingTitle get() = tx(R.string.trakt_tracking_title)
val DesignStrings.trackingNone get() = tx(R.string.trakt_tracking_none)
val DesignStrings.trackingHint get() = tx(R.string.trakt_tracking_hint)
fun DesignStrings.trackingNotLinked(name: String) = tx(R.string.trakt_tracking_not_linked, name)
val DesignStrings.trackingUnsupported get() = tx(R.string.trakt_tracking_unsupported)
val DesignStrings.traktAvailable get() = tx(R.string.trakt_available_from_list)
val DesignStrings.traktAvailableChannel get() = tx(R.string.trakt_available_channel)

package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/**
 * Libellés du lot B1 (actions sur un programme, enregistrements programmés, fiches enrichies).
 * Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */

// Dialogue « programme » (GuideActions)
val DesignStrings.actReplay get() = tx(R.string.dsb1_act_replay)
val DesignStrings.actReplayHint get() = tx(R.string.dsb1_act_replay_hint)
val DesignStrings.actReplayNone get() = tx(R.string.dsb1_act_replay_none)
val DesignStrings.actWatchChannel get() = tx(R.string.dsb1_act_watch_channel)
val DesignStrings.actWatchChannelHint get() = tx(R.string.dsb1_act_watch_channel_hint)
val DesignStrings.actRemind get() = tx(R.string.dsb1_act_remind)
val DesignStrings.actRemindHint get() = tx(R.string.dsb1_act_remind_hint)
val DesignStrings.actRecord get() = tx(R.string.dsb1_act_record)
val DesignStrings.actRecordHint get() = tx(R.string.dsb1_act_record_hint)
val DesignStrings.actRecordThis get() = tx(R.string.dsb1_act_record_this)
val DesignStrings.actRecordSeries get() = tx(R.string.dsb1_act_record_series)
val DesignStrings.actRecordSeriesHint get() = tx(R.string.dsb1_act_record_series_hint)
val DesignStrings.yesterday get() = tx(R.string.dsb1_yesterday)
val DesignStrings.replayTag get() = tx(R.string.dsb1_replay_tag)
fun DesignStrings.replayBadge(days: Int) = tx(R.string.dsb1_replay_badge, days)

// Rappels et enregistrements : retours à l'utilisateur
val DesignStrings.remindSet get() = tx(R.string.dsb1_remind_set)
val DesignStrings.startsInOneMin get() = tx(R.string.dsb1_starts_in_one_min)
val DesignStrings.watchWord get() = tx(R.string.dsb1_watch_word)
fun DesignStrings.recScheduled(n: Int) = tp(R.plurals.dsb1_rec_scheduled, n, n)
val DesignStrings.recNoSpace get() = tx(R.string.dsb1_rec_no_space)
val DesignStrings.recNothing get() = tx(R.string.dsb1_rec_nothing)
val DesignStrings.recScheduledBadge get() = tx(R.string.dsb1_rec_scheduled_badge)
val DesignStrings.recConnectionBusy get() = tx(R.string.dsb1_rec_connection_busy)

// Fiches enrichies TMDB
val DesignStrings.trailerLabel get() = tx(R.string.dsb1_trailer_label)
val DesignStrings.tmdbAttribution get() = tx(R.string.dsb1_tmdb_attribution)

/** Locale de FORMATAGE (noms de jours, de langues, dates) alignée sur la langue de l'application, pas sur celle de l'appareil. Chiffres latins en arabe. */
val DesignStrings.locale: java.util.Locale get() = when (lang) {
    AppLang.French -> java.util.Locale.FRENCH
    AppLang.Spanish -> java.util.Locale("es")
    AppLang.Arabic -> java.util.Locale.forLanguageTag("ar-u-nu-latn")
    else -> java.util.Locale.ENGLISH
}

// Trakt : rangées de l'accueil (seulement ce qui est disponible dans la playlist) et marque « vu »
val DesignStrings.traktWatchlist get() = tx(R.string.dsb1_trakt_watchlist)
val DesignStrings.traktRecommended get() = tx(R.string.dsb1_trakt_recommended)
val DesignStrings.traktTrending get() = tx(R.string.dsb1_trakt_trending)
val DesignStrings.traktPopular get() = tx(R.string.dsb1_trakt_popular)
val DesignStrings.traktWatched get() = tx(R.string.dsb1_trakt_watched)

// Portail Stalker / Ministra (source par adresse MAC)
val DesignStrings.kindStalker get() = tx(R.string.dsb1_kind_stalker)
val DesignStrings.formSubStalker get() = tx(R.string.dsb1_form_sub_stalker)
val DesignStrings.fieldPortalUrl get() = tx(R.string.dsb1_field_portal_url)
val DesignStrings.fieldMac get() = tx(R.string.dsb1_field_mac)
val DesignStrings.macHint get() = tx(R.string.dsb1_mac_hint)
val DesignStrings.fieldPortalLogin get() = tx(R.string.dsb1_field_portal_login)
val DesignStrings.fieldPortalPassword get() = tx(R.string.dsb1_field_portal_password)
val DesignStrings.stalkerChecking get() = tx(R.string.dsb1_stalker_checking)
val DesignStrings.stalkerFailed get() = tx(R.string.dsb1_stalker_failed)

// Fiche film / série (en-tête plein écran, saisons à gauche, épisodes à droite)
val DesignStrings.typeMovie get() = tx(R.string.dsb1_type_movie)
val DesignStrings.typeSeries get() = tx(R.string.dsb1_type_series)
val DesignStrings.directorLabel get() = tx(R.string.dsb1_director_label)
val DesignStrings.favoritesShort get() = tx(R.string.dsb1_favorites_short)
val DesignStrings.similarTitles get() = tx(R.string.dsb1_similar_titles)
val DesignStrings.jumpToEpisode get() = tx(R.string.dsb1_jump_to_episode)
val DesignStrings.seasonsHelp get() = tx(R.string.dsb1_seasons_help)
val DesignStrings.longPressDownload get() = tx(R.string.dsb1_long_press_download)
fun DesignStrings.episodesCount(n: Int) = tp(R.plurals.dsb1_episodes_count, n, n)

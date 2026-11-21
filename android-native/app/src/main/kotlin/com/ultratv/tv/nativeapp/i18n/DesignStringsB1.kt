package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.data.trakt.forTracker

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
val DesignStrings.dataSources get() = tx(R.string.dsb1_data_sources)

/** Locale de FORMATAGE (noms de jours, de langues, dates) alignée sur la langue de l'application, pas sur celle de l'appareil. Chiffres latins en arabe. */
val DesignStrings.locale: java.util.Locale get() = designLocaleCache.getOrPut(lang) { designLocaleOf(lang) }

/** Une locale par langue, construite une fois (le getter était relu à chaque formatage). */
private val designLocaleCache = java.util.concurrent.ConcurrentHashMap<AppLang, java.util.Locale>()

private fun designLocaleOf(lang: AppLang): java.util.Locale = DisplayLocale.latin(when (lang) {
    AppLang.French -> java.util.Locale.FRENCH
    AppLang.Spanish -> java.util.Locale("es")
    AppLang.Arabic -> java.util.Locale.forLanguageTag("ar")
    AppLang.Polish -> java.util.Locale("pl")
    AppLang.PortugueseBr -> java.util.Locale("pt", "BR")
    AppLang.Romanian, AppLang.NorwegianBokmal, AppLang.Czech, AppLang.Finnish, AppLang.Danish, AppLang.Greek, AppLang.Thai, AppLang.Vietnamese, AppLang.Turkish -> java.util.Locale.forLanguageTag(lang.code)
    else -> java.util.Locale.ENGLISH
})

// Trakt : rangées de l'accueil (seulement ce qui est disponible dans la playlist) et marque « vu »
val DesignStrings.traktWatchlist get() = tx(R.string.dsb1_trakt_watchlist).forTracker()
val DesignStrings.traktRecommended get() = tx(R.string.dsb1_trakt_recommended).forTracker()
val DesignStrings.traktTrending get() = tx(R.string.dsb1_trakt_trending).forTracker()
val DesignStrings.traktPopular get() = tx(R.string.dsb1_trakt_popular).forTracker()
val DesignStrings.traktWatched get() = tx(R.string.dsb1_trakt_watched).forTracker()

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

// Serveurs multimédias personnels (Jellyfin / Emby) : « Jellyfin » et « Emby » sont des noms propres, jamais traduits
val DesignStrings.formSubMediaServer get() = tx(R.string.dsb1_ms_sub)
val DesignStrings.fieldMediaUrl get() = tx(R.string.dsb1_ms_url)
val DesignStrings.fieldMediaUser get() = tx(R.string.dsb1_ms_user)
val DesignStrings.fieldMediaPass get() = tx(R.string.dsb1_ms_pass)
val DesignStrings.fieldMediaKey get() = tx(R.string.dsb1_ms_key)
val DesignStrings.mediaChecking get() = tx(R.string.dsb1_ms_checking)
val DesignStrings.mediaFailed get() = tx(R.string.dsb1_ms_failed)
val DesignStrings.mediaHint get() = tx(R.string.dsb1_ms_hint)

// Plex (connexion par code PIN) : « Plex » est un nom propre, jamais traduit
val DesignStrings.formSubPlex get() = tx(R.string.dsb1_plex_sub)
val DesignStrings.plexOpen get() = tx(R.string.dsb1_plex_open)
val DesignStrings.plexWaiting get() = tx(R.string.dsb1_plex_waiting)
val DesignStrings.plexExpired get() = tx(R.string.dsb1_plex_expired)
val DesignStrings.plexFailed get() = tx(R.string.dsb1_plex_failed)
val DesignStrings.plexNoServer get() = tx(R.string.dsb1_plex_none)
val DesignStrings.plexUnreachable get() = tx(R.string.dsb1_plex_unreach)
val DesignStrings.plexRetry get() = tx(R.string.dsb1_plex_retry)
val DesignStrings.plexPick get() = tx(R.string.dsb1_plex_pick)
val DesignStrings.plexHint get() = tx(R.string.dsb1_plex_hint)
// Partage réseau (NAS, SMB) : « NAS » et « SMB » sont des sigles, jamais traduits
val DesignStrings.formSubNas get() = tx(R.string.dsb1_nas_sub)
val DesignStrings.fieldNasUrl get() = tx(R.string.dsb1_nas_url)
val DesignStrings.fieldNasUser get() = tx(R.string.dsb1_nas_user)
val DesignStrings.nasChecking get() = tx(R.string.dsb1_nas_checking)
val DesignStrings.nasFailed get() = tx(R.string.dsb1_nas_failed)
val DesignStrings.nasHint get() = tx(R.string.dsb1_nas_hint)

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

// Portail Stalker : identité de boîtier facultative (formulaire d'ajout).
val DesignStrings.stalkerIdentityToggle get() = tx(R.string.stalker_identity_toggle)
val DesignStrings.stalkerIdentityHint get() = tx(R.string.stalker_identity_hint)
val DesignStrings.stalkerFieldSerial get() = tx(R.string.stalker_field_serial)
val DesignStrings.stalkerFieldDeviceId get() = tx(R.string.stalker_field_device_id)
val DesignStrings.stalkerFieldDeviceId2 get() = tx(R.string.stalker_field_device_id2)
val DesignStrings.stalkerFieldSignature get() = tx(R.string.stalker_field_signature)

// Lecteur : écran d'erreur précis (cause, quoi faire), voir PlayErrorText.
val DesignStrings.errNetworkHint get() = tx(R.string.ds_err_network_hint)
val DesignStrings.errServerDown get() = tx(R.string.ds_err_server_down)
val DesignStrings.errServerDownHint get() = tx(R.string.ds_err_server_down_hint)
val DesignStrings.errTooMany get() = tx(R.string.ds_err_too_many)
val DesignStrings.errTooManyHint get() = tx(R.string.ds_err_too_many_hint)
val DesignStrings.errRejected get() = tx(R.string.ds_err_rejected)
val DesignStrings.errGenericHint get() = tx(R.string.ds_err_generic_hint)
val DesignStrings.errNotFoundHint get() = tx(R.string.ds_err_not_found_hint)
val DesignStrings.errFormatHint get() = tx(R.string.ds_err_format_hint)
val DesignStrings.errDecoder get() = tx(R.string.ds_err_decoder)
val DesignStrings.errDecoderHint get() = tx(R.string.ds_err_decoder_hint)
val DesignStrings.errNoPicture get() = tx(R.string.ds_err_no_picture)

// Accueil TV : bouton rond « Ma liste » sur l'image à la une (ajoute ou retire le titre des favoris).
val DesignStrings.heroMyList get() = tx(R.string.ds_hero_my_list)

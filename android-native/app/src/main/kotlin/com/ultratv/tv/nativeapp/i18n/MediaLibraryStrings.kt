package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Textes de l'assistant d'ajout « Ma bibliothèque » (3 étapes) et de l'écran « Mes bibliothèques » (14 langues, clés `mlib_*`). */
val DesignStrings.mlibStep1 get() = tx(R.string.mlib_step1)
val DesignStrings.mlibStep2 get() = tx(R.string.mlib_step2)
val DesignStrings.mlibStep3 get() = tx(R.string.mlib_step3)
val DesignStrings.mlibMy get() = tx(R.string.mlib_my)
val DesignStrings.mlibMySub get() = tx(R.string.mlib_my_sub)
val DesignStrings.mlibFindTitle get() = tx(R.string.mlib_find_title)
val DesignStrings.mlibFindLead get() = tx(R.string.mlib_find_lead)
val DesignStrings.mlibSearching get() = tx(R.string.mlib_searching)
val DesignStrings.mlibSearchAgain get() = tx(R.string.mlib_search_again)
val DesignStrings.mlibNone get() = tx(R.string.mlib_none)
val DesignStrings.mlibEnterAddress get() = tx(R.string.mlib_enter_address)
val DesignStrings.mlibNasBtn get() = tx(R.string.mlib_nas_btn)
val DesignStrings.mlibPlexBtn get() = tx(R.string.mlib_plex_btn)
val DesignStrings.mlibPickKind get() = tx(R.string.mlib_pick_kind)
val DesignStrings.mlibLibsUnknown get() = tx(R.string.mlib_libs_unknown)
val DesignStrings.mlibModeLogin get() = tx(R.string.mlib_mode_login)
val DesignStrings.mlibModeKey get() = tx(R.string.mlib_mode_key)
val DesignStrings.mlibConnect get() = tx(R.string.mlib_connect)
val DesignStrings.mlibPwNote get() = tx(R.string.mlib_pw_note)
val DesignStrings.mlibPlexTitle get() = tx(R.string.mlib_plex_title)
val DesignStrings.mlibPlexS1 get() = tx(R.string.mlib_plex_s1)
val DesignStrings.mlibPlexS2 get() = tx(R.string.mlib_plex_s2)
val DesignStrings.mlibSrvTitle get() = tx(R.string.mlib_srv_title)
val DesignStrings.mlibSrvLead get() = tx(R.string.mlib_srv_lead)
val DesignStrings.mlibOwner get() = tx(R.string.mlib_owner)
val DesignStrings.mlibShared get() = tx(R.string.mlib_shared)
val DesignStrings.mlibConnLocal get() = tx(R.string.mlib_conn_local)
val DesignStrings.mlibConnRemote get() = tx(R.string.mlib_conn_remote)
val DesignStrings.mlibConnRelay get() = tx(R.string.mlib_conn_relay)
val DesignStrings.mlibConnHint get() = tx(R.string.mlib_conn_hint)
val DesignStrings.mlibLibsTitle get() = tx(R.string.mlib_libs_title)
val DesignStrings.mlibLibsLead get() = tx(R.string.mlib_libs_lead)
val DesignStrings.mlibLibsMovies get() = tx(R.string.mlib_libs_movies)
val DesignStrings.mlibLibsSeries get() = tx(R.string.mlib_libs_series)
val DesignStrings.mlibLibsImport get() = tx(R.string.mlib_libs_import)
val DesignStrings.mlibLibsAll get() = tx(R.string.mlib_libs_all)
val DesignStrings.mlibHubTitle get() = tx(R.string.mlib_hub_title)
val DesignStrings.mlibHubRowHint get() = tx(R.string.mlib_hub_row_hint)
val DesignStrings.mlibHubEmpty get() = tx(R.string.mlib_hub_empty)
val DesignStrings.mlibStatusOnline get() = tx(R.string.mlib_status_online)
val DesignStrings.mlibStatusRelay get() = tx(R.string.mlib_status_relay)
val DesignStrings.mlibNasFolders get() = tx(R.string.mlib_nas_folders)
val DesignStrings.mlibNasAuto get() = tx(R.string.mlib_nas_auto)
fun DesignStrings.mlibPlexExpires(left: String) = tx(R.string.mlib_plex_expires, left)
fun DesignStrings.mlibStatusOffline(ago: String) = tx(R.string.mlib_status_offline, ago)
fun DesignStrings.mlibCounts(movies: Int, series: Int) = tx(R.string.mlib_counts, movies, series)

/** Serveur multimédia ajouté depuis le site mais non connecté par l'appareil (affiché en toast, réessayé à la synchro suivante). */
fun cloudMediaFailedText(lang: AppLang, names: String): String = I18n.string(lang, R.string.mlib_cloud_failed, names)

/**
 * Serveur du compte non connecté : cause précise et prochain essai (voir [com.ultratv.tv.nativeapp.data.config.MediaFailure]).
 * Cause inconnue : texte général d'avant.
 */
fun cloudMediaErrorText(lang: AppLang, reason: com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason, name: String, host: String, retryMin: Int?): String {
    val main = when (reason) {
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.OTHER_NETWORK -> I18n.string(lang, R.string.mlib_err_lan, name, host)
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.NO_ANSWER -> I18n.string(lang, R.string.mlib_err_timeout, name, host)
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.REFUSED -> I18n.string(lang, R.string.mlib_err_refused, name, host)
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.AUTH -> I18n.string(lang, R.string.mlib_err_auth, name)
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.HOST_NOT_FOUND -> I18n.string(lang, R.string.mlib_err_host, name, host)
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.NOT_FOUND -> I18n.string(lang, R.string.mlib_err_notfound, name, host)
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.TLS -> I18n.string(lang, R.string.mlib_err_tls, name, host)
        com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason.OTHER -> return I18n.string(lang, R.string.mlib_cloud_failed, name)
    }
    return if (retryMin != null && retryMin > 0) main + " " + I18n.string(lang, R.string.mlib_err_retry, retryMin) else main
}

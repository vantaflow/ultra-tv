package com.ultratv.tv.nativeapp.ui.downloads

import com.ultratv.tv.nativeapp.data.downloads.DownloadError
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.Strings
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/**
 * Libellés des téléchargements hors ligne, à part de [com.ultratv.tv.nativeapp.i18n.DesignStrings] pour ne pas se
 * marcher dessus avec les autres chantiers. Vérifiés par TranslationsCompleteTest (en / fr / es / ar).
 */
class DownloadStrings(val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)

    val navTitle get() = tx(R.string.dl_nav_title)
    val download get() = tx(R.string.dl_download)
    val downloadSeason get() = tx(R.string.dl_download_season)
    val downloaded get() = tx(R.string.dl_downloaded)
    val queued get() = tx(R.string.dl_queued)
    val paused get() = tx(R.string.dl_paused)
    val failed get() = tx(R.string.dl_failed)
    val pause get() = tx(R.string.dl_pause)
    val resume get() = tx(R.string.dl_resume)
    val retry get() = tx(R.string.dl_retry)
    val cancel get() = tx(R.string.dl_cancel)
    val delete get() = tx(R.string.dl_delete)
    val play get() = tx(R.string.dl_play)
    val sectionActive get() = tx(R.string.dl_section_active)
    val sectionDone get() = tx(R.string.dl_section_done)
    val empty get() = tx(R.string.dl_empty)
    val storageUsed get() = tx(R.string.dl_storage_used)
    fun storageFree(used: String, free: String) = tx(R.string.dl_storage_free, used, free)

    // Retours à l'utilisateur
    val queuedToast get() = tx(R.string.dl_queued_toast)
    fun seasonQueued(n: Int) = tx(R.string.dl_season_queued, n)
    val alreadyQueued get() = tx(R.string.dl_already_queued)
    val notDownloadable get() = tx(R.string.dl_not_downloadable)
    val noSpace get() = tx(R.string.dl_no_space)
    val sourceMissing get() = tx(R.string.dl_source_missing)
    val networkError get() = tx(R.string.dl_network_error)
    val httpError get() = tx(R.string.dl_http_error)
    val storageError get() = tx(R.string.dl_storage_error)
    val connectionWhilePlaying get() = tx(R.string.dl_connection_while_playing)
    val connectionBusyDownloading get() = tx(R.string.dl_connection_busy_downloading)

    // Réglages
    val wifiOnly get() = tx(R.string.dl_wifi_only)
    val wifiOnlyHint get() = tx(R.string.dl_wifi_only_hint)
    val location get() = tx(R.string.dl_location)
    val locationHint get() = tx(R.string.dl_location_hint)
    val locationExternal get() = tx(R.string.dl_location_external)
    val locationInternal get() = tx(R.string.dl_location_internal)

    // Notification
    val notifChannel get() = tx(R.string.dl_notif_channel)
    fun notifProgress(count: Int, percent: Int) = tx(R.string.dl_notif_progress, count, percent)
    val notifStarting get() = tx(R.string.dl_notif_starting)

    /** Message d'un code d'erreur enregistré (jamais le texte brut d'une exception). */
    fun error(code: String?): String = when (code) {
        DownloadError.NO_SPACE -> noSpace
        DownloadError.NOT_DOWNLOADABLE -> notDownloadable
        DownloadError.SOURCE_MISSING -> sourceMissing
        DownloadError.HTTP -> httpError
        DownloadError.STORAGE -> storageError
        else -> networkError
    }

    companion object {
        /**
         * Langue d'une table [Strings] : le rail TV ne passe que celle-ci à ses libellés (stringsFor est composable, donc
         * inutilisable ici) — reconnue à son libellé « Réglages », propre à chaque langue (vérifié par DownloadStringsTest).
         */
        fun of(s: Strings): DownloadStrings = DownloadStrings(
            when (s.navSettings) {
                "Paramètres" -> AppLang.French
                "Ajustes" -> AppLang.Spanish
                "الإعدادات" -> AppLang.Arabic
                else -> AppLang.English
            },
        )
    }
}

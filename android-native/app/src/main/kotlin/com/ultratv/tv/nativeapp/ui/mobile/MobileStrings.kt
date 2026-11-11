package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/**
 * Libellés ajoutés par l'interface tactile (téléphone / tablette), FR · EN · ES · AR.
 * Fichier dédié : les tables globales (Strings, DesignStrings) ne sont pas touchées, ce qui évite
 * tout conflit avec les autres chantiers de libellés.
 */
data class MobileStrings(
    val tabMore: String,
    val moreTitle: String,
    val navProfiles: String,
    val seeAll: String,
    val a11ySearch: String,
    val a11yProfile: String,
    val a11yBack: String,
    val a11yClose: String,
    val a11yCast: String,
    val a11ySettings: String,
    val a11yMenu: String,
    val a11yPlay: String,
    val a11yPause: String,
    val a11ySeekBack: String,
    val a11ySeekForward: String,
    val brightness: String,
    val volume: String,
    val pip: String,
    val channels: String,
    val tracks: String,
    val fromStart: String,
    val sleep: String,
    val languagesLabel: String,
    val nextChannel: String,
    val previousChannel: String,
    val aspectChanged: String,
    val tapToWatchFull: String,
    val myList: String,
    val inMyList: String,
    val episodes: String,
    val season: String,
    val moreInfo: String,
    val lessInfo: String,
    val categoriesLabel: String,
    val liveNow: String,
    val pullToRefresh: String,
    val refreshing: String,
    val clearSearch: String,
    val searchHint: String,
    val details: String,
    val noPreview: String,
    val rotateHint: String,
    val castUnavailable: String,
    val pipUnavailable: String,
    val longPressHint: String,
    val fromCloud: String,
    val openDashboard: String,
    val dashboardAddress: String,
) {
    companion object {
        private fun build(lang: AppLang): MobileStrings {
            fun tx(id: Int): String = I18n.string(lang, id)
            return MobileStrings(
            tabMore = tx(R.string.mob_tab_more),
            moreTitle = tx(R.string.mob_more_title),
            navProfiles = tx(R.string.mob_nav_profiles),
            seeAll = tx(R.string.mob_see_all),
            a11ySearch = tx(R.string.mob_a11y_search),
            a11yProfile = tx(R.string.mob_a11y_profile),
            a11yBack = tx(R.string.mob_a11y_back),
            a11yClose = tx(R.string.mob_a11y_close),
            a11yCast = tx(R.string.mob_a11y_cast),
            a11ySettings = tx(R.string.mob_a11y_settings),
            a11yMenu = tx(R.string.mob_a11y_menu),
            a11yPlay = tx(R.string.mob_a11y_play),
            a11yPause = tx(R.string.mob_a11y_pause),
            a11ySeekBack = tx(R.string.mob_a11y_seek_back),
            a11ySeekForward = tx(R.string.mob_a11y_seek_forward),
            brightness = tx(R.string.mob_brightness),
            volume = tx(R.string.mob_volume),
            pip = tx(R.string.mob_pip),
            channels = tx(R.string.mob_channels),
            tracks = tx(R.string.mob_tracks),
            fromStart = tx(R.string.mob_from_start),
            sleep = tx(R.string.mob_sleep),
            languagesLabel = tx(R.string.mob_languages_label),
            nextChannel = tx(R.string.mob_next_channel),
            previousChannel = tx(R.string.mob_previous_channel),
            aspectChanged = tx(R.string.mob_aspect_changed),
            tapToWatchFull = tx(R.string.mob_tap_to_watch_full),
            myList = tx(R.string.mob_my_list),
            inMyList = tx(R.string.mob_in_my_list),
            episodes = tx(R.string.mob_episodes),
            season = tx(R.string.mob_season),
            moreInfo = tx(R.string.mob_more_info),
            lessInfo = tx(R.string.mob_less_info),
            categoriesLabel = tx(R.string.mob_categories_label),
            liveNow = tx(R.string.mob_live_now),
            pullToRefresh = tx(R.string.mob_pull_to_refresh),
            refreshing = tx(R.string.mob_refreshing),
            clearSearch = tx(R.string.mob_clear_search),
            searchHint = tx(R.string.mob_search_hint),
            details = tx(R.string.mob_details),
            noPreview = tx(R.string.mob_no_preview),
            rotateHint = tx(R.string.mob_rotate_hint),
            castUnavailable = tx(R.string.mob_cast_unavailable),
            pipUnavailable = tx(R.string.mob_pip_unavailable),
            longPressHint = tx(R.string.mob_long_press_hint),
            fromCloud = tx(R.string.mob_from_cloud),
            openDashboard = tx(R.string.mob_open_dashboard),
            dashboardAddress = tx(R.string.mob_dashboard_address),
            )
        }

        val EN get() = forLang(AppLang.English, "en")
        val FR get() = forLang(AppLang.French, "fr")
        val ES get() = forLang(AppLang.Spanish, "es")
        val AR get() = forLang(AppLang.Arabic, "ar")


        fun forLang(lang: AppLang, systemLanguage: String): MobileStrings {
            val resolved = if (lang == AppLang.System) AppLang.entries.firstOrNull { it.code == systemLanguage } ?: AppLang.English else lang
            return I18n.cached("MobileStrings", resolved) { build(resolved) }
        }
    }
}

val LocalMobileStrings = compositionLocalOf { MobileStrings.forLang(AppLang.English, "en") }

@Composable
fun mobileStringsFor(lang: AppLang): MobileStrings {
    val sys = LocalConfiguration.current.locales.get(0)?.language ?: "en"
    return remember(lang, sys) { MobileStrings.forLang(lang, sys) }
}

/** Ouvre une adresse https dans le navigateur (jamais d'autre schéma). */
fun android.content.Context.openInBrowser(url: String): Boolean {
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return false
    return runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

package com.ultratv.tv.nativeapp.data.profile

import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.data.prefs.SidebarPosition
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs

/**
 * Préférences PAR PROFIL. Chaque champ est un SURCHARGE facultative (null = hérite de la valeur globale),
 * si bien que le profil « Principal » migré garde les réglages existants sans rien copier.
 * Les réglages techniques (lecteur, tampon, synchro, sources) ne sont volontairement PAS ici.
 */
data class ProfilePrefs(
    val theme: AppTheme? = null,
    val sidebarPosition: SidebarPosition? = null,
    val showChannelNumbers: Boolean? = null,
    val hideAdultCategories: Boolean? = null,
    val resumePlayback: Boolean? = null,
    val autoPlayNextEpisode: Boolean? = null,
    val autoPlayLastOnLaunch: Boolean? = null,
    /** Langues des contenus (CSV ; modèle seulement — le filtre est branché ailleurs). */
    val languages: String? = null,
    val includeMulti: Boolean? = null,
    val includeUnknownLang: Boolean? = null,
) {
    /** Applique les surcharges du profil sur les préférences globales. [isKids] force le contrôle parental. */
    fun applyTo(base: UserPrefs, isKids: Boolean = false): UserPrefs = base.copy(
        theme = theme ?: base.theme,
        sidebarPosition = sidebarPosition ?: base.sidebarPosition,
        showChannelNumbers = showChannelNumbers ?: base.showChannelNumbers,
        hideAdultCategories = if (isKids) true else hideAdultCategories ?: base.hideAdultCategories,
        resumePlayback = resumePlayback ?: base.resumePlayback,
        autoPlayNextEpisode = autoPlayNextEpisode ?: base.autoPlayNextEpisode,
        autoPlayLastOnLaunch = autoPlayLastOnLaunch ?: base.autoPlayLastOnLaunch,
        languages = languages ?: base.languages,
        includeMulti = includeMulti ?: base.includeMulti,
        includeUnknownLang = includeUnknownLang ?: base.includeUnknownLang,
    )

    companion object {
        const val THEME = "theme"
        const val SIDEBAR = "sidebar"
        const val CHANNEL_NUMBERS = "channel_numbers"
        const val HIDE_ADULT = "hide_adult"
        const val RESUME = "resume"
        const val AUTOPLAY_NEXT = "autoplay_next"
        const val AUTOPLAY_LAST = "autoplay_last"
        const val LANGUAGES = "languages"
        const val INCLUDE_MULTI = "include_multi"
        const val INCLUDE_UNKNOWN = "include_unknown"

        fun fromMap(m: Map<String, String>) = ProfilePrefs(
            theme = m[THEME]?.let { AppTheme.parse(it) },
            sidebarPosition = m[SIDEBAR]?.let { runCatching { SidebarPosition.valueOf(it) }.getOrNull() },
            showChannelNumbers = m[CHANNEL_NUMBERS]?.toBooleanStrictOrNull(),
            hideAdultCategories = m[HIDE_ADULT]?.toBooleanStrictOrNull(),
            resumePlayback = m[RESUME]?.toBooleanStrictOrNull(),
            autoPlayNextEpisode = m[AUTOPLAY_NEXT]?.toBooleanStrictOrNull(),
            autoPlayLastOnLaunch = m[AUTOPLAY_LAST]?.toBooleanStrictOrNull(),
            languages = m[LANGUAGES],
            includeMulti = m[INCLUDE_MULTI]?.toBooleanStrictOrNull(),
            includeUnknownLang = m[INCLUDE_UNKNOWN]?.toBooleanStrictOrNull(),
        )
    }
}

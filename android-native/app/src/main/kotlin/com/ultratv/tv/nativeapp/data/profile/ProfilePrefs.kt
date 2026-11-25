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
    val showChannelLogos: Boolean? = null,
    /** Largeur de la colonne des noms du Guide (clé de GuideNameWidth). */
    val guideNameWidth: String? = null,
    /** Numérotation des chaînes (clé de ChannelNumberMode) ; null = automatique. */
    val channelNumbering: String? = null,
    /** Tri des chaînes du Direct (clé de ChannelSort) ; null = ordre de la source. */
    val channelSort: String? = null,
    val hideAdultCategories: Boolean? = null,
    val resumePlayback: Boolean? = null,
    val autoPlayNextEpisode: Boolean? = null,
    val autoPlayLastOnLaunch: Boolean? = null,
    /** Langues des contenus (CSV ; modèle seulement — le filtre est branché ailleurs). */
    val languages: String? = null,
    val includeMulti: Boolean? = null,
    val includeUnknownLang: Boolean? = null,
    /** Mode simple (grandes tuiles à la place de l'accueil) : propre à chaque profil. */
    val simpleMode: Boolean? = null,
    /** Dernier groupe de chaînes choisi dans le Guide TV (identifiant de catégorie du Direct, « __all__ », « __fav__ »). Pas une préférence globale : jamais appliqué à [UserPrefs]. */
    val guideGroup: String? = null,
    /** Langue de l'interface du profil (code AppLang) ; null = langue de l'appareil. Partagée entre appareils. */
    val uiLanguage: String? = null,
    /** Horaires de visionnage et temps d'écran du profil (codage de [com.ultratv.tv.nativeapp.data.screentime.ScreenTime]) ; null = aucune règle. Partagé entre appareils, le décompte reste local. */
    val screenTime: String? = null,
    /** Noms des sources visibles du profil (null = toutes). Partagé entre appareils ; sans effet sur [applyTo]. */
    val sources: List<String>? = null,
    /** Recherche dans toutes les sources visibles du profil (et non la seule source active) ; local à l'appareil. */
    val searchAllSources: Boolean? = null,
    /** Écran Favoris : favoris de toutes les sources visibles du profil réunis (et non ceux de la seule source active) ; local à l'appareil. */
    val favAllSources: Boolean? = null,
    /** Espace Sport : ligues dans l'ordre choisi, les masquées préfixées de « - » (codage de LeaguePrefs) ; null = ordre par défaut. */
    val sportLeagues: String? = null,
    /** Espace Sport : mode sans spoiler (scores masqués jusqu'à ce qu'on les affiche) ; null = désactivé. */
    val sportNoSpoiler: Boolean? = null,
) {
    /** Applique les surcharges du profil sur les préférences globales. [isKids] force le contrôle parental. */
    fun applyTo(base: UserPrefs, isKids: Boolean = false): UserPrefs = base.copy(
        theme = theme ?: base.theme,
        sidebarPosition = sidebarPosition ?: base.sidebarPosition,
        showChannelNumbers = showChannelNumbers ?: base.showChannelNumbers,
        showChannelLogos = showChannelLogos ?: base.showChannelLogos,
        guideNameWidth = guideNameWidth ?: base.guideNameWidth,
        channelNumbering = channelNumbering ?: base.channelNumbering,
        channelSort = channelSort ?: base.channelSort,
        hideAdultCategories = if (isKids) true else hideAdultCategories ?: base.hideAdultCategories,
        resumePlayback = resumePlayback ?: base.resumePlayback,
        autoPlayNextEpisode = autoPlayNextEpisode ?: base.autoPlayNextEpisode,
        autoPlayLastOnLaunch = autoPlayLastOnLaunch ?: base.autoPlayLastOnLaunch,
        languages = languages ?: base.languages,
        includeMulti = includeMulti ?: base.includeMulti,
        includeUnknownLang = includeUnknownLang ?: base.includeUnknownLang,
        simpleMode = simpleMode ?: base.simpleMode,
        language = uiLanguage ?: base.language,
    )

    companion object {
        const val THEME = "theme"
        const val SIDEBAR = "sidebar"
        const val CHANNEL_NUMBERS = "channel_numbers"
        const val CHANNEL_LOGOS = "channel_logos"
        const val GUIDE_NAME_WIDTH = "guide_name_width"
        const val CHANNEL_NUMBERING = "channel_numbering"
        const val CHANNEL_SORT = "channel_sort"
        const val HIDE_ADULT = "hide_adult"
        const val RESUME = "resume"
        const val AUTOPLAY_NEXT = "autoplay_next"
        const val AUTOPLAY_LAST = "autoplay_last"
        const val LANGUAGES = "languages"
        const val INCLUDE_MULTI = "include_multi"
        const val INCLUDE_UNKNOWN = "include_unknown"
        const val SIMPLE_MODE = "simple_mode"
        const val GUIDE_GROUP = "guide_group"
        const val UI_LANGUAGE = "ui_language"
        const val SCREEN_TIME = "screen_time"
        const val SOURCES = "sources"
        const val SEARCH_ALL = "search_all"
        const val FAV_ALL = "fav_all"
        const val SPORT_LEAGUES = "sport_leagues"
        const val SPORT_NO_SPOILER = "sport_no_spoiler"

        fun fromMap(m: Map<String, String>) = ProfilePrefs(
            theme = m[THEME]?.let { AppTheme.parse(it) },
            sidebarPosition = m[SIDEBAR]?.let { runCatching { SidebarPosition.valueOf(it) }.getOrNull() },
            showChannelNumbers = m[CHANNEL_NUMBERS]?.toBooleanStrictOrNull(),
            showChannelLogos = m[CHANNEL_LOGOS]?.toBooleanStrictOrNull(),
            guideNameWidth = m[GUIDE_NAME_WIDTH]?.takeIf { it.isNotEmpty() },
            channelNumbering = m[CHANNEL_NUMBERING]?.takeIf { it.isNotEmpty() },
            channelSort = m[CHANNEL_SORT]?.takeIf { it.isNotEmpty() },
            hideAdultCategories = m[HIDE_ADULT]?.toBooleanStrictOrNull(),
            resumePlayback = m[RESUME]?.toBooleanStrictOrNull(),
            autoPlayNextEpisode = m[AUTOPLAY_NEXT]?.toBooleanStrictOrNull(),
            autoPlayLastOnLaunch = m[AUTOPLAY_LAST]?.toBooleanStrictOrNull(),
            languages = m[LANGUAGES],
            includeMulti = m[INCLUDE_MULTI]?.toBooleanStrictOrNull(),
            includeUnknownLang = m[INCLUDE_UNKNOWN]?.toBooleanStrictOrNull(),
            simpleMode = m[SIMPLE_MODE]?.toBooleanStrictOrNull(),
            guideGroup = m[GUIDE_GROUP]?.takeIf { it.isNotEmpty() },
            uiLanguage = m[UI_LANGUAGE]?.takeIf { it.isNotEmpty() },
            screenTime = m[SCREEN_TIME]?.takeIf { it.isNotEmpty() },
            sources = ProfileSourcesRules.decode(m[SOURCES]),
            searchAllSources = m[SEARCH_ALL]?.toBooleanStrictOrNull(),
            favAllSources = m[FAV_ALL]?.toBooleanStrictOrNull(),
            sportLeagues = m[SPORT_LEAGUES]?.takeIf { it.isNotEmpty() },
            sportNoSpoiler = m[SPORT_NO_SPOILER]?.toBooleanStrictOrNull(),
        )
    }
}

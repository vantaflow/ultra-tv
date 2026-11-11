package com.ultratv.tv.nativeapp.i18n

/**
 * Libellés du nouvel accueil (« À la une » pleine largeur, raccourcis, rangée Direct, Top 10).
 * Fichier à part (extensions de [DesignStrings]) : ni le constructeur de [Strings] ni [DesignStrings] ne grossissent.
 */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

val DesignStrings.homeMoreInfo get() = t("More info", "Plus d'infos", "Más información", "مزيد من المعلومات")
val DesignStrings.homeLiveNow get() = t("Live", "En direct", "En directo", "مباشر")
val DesignStrings.homeTop10 get() = t("Top 10 today", "Top 10 aujourd'hui", "Top 10 de hoy", "الأفضل 10 اليوم")
val DesignStrings.homeShortcutsRow get() = t("Shortcuts", "Raccourcis", "Accesos directos", "اختصارات")
val DesignStrings.homeGuideSubtitle get() = t("Today's programmes", "Programmes du jour", "Programas de hoy", "برامج اليوم")

fun DesignStrings.homeChannelsCount(n: Int, count: String) = when (lang) {
    AppLang.French -> if (n <= 1) "$count chaîne" else "$count chaînes"
    AppLang.Spanish -> if (n == 1) "$count canal" else "$count canales"
    AppLang.Arabic -> "$count قناة"
    else -> if (n == 1) "$count channel" else "$count channels"
}

fun DesignStrings.homeMoviesCount(n: Int, count: String) = when (lang) {
    AppLang.French -> if (n <= 1) "$count film" else "$count films"
    AppLang.Spanish -> if (n == 1) "$count película" else "$count películas"
    AppLang.Arabic -> "$count فيلم"
    else -> if (n == 1) "$count movie" else "$count movies"
}

fun DesignStrings.homeSeriesCount(n: Int, count: String) = when (lang) {
    AppLang.French -> if (n <= 1) "$count série" else "$count séries"
    AppLang.Spanish -> if (n == 1) "$count serie" else "$count series"
    AppLang.Arabic -> "$count مسلسل"
    else -> if (n == 1) "$count series" else "$count series"
}

fun DesignStrings.homeFavoritesCount(n: Int, count: String) = when (lang) {
    AppLang.French -> if (n <= 1) "$count favori" else "$count favoris"
    AppLang.Spanish -> if (n == 1) "$count favorito" else "$count favoritos"
    AppLang.Arabic -> "$count مفضلة"
    else -> if (n == 1) "$count favorite" else "$count favorites"
}

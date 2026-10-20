package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.nav.StartScreen
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.TextSize
import com.ultratv.tv.nativeapp.ui.home.HomeBlock

/**
 * Libellés de la personnalisation (accent, taille du texte, rangées de l'accueil, écran d'ouverture) et de l'en-tête
 * de la liste Direct. Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

private fun count(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)

// ── En-tête de la liste Direct ──

/** « 40 chaînes » avec le bon pluriel (arabe : singulier, duel, pluriel 3–10, singulier accusatif au-delà). */
fun DesignStrings.channelCount(n: Int): String {
    val f = count(n)
    return when (lang) {
        AppLang.French -> if (n <= 1) "$f chaîne" else "$f chaînes"
        AppLang.Spanish -> if (n == 1) "$f canal" else "$f canales"
        AppLang.Arabic -> when { n == 1 -> "قناة واحدة"; n == 2 -> "قناتان"; n in 3..10 -> "$f قنوات"; else -> "$f قناة" }
        else -> if (n == 1) "$f channel" else "$f channels"
    }
}

fun DesignStrings.sectionCount(n: Int): String {
    val f = count(n)
    return when (lang) {
        AppLang.French -> if (n <= 1) "$f section" else "$f sections"
        AppLang.Spanish -> if (n == 1) "$f sección" else "$f secciones"
        AppLang.Arabic -> when { n == 1 -> "قسم واحد"; n == 2 -> "قسمان"; n in 3..10 -> "$f أقسام"; else -> "$f قسمًا" }
        else -> if (n == 1) "$f section" else "$f sections"
    }
}

/**
 * Partie « compteur » de l'en-tête Direct, toujours dans la langue de l'interface : « 40 chaînes », suivie du nombre
 * de sections seulement s'il y en a plusieurs. Le nom de la catégorie est affiché à part, tel quel (jamais mêlé à ce
 * texte dans une même chaîne : un nom arabe dans une phrase française déplaçait les nombres à l'affichage).
 */
fun DesignStrings.liveHeaderCount(channels: Int, sections: Int): String =
    if (sections > 1) "${channelCount(channels)} · ${sectionCount(sections)}" else channelCount(channels)

// ── Réglages › Affichage ──

val DesignStrings.personalization get() = t("Personalization", "Personnalisation", "Personalización", "التخصيص")
val DesignStrings.accentColor get() = t("Accent colour", "Couleur d'accent", "Color de acento", "لون التمييز")
val DesignStrings.textSizeLabel get() = t("Text size", "Taille du texte", "Tamaño del texto", "حجم النص")
val DesignStrings.homeRows get() = t("Home rows", "Rangées de l'accueil", "Filas de inicio", "صفوف الصفحة الرئيسية")
fun DesignStrings.homeRowsSummary(shown: Int, total: Int) = t("$shown of $total shown", "$shown sur $total affichées", "$shown de $total visibles", "$shown من $total معروضة")
val DesignStrings.homeRowsHint get() = t(
    "OK shows or hides a row; the arrows move it.",
    "OK affiche ou masque une rangée ; les flèches la déplacent.",
    "OK muestra u oculta una fila; las flechas la mueven.",
    "زر OK يُظهر الصف أو يخفيه، والأسهم تنقله.",
)
val DesignStrings.rowShown get() = t("Shown", "Affichée", "Visible", "معروض")
val DesignStrings.rowHidden get() = t("Hidden", "Masquée", "Oculta", "مخفي")
val DesignStrings.moveUp get() = t("Move up", "Monter", "Subir", "تحريك لأعلى")
val DesignStrings.moveDown get() = t("Move down", "Descendre", "Bajar", "تحريك لأسفل")
val DesignStrings.resetDefault get() = t("Reset", "Réinitialiser", "Restablecer", "إعادة تعيين")
val DesignStrings.doneLabel get() = t("Done", "Terminé", "Listo", "تم")
val DesignStrings.traktListsRow get() = t("My Trakt lists", "Mes listes Trakt", "Mis listas de Trakt", "قوائمي على Trakt")

fun DesignStrings.accentName(a: AccentChoice): String = when (a) {
    AccentChoice.RED -> t("Red (default)", "Rouge (par défaut)", "Rojo (predeterminado)", "أحمر (افتراضي)")
    AccentChoice.BLUE -> t("Blue", "Bleu", "Azul", "أزرق")
    AccentChoice.VIOLET -> t("Purple", "Violet", "Violeta", "بنفسجي")
    AccentChoice.GREEN -> t("Green", "Vert", "Verde", "أخضر")
    AccentChoice.ORANGE -> t("Orange", "Orange", "Naranja", "برتقالي")
    AccentChoice.PINK -> t("Pink", "Rose", "Rosa", "وردي")
}

fun DesignStrings.textSizeName(s: TextSize): String = when (s) {
    TextSize.NORMAL -> t("Normal", "Normal", "Normal", "عادي")
    TextSize.LARGE -> t("Large", "Grand", "Grande", "كبير")
    TextSize.XLARGE -> t("Extra large", "Très grand", "Muy grande", "كبير جدًا")
}

fun DesignStrings.startScreenName(s: StartScreen): String = when (s) {
    StartScreen.HOME -> t("Home (default)", "Accueil (par défaut)", "Inicio (predeterminado)", "الرئيسية (افتراضي)")
    StartScreen.LIVE -> t("Live TV", "Direct", "En directo", "البث المباشر")
    StartScreen.MOVIES -> t("Movies", "Films", "Películas", "الأفلام")
    StartScreen.SERIES -> t("TV shows", "Séries", "Series", "المسلسلات")
    StartScreen.GUIDE -> t("TV guide", "Guide TV", "Guía de TV", "دليل البرامج")
    StartScreen.LAST_CHANNEL -> t("Last channel watched", "Dernière chaîne regardée", "Último canal visto", "آخر قناة تمت مشاهدتها")
}

fun DesignStrings.homeBlockName(b: HomeBlock): String = when (b) {
    HomeBlock.RESUME -> continueWatching
    HomeBlock.TRAKT_UP_NEXT -> traktUpNext
    HomeBlock.TRAKT_WATCHLIST -> traktWatchlist
    HomeBlock.TRAKT_RECOMMENDED -> traktRecommended
    HomeBlock.TRAKT_TRENDING -> traktTrending
    HomeBlock.TRAKT_POPULAR -> traktPopular
    HomeBlock.TRAKT_LISTS -> traktListsRow
    HomeBlock.RECENT_CHANNELS -> recentlyWatched
    HomeBlock.LATEST_MOVIES -> latestMovies
    HomeBlock.LATEST_SERIES -> latestSeries
    HomeBlock.CHANNELS -> favoriteChannels
}

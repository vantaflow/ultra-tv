package com.ultratv.tv.nativeapp.i18n

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatages de dates partagés par les écrans, tous sur la locale d'affichage ([DisplayLocale] : chiffres latins). */
object DisplayFormats {
    /** « Mardi 17 novembre » : date du haut de l'accueil du mode simple. */
    fun simpleHomeDate(lang: AppLang, nowMs: Long): String {
        val loc = DisplayLocale.of(lang)
        return SimpleDateFormat("EEEE d MMMM", loc).format(Date(nowMs)).replaceFirstChar { it.titlecase(loc) }
    }

    /** Locale des dates de l'écran Sport (langue de l'appli, chiffres latins). */
    fun sportLocale(lang: AppLang): Locale = DisplayLocale.of(lang)

    /** « sam. 14 nov. 20:30 » : début d'un match (feuille de match de Sport). */
    fun sportStart(locale: Locale, ms: Long): String {
        val loc = DisplayLocale.latin(locale)
        return SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "EEEdMMMHHmm"), loc).format(Date(ms))
    }
}

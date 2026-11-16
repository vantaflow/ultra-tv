package com.ultratv.tv.nativeapp.ui.screentime

import com.ultratv.tv.nativeapp.data.screentime.ScreenTime
import com.ultratv.tv.nativeapp.data.screentime.ScreenTimeRules
import com.ultratv.tv.nativeapp.data.screentime.TimeWindow
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Mise en forme (heures, jours, résumés) des règles de temps d'écran ; fonctions pures, testées à part. */
object ScreenTimeText {
    /** « 08:00 » ; 1440 = « 24:00 » (fin de journée). */
    fun hhmm(min: Int): String = String.format(Locale.ROOT, "%02d:%02d", min / 60, min % 60)

    fun windows(ws: List<TimeWindow>): String = ws.joinToString(", ") { "${hhmm(it.startMin)}–${hhmm(it.endMin)}" }

    /** Nom du jour dans la langue de l'appli (1 = lundi … 7 = dimanche). */
    fun dayName(dow: Int, lang: AppLang): String =
        DayOfWeek.of(dow).getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag(I18n.resolve(lang).code))
            .replaceFirstChar { it.titlecase(Locale.forLanguageTag(I18n.resolve(lang).code)) }

    /** « Mardi 08:00 » : prochain instant autorisé. */
    fun next(ms: Long, zone: ZoneId, lang: AppLang): String =
        "${dayName(ScreenTime.dayOfWeek(ms, zone), lang)} ${hhmm(ScreenTime.minuteOfDay(ms, zone))}"

    /** Valeur affichée pour un jour dans l'éditeur. */
    fun dayValue(r: ScreenTimeRules, dow: Int, S: ScreenTimeStrings): String {
        val w = r.days[dow] ?: return S.free
        return if (w.isEmpty()) S.blockedDay else windows(w)
    }

    /** Résumé d'un profil dans la liste (null = aucune règle). */
    fun summary(r: ScreenTimeRules, S: ScreenTimeStrings): String? {
        if (r.isEmpty) return null
        return listOfNotNull(r.dailyLimitMin?.let { S.summaryLimit(it) }, if (r.days.isNotEmpty()) S.summaryHours else null).joinToString(" · ")
    }
}

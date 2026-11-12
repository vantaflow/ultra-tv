package com.ultratv.tv.nativeapp.ui.common

/** Réglage « Format de l'heure » (Réglages › Affichage). Clés communes avec le bureau : "auto" | "12" | "24". */
enum class TimeFormatPref(val key: String) {
    AUTO("auto"), H12("12"), H24("24");

    /** Vrai si l'heure doit s'afficher sur 24 h ; [system24] = réglage du système / de la langue (utilisé par Automatique). */
    fun use24(system24: Boolean): Boolean = when (this) { AUTO -> system24; H12 -> false; H24 -> true }

    companion object {
        /** Tolérant : valeur absente ou inconnue (autre appareil, version future) = Automatique. */
        fun parse(raw: String?): TimeFormatPref = entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: AUTO
    }
}

/**
 * Mise en forme PURE d'une heure d'horloge (aucune dépendance Android). Les chiffres restent latins dans toutes les
 * langues ; seules les mentions matin / après-midi suivent la langue ([am], [pm]).
 *  - 24 h : « 16:30 », « 00:05 » ;  12 h : « 4:30 PM », minuit « 12:00 AM », midi « 12:00 PM ».
 */
object ClockFormat {
    private fun two(n: Int) = if (n < 10) "0$n" else "$n"

    fun format(hour: Int, minute: Int, use24: Boolean, am: String = "AM", pm: String = "PM"): String {
        val h = ((hour % 24) + 24) % 24
        if (use24) return "${two(h)}:${two(minute)}"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${two(minute)} ${if (h < 12) am else pm}"
    }

    /** Heure pleine seule (heure de synchro planifiée) : « 03:00 » ou « 3:00 AM ». */
    fun formatHour(hour: Int, use24: Boolean, am: String = "AM", pm: String = "PM"): String = format(hour, 0, use24, am, pm)

    /** « 16:00 – 17:30 » (les bornes ne s'inversent pas en RTL : isolé en LTR). */
    fun range(start: String, end: String): String = "⁦$start – $end⁩"
}

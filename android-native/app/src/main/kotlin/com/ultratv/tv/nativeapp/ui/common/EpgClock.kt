package com.ultratv.tv.nativeapp.ui.common

import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * Shared "what should the clock say for this EPG timestamp" helper.
 *
 * Stores the user's `epgTimeOffsetMin` preference at the process level so any
 * Compose layer can format programme start/end times without having to thread
 * the offset through every view-model. The pref is mirrored from DataStore by
 * [UltraTvApp] on every change.
 */
object EpgClock {
    @Volatile var offsetMinutes: Int = 0

    private val cal = ThreadLocal.withInitial { Calendar.getInstance() }

    /** Réglage « Format de l'heure » ("auto" | "12" | "24"), miroir du DataStore. */
    @Volatile var timeFormat: String = TimeFormatPref.AUTO.key
    /** Format du système / de la langue, utilisé quand le réglage est Automatique (posé par UltraTvApp, rafraîchi à chaque changement de réglage). */
    @Volatile var system24: Boolean = true
    /** Langue de l'interface ("system" = celle de l'appareil) : mentions AM / PM. */
    @Volatile var langCode: String = "system"
    @Volatile private var ampmCache: Pair<String, Pair<String, String>>? = null
    private fun ampm(): Pair<String, String> {
        val code = langCode
        ampmCache?.let { (k, v) -> if (k == code) return v }
        val loc = if (code == "system" || code.isBlank()) Locale.getDefault() else Locale.forLanguageTag(code)
        val a = DateFormatSymbols.getInstance(loc).amPmStrings
        val v = (a.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "AM") to (a.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "PM")
        ampmCache = code to v
        return v
    }
    fun use24(): Boolean = TimeFormatPref.parse(timeFormat).use24(system24)

    /** Fuseau choisi dans les réglages ("" = système). Lu à chaque formatage : un changement s'applique tout de suite. */
    @Volatile var zoneId: String = ""
    /** Fuseau mis en cache tant que [zoneId] ne change pas (getTimeZone recrée l'objet ; appelé des centaines de fois par écran). */
    @Volatile private var zoneCache: Pair<String, java.util.TimeZone>? = null
    private fun zone(): java.util.TimeZone {
        val id = zoneId
        zoneCache?.let { (k, z) -> if (k == id) return z }
        val z = id.takeIf { it.isNotBlank() }?.let { java.util.TimeZone.getTimeZone(it) } ?: java.util.TimeZone.getDefault()
        zoneCache = id to z
        return z
    }

    fun apply(ms: Long): Long = ms + offsetMinutes * 60_000L

    /** « 07:30 – 08:30 » isolé en LTR : en RTL, les bornes ne doivent pas s'inverser. */
    fun range(startMs: Long, endMs: Long): String = ClockFormat.range(hm(startMs), hm(endMs))

    /** Heure d'un PROGRAMME : décalage EPG appliqué (corrige un guide mal horodaté par le fournisseur). */
    fun hm(ms: Long): String = fmt(apply(ms))

    /** Heure MURALE (horloge, bornes du direct différé) : jamais décalée par le réglage du guide. */
    fun wall(ms: Long): String = fmt(ms)

    /** Heure pleine « 03:00 » / « 3:00 AM » (heure de synchronisation planifiée), sans fuseau : c'est une heure locale. */
    fun hourLabel(hour: Int): String { val (am, pm) = ampm(); return ClockFormat.formatHour(hour, use24(), am, pm) }

    /** Fuseau effectif (réglage ou système), pour les libellés « jour » affichés à côté d'une heure. */
    fun timeZone(): java.util.TimeZone = zone()

    private fun fmt(ms: Long): String {
        val c = cal.get()!!
        c.timeZone = zone()
        c.timeInMillis = ms
        val (am, pm) = ampm()
        return ClockFormat.format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), use24(), am, pm)
    }
}

package com.ultratv.tv.nativeapp.ui.common

import java.text.SimpleDateFormat
import java.util.Date
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

    private val fmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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
    fun range(startMs: Long, endMs: Long): String = "\u2066${hm(startMs)} – ${hm(endMs)}\u2069"

    /** Heure d'un PROGRAMME : décalage EPG appliqué (corrige un guide mal horodaté par le fournisseur). */
    fun hm(ms: Long): String = fmtIn().format(Date(apply(ms)))

    /** Heure MURALE (horloge, bornes du direct différé) : jamais décalée par le réglage du guide. */
    fun wall(ms: Long): String = fmtIn().format(Date(ms))

    private fun fmtIn(): SimpleDateFormat = fmt.get()!!.also { it.timeZone = zone() }
}

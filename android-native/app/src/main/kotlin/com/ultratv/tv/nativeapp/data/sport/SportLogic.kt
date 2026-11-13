package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import java.util.Calendar
import java.util.TimeZone

/**
 * Un programme de sport candidat sur une chaîne. [sportChannel] : la chaîne est une chaîne de sport ; [strict] : le
 * programme est reconnu comme sport par son seul titre (les chaînes de sport diffusent aussi des plateaux et des rediffusions :
 * la liste « À venir » ne garde que les rencontres et événements reconnus).
 */
data class SportEntry(val channel: ChannelEntity, val prog: EpgEntity, val sportChannel: Boolean, val strict: Boolean)

/** Les programmes d'une journée locale ([dayStartMs] = minuit local). */
data class SportDay(val dayStartMs: Long, val entries: List<SportEntry>)

/** Tri, regroupement et progression de l'écran Sport : fonctions pures, testées sur la JVM. */
object SportLogic {
    const val UPCOMING_MS = 48L * 3_600_000L
    const val MAX_LIVE = 60
    const val MAX_UPCOMING = 120

    /**
     * « En direct maintenant » : programmes en cours, UN par chaîne (le plus récemment commencé), chaînes de sport d'abord,
     * puis début le plus ancien, puis titre. Au plus [limit].
     */
    fun liveNow(entries: List<SportEntry>, nowMs: Long, limit: Int = MAX_LIVE): List<SportEntry> =
        entries.asSequence()
            .filter { it.prog.startMs <= nowMs && it.prog.endMs > nowMs }
            .groupBy { it.channel.id }
            .values
            .map { g -> g.maxBy { it.prog.startMs } }
            .sortedWith(compareByDescending<SportEntry> { it.sportChannel }.thenBy { it.prog.startMs }.thenBy { it.channel.title }.thenBy { it.channel.id })
            // Même programme diffusé sur plusieurs chaînes (versions HD / FHD…) : une seule carte, la première chaîne.
            .distinctBy { SportDetector.fold(it.prog.title) to it.prog.startMs }
            .take(limit)

    /**
     * « À venir (48 h) » : programmes reconnus ([SportEntry.strict]) qui commencent après [nowMs] et au plus tard dans 48 h,
     * dédoublonnés (même chaîne, même début), regroupés par jour local, triés par heure. Au plus [limit] au total.
     */
    fun upcoming(entries: List<SportEntry>, nowMs: Long, zone: TimeZone = TimeZone.getDefault(), limit: Int = MAX_UPCOMING): List<SportDay> {
        val to = nowMs + UPCOMING_MS
        val picked = entries.asSequence()
            .filter { it.strict && it.prog.startMs > nowMs && it.prog.startMs <= to }
            .distinctBy { it.channel.id to it.prog.startMs }
            .sortedWith(compareBy<SportEntry> { it.prog.startMs }.thenByDescending { it.sportChannel }.thenBy { it.channel.title }.thenBy { it.channel.id })
            .distinctBy { SportDetector.fold(it.prog.title) to it.prog.startMs }
            .take(limit)
            .toList()
        return picked.groupBy { dayStart(it.prog.startMs, zone) }
            .map { (d, list) -> SportDay(d, list) }
            .sortedBy { it.dayStartMs }
    }

    /** Minuit local du jour de [ms]. */
    fun dayStart(ms: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Écart en jours calendaires entre [dayStartMs] et le jour de [nowMs] : 0 aujourd'hui, 1 demain. */
    fun dayOffset(dayStartMs: Long, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Int =
        Math.round((dayStartMs - dayStart(nowMs, zone)) / 86_400_000.0).toInt()

    /** Part écoulée d'un programme en cours, 0..1. */
    fun progress(startMs: Long, endMs: Long, nowMs: Long): Float =
        ((nowMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Chaînes de sport pour la rangée « Chaînes sport » : sans doublon, triées par numéro (ceux sans numéro en dernier) puis titre, au plus [limit]. */
    /**
     * Chaîne « réservée » sans évènement (« NO EVENT STREAMING NOW », « No events », « Event offline »…) : les fournisseurs
     * en publient des centaines pour leurs évènements à la demande ; vides, elles noyaient la liste.
     */
    private val placeholder = Regex("""\bno\s+(live\s+)?events?\b|\bevents?\s+(offline|not\s+(live|started))\b|\bno\s+stream(ing)?\b""", RegexOption.IGNORE_CASE)
    fun isPlaceholderChannel(name: String): Boolean = placeholder.containsMatchIn(name)

    fun sportChannels(channels: List<ChannelEntity>, limit: Int = 200): List<ChannelEntity> =
        channels.distinctBy { it.id }.filterNot { isPlaceholderChannel(it.name) }.sortedWith(compareBy<ChannelEntity> { it.num == 0 }.thenBy { it.num }.thenBy { it.title }.thenBy { it.id }).take(limit)
}

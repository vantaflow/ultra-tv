package com.ultratv.tv.nativeapp.data.screentime

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Horaires de visionnage et temps d'écran quotidien d'un profil (surtout Enfants). Toute la logique est PURE
 * (aucun Android, l'heure et le fuseau sont des paramètres) pour être testée à fond : plages, minuit,
 * changement d'heure, cumul par jour local.
 *
 * Modèle : une plage = [début, fin) en minutes depuis minuit (heure locale du mur) ; un jour de la semaine
 * (1 = lundi … 7 = dimanche) ABSENT n'a aucune restriction horaire, un jour présent avec une liste VIDE est interdit
 * toute la journée. La limite quotidienne compte la lecture effective, par jour local.
 */
data class TimeWindow(val startMin: Int, val endMin: Int)

data class ScreenTimeRules(
    /** Minutes de lecture par jour ; null = pas de limite. */
    val dailyLimitMin: Int? = null,
    val days: Map<Int, List<TimeWindow>> = emptyMap(),
) {
    val isEmpty: Boolean get() = dailyLimitMin == null && days.isEmpty()
}

/** Pourquoi la lecture est bloquée. */
enum class BlockReason { OUTSIDE_WINDOW, LIMIT_REACHED }

sealed interface Verdict {
    /** Pas de règle (ou aucune ne s'applique) : jamais d'avertissement. */
    data object Unrestricted : Verdict
    /** Lecture permise ; [secondsLeft] avant la prochaine contrainte, [warn] dans les 5 dernières minutes. */
    data class Allowed(val secondsLeft: Long, val warn: Boolean) : Verdict
    /** Bloqué ; [nextAllowedMs] = prochain instant autorisé (null si aucun dans la semaine à venir). */
    data class Blocked(val reason: BlockReason, val nextAllowedMs: Long?) : Verdict
}

/**
 * Déverrouillage exceptionnel par le code parental : [untilMs] = fin de la dérogation (ignore plages ET limite),
 * [wholeDay] = « pour aujourd'hui » (jusqu'à minuit : pas d'avertissement de fin).
 */
data class Grant(val untilMs: Long, val wholeDay: Boolean = false)

object ScreenTime {
    const val WARN_SECONDS = 300L
    const val EXTRA_MINUTES = 30
    const val MAX_WINDOWS_PER_DAY = 4
    const val MAX_ENCODED = 400
    const val KEEP_DAYS = 14
    /** Choix proposés pour la limite quotidienne (minutes). */
    val LIMIT_CHOICES = listOf(30, 60, 90, 120, 180, 240, 300, 360)

    // ── Codage compact (profil + synchro) : « L90;1=480-1200,1260-1380;2=;… » ──

    fun encode(r: ScreenTimeRules): String {
        val parts = mutableListOf<String>()
        r.dailyLimitMin?.takeIf { it in 1..1440 }?.let { parts += "L$it" }
        for (d in 1..7) {
            val w = r.days[d] ?: continue
            parts += "$d=" + normalize(w).joinToString(",") { "${it.startMin}-${it.endMin}" }
        }
        return parts.joinToString(";")
    }

    /** Tolérant : segment illisible ignoré, plages bornées, triées et fusionnées. Texte vide / null = aucune règle. */
    fun decode(raw: String?): ScreenTimeRules {
        if (raw.isNullOrBlank() || raw.length > MAX_ENCODED) return ScreenTimeRules()
        var limit: Int? = null
        val days = mutableMapOf<Int, List<TimeWindow>>()
        for (seg in raw.split(';')) {
            val s = seg.trim()
            if (s.startsWith("L")) { s.drop(1).toIntOrNull()?.takeIf { it in 1..1440 }?.let { limit = it }; continue }
            val eq = s.indexOf('=')
            val d = if (eq > 0) s.substring(0, eq).toIntOrNull() else null
            if (d == null || d !in 1..7) continue
            val wins = s.substring(eq + 1).split(',').mapNotNull { w ->
                val p = w.split('-')
                val a = p.getOrNull(0)?.trim()?.toIntOrNull()
                val b = p.getOrNull(1)?.trim()?.toIntOrNull()
                if (p.size == 2 && a != null && b != null) TimeWindow(a, b) else null
            }
            days[d] = normalize(wins)
        }
        return ScreenTimeRules(limit, days)
    }

    /** Plages valides (0 ≤ début < fin ≤ 1440), triées, fusionnées si elles se touchent, 4 au plus. */
    fun normalize(w: List<TimeWindow>): List<TimeWindow> {
        val ok = w.filter { it.startMin in 0..1439 && it.endMin in 1..1440 && it.endMin > it.startMin }.sortedBy { it.startMin }
        val out = mutableListOf<TimeWindow>()
        for (x in ok) {
            val last = out.lastOrNull()
            if (last != null && x.startMin <= last.endMin) out[out.lastIndex] = TimeWindow(last.startMin, maxOf(last.endMin, x.endMin)) else out += x
        }
        return out.take(MAX_WINDOWS_PER_DAY)
    }

    // ── Heure locale ──

    private fun zdt(ms: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    /** Clé du jour LOCAL « aaaa-mm-jj » (le cumul se fait par jour local, pas par fenêtre de 24 h). */
    fun dayKey(ms: Long, zone: ZoneId): String = zdt(ms, zone).toLocalDate().toString()

    /** Jour de la semaine local (1 = lundi … 7 = dimanche) et minute du jour (heure du mur). */
    fun dayOfWeek(ms: Long, zone: ZoneId): Int = zdt(ms, zone).dayOfWeek.value
    fun minuteOfDay(ms: Long, zone: ZoneId): Int = zdt(ms, zone).let { it.hour * 60 + it.minute }

    /** Instant de l'heure du mur [minute] du jour [date] (1440 = minuit suivant). Dans un trou d'heure d'été, décalé vers l'avant. */
    private fun epochOf(date: LocalDate, minute: Int, zone: ZoneId): Long =
        (if (minute >= 1440) date.plusDays(1).atStartOfDay(zone) else ZonedDateTime.of(date, LocalTime.of(minute / 60, minute % 60), zone))
            .toInstant().toEpochMilli()

    /** Plages du jour [date] ; null = jour sans restriction. */
    private fun windowsOf(r: ScreenTimeRules, date: LocalDate): List<TimeWindow>? = r.days[date.dayOfWeek.value]

    /**
     * Fin de l'autorisation horaire en cours (instant), ou null si aucune contrainte horaire dans les 8 jours à venir.
     * Renvoie [nowMs] si [nowMs] est HORS plage. Les plages qui se touchent à minuit (24:00 / 00:00) sont enchaînées,
     * un jour sans restriction laisse passer la journée entière.
     */
    fun allowedUntil(r: ScreenTimeRules, nowMs: Long, zone: ZoneId): Long? {
        val now = zdt(nowMs, zone)
        var date = now.toLocalDate()
        var minute = now.hour * 60 + now.minute
        for (i in 0..8) {
            val w = windowsOf(r, date)
            if (w != null) {
                val cur = w.firstOrNull { minute >= it.startMin && minute < it.endMin }
                    ?: return if (i == 0) nowMs else epochOf(date, 0, zone)
                if (cur.endMin < 1440) return epochOf(date, cur.endMin, zone)
            }
            date = date.plusDays(1); minute = 0
        }
        return null
    }

    /** Premier instant autorisé à partir de [fromMs] (lui-même s'il l'est déjà) dans les 8 jours ; null sinon. */
    fun nextAllowed(r: ScreenTimeRules, fromMs: Long, zone: ZoneId): Long? {
        val from = zdt(fromMs, zone)
        val minute = from.hour * 60 + from.minute
        for (i in 0..8) {
            val date = from.toLocalDate().plusDays(i.toLong())
            val w = windowsOf(r, date) ?: return if (i == 0) fromMs else epochOf(date, 0, zone)
            val m = if (i == 0) minute else -1
            val hit = w.firstOrNull { it.endMin > m } ?: continue
            return if (m >= hit.startMin) fromMs else epochOf(date, hit.startMin, zone)
        }
        return null
    }

    /** Début du jour local suivant (minuit ; 23 h ou 25 h plus tard les jours de changement d'heure). */
    fun nextMidnight(ms: Long, zone: ZoneId): Long = zdt(ms, zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    // ── Décision ──

    /**
     * @param usedTodaySec lecture effective déjà cumulée aujourd'hui (jour local de [nowMs]).
     * @param grant dérogation parentale en cours, s'il y en a une.
     */
    fun evaluate(r: ScreenTimeRules, usedTodaySec: Long, nowMs: Long, zone: ZoneId, grant: Grant? = null): Verdict {
        if (r.isEmpty) return Verdict.Unrestricted
        if (grant != null && nowMs < grant.untilMs) {
            val left = (grant.untilMs - nowMs) / 1000
            return Verdict.Allowed(left, warn = !grant.wholeDay && left <= WARN_SECONDS)
        }
        var left = Long.MAX_VALUE
        if (r.days.isNotEmpty()) {
            val until = allowedUntil(r, nowMs, zone)
            if (until != null) {
                if (until <= nowMs) return Verdict.Blocked(BlockReason.OUTSIDE_WINDOW, nextAllowed(r, nowMs, zone))
                left = (until - nowMs) / 1000
            }
        }
        r.dailyLimitMin?.let { lim ->
            val rest = lim * 60L - usedTodaySec
            if (rest <= 0) return Verdict.Blocked(BlockReason.LIMIT_REACHED, nextAllowed(r, nextMidnight(nowMs, zone), zone))
            left = minOf(left, rest)
        }
        if (left == Long.MAX_VALUE) return Verdict.Unrestricted
        return Verdict.Allowed(left, warn = left <= WARN_SECONDS)
    }

    /** Dérogation « +30 min » : prolonge la dérogation en cours, sinon part de maintenant. */
    fun extend(grant: Grant?, nowMs: Long): Grant =
        Grant(maxOf(nowMs, grant?.untilMs ?: 0L) + EXTRA_MINUTES * 60_000L, wholeDay = false)

    /** Dérogation « pour aujourd'hui » : jusqu'à minuit local. */
    fun forToday(nowMs: Long, zone: ZoneId): Grant = Grant(nextMidnight(nowMs, zone), wholeDay = true)

    // ── Cumul par jour local ──

    /** Découpe [fromMs, toMs[ par jour local (durée réelle écoulée : le changement d'heure ne fausse pas le cumul). Millisecondes par jour. */
    fun splitByDay(fromMs: Long, toMs: Long, zone: ZoneId): Map<String, Long> {
        val out = linkedMapOf<String, Long>()
        var a = fromMs
        while (a < toMs) {
            val end = minOf(toMs, nextMidnight(a, zone))
            val k = dayKey(a, zone)
            out[k] = (out[k] ?: 0L) + (end - a)
            a = end
        }
        return out
    }

    /** Ajoute la lecture de [fromMs, toMs[ au cumul (en millisecondes par jour local). */
    fun addUsage(usage: Map<String, Long>, fromMs: Long, toMs: Long, zone: ZoneId): Map<String, Long> {
        val m = usage.toMutableMap()
        for ((k, ms) in splitByDay(fromMs, toMs, zone)) m[k] = (m[k] ?: 0L) + ms
        return m
    }

    /** Durée créditée entre deux ticks : plafonnée (veille, appli gelée) et jamais négative (horloge reculée). */
    fun creditMs(lastMs: Long, nowMs: Long, capMs: Long = 5_000L): Long = (nowMs - lastMs).coerceIn(0L, capMs)

    /** Ne garde que les [KEEP_DAYS] derniers jours (clés « aaaa-mm-jj » : l'ordre texte est l'ordre des dates). */
    fun prune(usage: Map<String, Long>): Map<String, Long> =
        usage.entries.sortedByDescending { it.key }.take(KEEP_DAYS).associate { it.key to it.value }

    fun usageToJson(usage: Map<String, Long>): String =
        org.json.JSONObject().also { o -> usage.forEach { (k, v) -> o.put(k, v) } }.toString()

    fun usageFromJson(raw: String?): Map<String, Long> = runCatching {
        val o = org.json.JSONObject(raw ?: return emptyMap())
        o.keys().asSequence().associateWith { o.optLong(it) }.filterValues { it >= 0 }
    }.getOrDefault(emptyMap())

    /** Secondes de lecture cumulées pour le jour local de [nowMs]. */
    fun usedToday(usage: Map<String, Long>, nowMs: Long, zone: ZoneId): Long = (usage[dayKey(nowMs, zone)] ?: 0L) / 1000
}

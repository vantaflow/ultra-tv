package com.ultratv.tv.nativeapp.data.replay

import java.util.Calendar
import java.util.TimeZone

/** Mode de rattrapage annoncé par `catchup="…"` dans une playlist M3U. */
enum class CatchupType {
    /** `catchup-source` est l'URL complète, avec ses jetons. */
    DEFAULT,
    /** `catchup-source` est ajouté à l'URL du direct. */
    APPEND,
    /** Paramètres `utc` / `lutc` ajoutés à l'URL du direct. */
    SHIFT,
    /** Serveur Flussonic : `timeshift_abs-…` ou `archive-…` à la place du dernier segment. */
    FLUSSONIC,
    /** Xtream Codes : l'URL `timeshift/…` est reconstruite depuis l'URL du direct. */
    XC;

    companion object {
        /** Valeurs usuelles, sans tenir compte de la casse. null si inconnu ou vide. */
        fun parse(raw: String?): CatchupType? = when (raw?.trim()?.lowercase()) {
            "default" -> DEFAULT
            "append" -> APPEND
            "shift", "timeshift" -> SHIFT
            "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> FLUSSONIC
            "xc", "xtream", "xtream-codes" -> XC
            else -> null
        }
    }
}

/**
 * Construction PURE des URL de rattrapage des playlists M3U (aucune E/S). Les URL contiennent des identifiants :
 * jamais journalisées ni affichées.
 *
 * Jetons reconnus dans un gabarit (formes `{x}` et `${x}`) :
 *  - `utc`, `start` : début, en secondes Unix ; `utcend`, `end` : fin ;
 *  - `lutc`, `now`, `timestamp` : instant présent ; `offset` : secondes écoulées depuis le début ;
 *  - `duration` : durée en secondes (`duration:60` = en minutes, de même `offset:60`) ;
 *  - `Y`, `m`, `d`, `H`, `M`, `S` : début en UTC (année, mois, jour, heure, minute, seconde).
 */
object CatchupFormats {
    /** Jours d'archive supposés quand la playlist annonce un mode sans `catchup-days`. */
    const val DEFAULT_DAYS = 5

    /**
     * Gabarit unique stocké avec la chaîne ([com.ultratv.tv.nativeapp.data.db.ChannelEntity.catchupSource]),
     * calculé à la lecture de la playlist. null : rien à stocker (mode XC, ou aucune information de rattrapage).
     */
    fun template(type: CatchupType?, source: String?, streamUrl: String): String? {
        val src = source?.trim()?.takeIf { it.isNotEmpty() }
        return when (type) {
            CatchupType.XC -> null
            CatchupType.APPEND -> src?.let { streamUrl + it }
            CatchupType.SHIFT -> if (src != null) streamUrl + src else streamUrl + (if ('?' in streamUrl) "&" else "?") + "utc={utc}&lutc={lutc}"
            CatchupType.FLUSSONIC -> flussonic(src, streamUrl)
            // Sans `catchup`, un `catchup-source` qui n'est pas une URL complète s'ajoute au direct (usage courant).
            CatchupType.DEFAULT, null -> src?.let { if (type == null && !it.startsWith("http", ignoreCase = true)) streamUrl + it else it }
        }
    }

    private fun flussonic(src: String?, streamUrl: String): String? {
        if (src != null) return if (src.startsWith("http", ignoreCase = true)) src else streamUrl.substringBefore('?').substringBeforeLast('/') + "/" + src.trimStart('/')
        val noQuery = streamUrl.substringBefore('?')
        val query = streamUrl.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        val slash = noQuery.lastIndexOf('/')
        if (slash < "https://x".length) return null
        val dir = noQuery.substring(0, slash)
        val last = noQuery.substring(slash + 1)
        return when {
            last.equals("mpegts", true) || last.endsWith(".ts", true) -> "$dir/timeshift_abs-{utc}.ts$query"
            last.endsWith(".m3u8", true) -> "$dir/archive-{utc}-{duration}.m3u8$query"
            else -> null
        }
    }

    /** Remplace les jetons du gabarit pour un programme [startMs]..[endMs]. */
    fun fill(template: String, startMs: Long, endMs: Long, nowMs: Long): String {
        val startSec = startMs / 1000
        val durSec = ((endMs - startMs) / 1000).coerceAtLeast(60)
        val offSec = ((nowMs - startMs) / 1000).coerceAtLeast(0)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = startMs }
        // Locale.ROOT : la locale par défaut peut être remise en arabe par le framework, l'adresse serait fausse (replay en échec).
        fun two(n: Int) = String.format(java.util.Locale.ROOT, "%02d", n)
        val named = mapOf(
            "utc" to startSec.toString(), "start" to startSec.toString(),
            "utcend" to (endMs / 1000).toString(), "end" to (endMs / 1000).toString(),
            "lutc" to (nowMs / 1000).toString(), "now" to (nowMs / 1000).toString(), "timestamp" to (nowMs / 1000).toString(),
            "Y" to cal.get(Calendar.YEAR).toString(), "m" to two(cal.get(Calendar.MONTH) + 1), "d" to two(cal.get(Calendar.DAY_OF_MONTH)),
            "H" to two(cal.get(Calendar.HOUR_OF_DAY)), "M" to two(cal.get(Calendar.MINUTE)), "S" to two(cal.get(Calendar.SECOND)),
        )
        return TOKEN.replace(template) { m ->
            val name = m.groupValues[1]
            val div = m.groupValues[2].toLongOrNull()?.takeIf { it > 0 }
            when (name) {
                "duration" -> (durSec / (div ?: 1)).toString()
                "offset" -> (offSec / (div ?: 1)).toString()
                else -> named[name] ?: m.value
            }
        }
    }

    // Accolade fermante échappée : le moteur de regex d'Android (ICU) refuse un « } » nu, que la JVM des tests accepte.
    // Sans échappement, l'initialisation de l'objet échoue sur l'appareil et toute synchro M3U plante (NoClassDefFoundError).
    private val TOKEN = Regex("""\$?\{(\w+)(?::(\d+))?\}""")
}

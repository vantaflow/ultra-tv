package com.ultratv.tv.nativeapp.data.skip

import org.json.JSONArray
import org.json.JSONObject

/**
 * Passages à sauter (intro, rappel, générique) d'un film ou d'un épisode, fournis par TheIntroDB
 * (base communautaire, sans clé). Logique pure, testée à part : construction de l'URL, lecture de la réponse,
 * choix du passage en cours à une position donnée.
 */
enum class SkipKind { INTRO, RECAP, CREDITS }

/** [endMs] nul = jusqu'à la fin du média. Début nul côté API = depuis le tout début (ramené à 0). */
data class SkipSegment(val kind: SkipKind, val startMs: Long, val endMs: Long?)

/** Ce que le lecteur doit proposer : sauter jusqu'à [targetMs], ou passer à l'épisode suivant (générique final). */
data class SkipOffer(val kind: SkipKind, val targetMs: Long?, val toNextEpisode: Boolean)

object SkipSegmentsLogic {
    const val BASE_URL = "https://api.theintrodb.org/v4/media"

    /** Marge avant la fin d'un passage : le bouton disparaît un peu avant pour ne pas sauter 1 s inutilement. */
    const val END_MARGIN_MS = 1_500L

    fun url(tmdbId: Int, season: Int?, episode: Int?, durationMs: Long?): String = buildString {
        append(BASE_URL).append("?tmdb_id=").append(tmdbId)
        if (season != null && episode != null) append("&season=").append(season).append("&episode=").append(episode)
        if (durationMs != null && durationMs > 0) append("&duration_ms=").append(durationMs)
    }

    /** Réponse `/media` → passages valides, triés par début. Réponse illisible = liste vide (jamais d'exception). */
    fun parse(json: String): List<SkipSegment> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<SkipSegment>()
        fun read(key: String, kind: SkipKind) {
            val arr: JSONArray = root.optJSONArray(key) ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val start = if (o.isNull("start_ms")) 0L else o.optLong("start_ms", -1L)
                val end = if (o.isNull("end_ms") || !o.has("end_ms")) null else o.optLong("end_ms", -1L)
                if (start < 0 || (end != null && (end < 0 || end <= start))) continue
                out += SkipSegment(kind, start, end)
            }
        }
        read("intro", SkipKind.INTRO)
        read("recap", SkipKind.RECAP)
        read("credits", SkipKind.CREDITS)
        return out.sortedBy { it.startMs }
    }

    /**
     * Passage en cours à [positionMs] et action proposée. Intro / rappel : sauter à leur fin. Générique d'un épisode :
     * épisode suivant s'il en existe un ([hasNext]), sinon sauter à la fin du générique s'il s'arrête avant la fin
     * (scène post-générique). Générique d'un film qui va jusqu'au bout : rien (sauter reviendrait à quitter).
     */
    fun offerAt(segments: List<SkipSegment>, positionMs: Long, durationMs: Long, isEpisode: Boolean, hasNext: Boolean): SkipOffer? {
        if (positionMs < 0) return null
        for (s in segments) {
            val end = s.endMs ?: durationMs.takeIf { it > 0 }
            if (positionMs < s.startMs) continue
            if (end != null && positionMs >= end - END_MARGIN_MS) continue
            if (end == null && s.kind != SkipKind.CREDITS) continue
            return when (s.kind) {
                SkipKind.INTRO, SkipKind.RECAP -> SkipOffer(s.kind, end, toNextEpisode = false)
                SkipKind.CREDITS -> when {
                    isEpisode && hasNext -> SkipOffer(s.kind, null, toNextEpisode = true)
                    s.endMs != null && durationMs > 0 && s.endMs < durationMs - 5_000 -> SkipOffer(s.kind, s.endMs, toNextEpisode = false)
                    else -> null
                }
            }
        }
        return null
    }
}

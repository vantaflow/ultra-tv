package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity

/** Épisode repéré par sa saison et son numéro. Ordre naturel : saison puis épisode. */
data class EpisodeRef(val season: Int, val episode: Int) : Comparable<EpisodeRef> {
    override fun compareTo(other: EpisodeRef): Int = compareValuesBy(this, other, { it.season }, { it.episode })
    val key: String get() = TraktLibrary.episodeKey(season, episode)

    companion object {
        /** « 2x5 » → EpisodeRef(2, 5) ; null si illisible. */
        fun parse(key: String): EpisodeRef? {
            val i = key.indexOf('x')
            if (i <= 0) return null
            val s = key.substring(0, i).toIntOrNull() ?: return null
            val e = key.substring(i + 1).toIntOrNull() ?: return null
            return EpisodeRef(s, e)
        }
    }
}

/**
 * « À suivre » (logique pure, testée) : à partir des épisodes vus sur Trakt et des épisodes que la playlist propose
 * VRAIMENT, le prochain épisode à regarder.
 */
object TraktUpNext {
    /** Séries candidates : les plus récemment regardées d'abord (au plus [limit]). */
    const val MAX_CANDIDATES = 20
    const val MAX_CARDS = 15

    fun candidates(lib: TraktLibrary, limit: Int = MAX_CANDIDATES): List<TraktWatchedShow> =
        lib.watchedShows.filter { s -> s.episodes.any { (EpisodeRef.parse(it)?.season ?: 0) > 0 } }
            .sortedByDescending { it.lastWatchedAt }
            .take(limit)

    /**
     * Premier épisode disponible APRÈS le plus avancé des épisodes vus (saisons « spéciales » 0 ignorées) et non vu
     * lui-même. null si rien n'a été vu, si la série est à jour ou si la playlist ne propose pas la suite.
     */
    fun nextEpisode(watched: Set<String>, available: List<EpisodeRef>): EpisodeRef? {
        val last = watched.mapNotNull(EpisodeRef::parse).filter { it.season > 0 }.maxOrNull() ?: return null
        return available.asSequence().filter { it.season > 0 && it > last && it.key !in watched }.minOrNull()
    }
}

/** Reprise importée de Trakt (/sync/playback) rapprochée d'un élément du catalogue. */
data class PlaybackHit(
    val providerId: Long,
    val kind: String,              // "MOVIE" | "EPISODE"
    val remoteId: String,
    val title: String,
    val poster: String?,
    val streamUrl: String,
    val parentRemoteId: String?,
    val progress: Double,
    val pausedAt: Long,
    /** Durée connue (Trakt, sinon la source), en ms ; 0 = inconnue. */
    val durationMs: Long,
) {
    /** Clé d'import : une même pause n'est importée qu'une fois (même si l'utilisateur retire ensuite la carte). */
    val importKey: String get() = "$providerId|$kind|$remoteId|$pausedAt"
}

/** Décision d'import d'une reprise Trakt dans l'historique local (logique pure, testée). */
object TraktResume {
    /** En deçà, une reprise n'a pas d'intérêt (même seuil que le lecteur pour mémoriser une position). */
    const val MIN_POSITION_MS = 30_000L

    /**
     * Ligne d'historique à écrire, ou null pour ne rien faire : durée inconnue, position trop courte, ou position
     * locale PLUS RÉCENTE que la pause Trakt (jamais écrasée).
     */
    fun decide(existing: WatchHistoryEntity?, hit: PlaybackHit, profileId: Long, now: Long): WatchHistoryEntity? {
        if (existing != null && existing.watchedAt >= hit.pausedAt) return null
        val dur = existing?.durationMs?.takeIf { it > 0 } ?: hit.durationMs
        if (dur <= 0) return null
        val pos = (hit.progress / 100.0 * dur).toLong()
        if (pos < MIN_POSITION_MS || pos >= dur - 60_000) return null
        val at = if (hit.pausedAt in 1..now) hit.pausedAt else now
        return WatchHistoryEntity(
            providerId = hit.providerId, kind = hit.kind, remoteId = hit.remoteId,
            title = existing?.title ?: hit.title, poster = existing?.poster ?: hit.poster, streamUrl = hit.streamUrl,
            positionMs = pos, durationMs = dur, watchedAt = at, parentRemoteId = hit.parentRemoteId, profileId = profileId,
        )
    }

    /** Durée fournie par la source (« 01:42:10 », « 42:10 », « 95 » minutes, « 95 min ») → ms ; 0 si illisible. */
    fun parseDurationMs(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0L
        val parts = s.split(':')
        if (parts.size in 2..3 && parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) }) {
            val n = parts.map { it.toLong() }
            return if (n.size == 3) ((n[0] * 60 + n[1]) * 60 + n[2]) * 1000 else (n[0] * 60 + n[1]) * 1000
        }
        val min = Regex("^(\\d{1,4})\\s*(min|mn|m)?$", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.toLongOrNull() ?: return 0L
        return min * 60_000
    }
}

package com.ultratv.tv.nativeapp.data.extmeta

import com.ultratv.tv.nativeapp.data.tmdb.ExternalIds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logos et fonds Fanart.tv (via le Worker). Films : identifiant TMDB ; séries : identifiant TheTVDB. Cache mémoire LRU
 * (24 h, réponses vides comprises) ; Worker sans clé (503) : plus aucun appel pendant 30 min. Tout échec rend null.
 */
@Singleton
class FanartRepository @Inject constructor(private val api: ExtMetaApi) {
    private val cache = MemoCache<FanartArt>(max = 150, ttlMs = 24L * 3_600_000)
    @Volatile private var offUntil = 0L

    suspend fun movie(tmdbId: Int, lang: String, now: Long = System.currentTimeMillis()): FanartArt? = load("movie/$tmdbId", lang, now)
    suspend fun tv(tvdbId: Int, lang: String, now: Long = System.currentTimeMillis()): FanartArt? = load("tv/$tvdbId", lang, now)


    private fun key(path: String, lang: String) = "$path|${safeLang(lang)}"
    private fun safeLang(lang: String) = lang.lowercase().take(2).takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } } ?: "en"

    private suspend fun load(path: String, lang: String, now: Long): FanartArt? {
        if (!api.isEnabled() || now < offUntil) return null
        val l = safeLang(lang)
        return cache.getOrLoad(key(path, l), now) {
            val r = api.get("fanart/$path?lang=$l") ?: throw SkipCache()
            when (r.code) {
                200 -> ExtMetaParser.fanart(r.body)?.takeUnless { it.isEmpty }
                404 -> null
                503 -> { offUntil = now + OFF_MS; throw SkipCache() }
                else -> throw SkipCache()
            }
        }
    }

    private companion object { const val OFF_MS = 30L * 60_000 }
}

/** Calendrier TVmaze d'une série (via le Worker) par identifiant IMDb, sinon TheTVDB. Cache mémoire de 6 h ; échec : null. */
@Singleton
class TvMazeRepository @Inject constructor(private val api: ExtMetaApi) {
    private val cache = MemoCache<TvShowInfo>(max = 200, ttlMs = TTL_MS)

    suspend fun show(ids: ExternalIds, now: Long = System.currentTimeMillis()): TvShowInfo? {
        val q = ids.imdbId?.let { "imdb=$it" } ?: ids.tvdbId?.let { "tvdb=$it" } ?: return null
        if (!api.isEnabled()) return null
        return cache.getOrLoad(q, now) {
            val r = api.get("tvmaze/show?$q") ?: throw SkipCache()
            when (r.code) {
                200 -> ExtMetaParser.tvShow(r.body)?.takeIf { it.id != null }
                404 -> null
                else -> throw SkipCache()
            }
        }
    }

    companion object { const val TTL_MS = 6L * 3_600_000 }
}

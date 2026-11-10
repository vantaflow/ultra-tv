package com.ultratv.tv.nativeapp.data.skip

import com.ultratv.tv.nativeapp.data.db.VodInfoDao
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.tmdb.parseSourceTmdbId
import com.ultratv.tv.nativeapp.data.trakt.DbTraktIdentityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passages à sauter d'un film ou d'un épisode, demandés directement à TheIntroDB (aucune clé, aucun identifiant
 * IPTV transmis : seulement l'identifiant TMDB, la saison, l'épisode et la durée). L'identifiant TMDB vient du cache
 * des fiches (rempli à l'ouverture de la fiche), sinon de celui fourni par la source pour un film.
 * Échec réseau, titre inconnu ou TMDB indisponible : liste vide, le lecteur n'affiche simplement rien.
 */
@Singleton
class SkipSegmentsRepository @Inject constructor(
    okHttp: OkHttpClient,
    private val identities: DbTraktIdentityResolver,
    private val vodInfo: VodInfoDao,
) {
    // Délais courts : un bouton qui arrive après l'intro ne sert à rien.
    private val http = okHttp.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

    private val cache = object : LinkedHashMap<String, List<SkipSegment>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipSegment>>?) = size > 40
    }

    suspend fun forItem(item: PlaybackContext.Item, durationMs: Long): List<SkipSegment> {
        if (item.kind != "MOVIE" && item.kind != "EPISODE") return emptyList()
        val key = "${item.providerId}|${item.kind}|${item.remoteId}"
        synchronized(cache) { cache[key] }?.let { return it }
        val id = runCatching { identities.resolve(item) }.getOrNull() ?: return emptyList()
        val tmdb = id.tmdb ?: if (item.kind == "MOVIE") runCatching { parseSourceTmdbId(vodInfo.get(item.providerId, item.remoteId)?.tmdbId) }.getOrNull() else null
        if (tmdb == null) return emptyList()
        if (item.kind == "EPISODE" && (id.season == null || id.episode == null)) return emptyList()
        val segments = fetch(SkipSegmentsLogic.url(tmdb, id.season, id.episode, durationMs)) ?: return emptyList()
        synchronized(cache) { cache[key] = segments }
        return segments
    }

    /** null = échec réseau (non mis en cache, on retentera à la prochaine lecture) ; 404 = aucun passage connu. */
    private suspend fun fetch(url: String): List<SkipSegment>? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").get().build()).execute().use { r ->
                when {
                    r.code == 404 -> emptyList()
                    r.isSuccessful -> SkipSegmentsLogic.parse(r.body?.string().orEmpty())
                    else -> null
                }
            }
        }.getOrNull()
    }
}

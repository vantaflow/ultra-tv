package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client `/api/ratings` du Worker (appareil appairé). Cache mémoire par titre : une fiche rouverte ne refait pas l'appel.
 * Worker sans clé OMDb (503), non appairé ou réseau absent : null, la fiche garde la note habituelle.
 */
@Singleton
class RatingsClient @Inject constructor(
    okHttp: OkHttpClient,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
) {
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val cache = object : LinkedHashMap<String, ExternalRatings?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExternalRatings?>?) = size > 80
    }

    suspend fun forTmdb(kind: TmdbKind, tmdbId: Int): ExternalRatings? {
        val k = "${kind.name}:$tmdbId"
        synchronized(cache) { if (cache.containsKey(k)) return cache[k] }
        if (!tokens.isPaired) return null
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val token = tokens.token() ?: return@runCatching null
                val base = WorkerUrl.normalize(WorkerUrl.resolve(prefs.flow.first().workerBaseUrl), BuildConfig.DEBUG) ?: return@runCatching null
                val q = "kind=${if (kind == TmdbKind.MOVIE) "movie" else "tv"}&tmdb=$tmdbId"
                val req = Request.Builder().url("$base/api/ratings?$q").header("Authorization", "Bearer $token").get().build()
                http.newCall(req).execute().use { res -> if (res.isSuccessful) Result.success(RatingsLogic.parse(res.body?.string().orEmpty())) else null }
            }.getOrNull()
        } ?: return null                                     // échec : rien en cache, on réessaiera
        val value = r.getOrNull()
        synchronized(cache) { cache[k] = value }
        return value
    }
}

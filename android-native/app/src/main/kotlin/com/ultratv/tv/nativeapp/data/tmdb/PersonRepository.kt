package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.FtsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Titres de la playlist où apparaît une personne. */
data class PersonWorks(val movies: List<MovieEntity>, val series: List<SeriesEntity>)

/**
 * Page acteur / réalisateur. Deux sources réunies : la filmographie TMDB (via le Worker, appareil appairé) rapprochée de
 * la playlist par titre + année, puis les titres dont la distribution locale cite ce nom (fiches déjà ouvertes, listes
 * de séries). Sans Worker, seule la seconde répond.
 */
@Singleton
class PersonRepository @Inject constructor(
    okHttp: OkHttpClient,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
) {
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun works(pid: Long, name: String): PersonWorks {
        val clean = name.trim().replace("%", "").replace("_", " ")
        if (clean.length < 3) return PersonWorks(emptyList(), emptyList())
        val tmdb = runCatching { tmdbCredits(clean) }.getOrDefault(emptyList())
        val movies = LinkedHashMap<Long, MovieEntity>()
        val series = LinkedHashMap<Long, SeriesEntity>()
        for (c in tmdb.take(MAX_CREDITS)) {
            val match = FtsQuery.of(c.title) ?: continue
            if (c.isTv) seriesDao.searchFts(pid, match, 12).firstOrNull { PersonLogic.matches(c, it.title, it.year) }?.let { series.putIfAbsent(it.id, it) }
            else movieDao.searchFts(pid, match, 12).firstOrNull { PersonLogic.matches(c, it.title, it.year) }?.let { movies.putIfAbsent(it.id, it) }
        }
        movieDao.byPerson(pid, clean, 60).forEach { movies.putIfAbsent(it.id, it) }
        seriesDao.byPerson(pid, clean, 60).forEach { series.putIfAbsent(it.id, it) }
        return PersonWorks(movies.values.toList(), series.values.toList())
    }

    private suspend fun tmdbCredits(name: String): List<PersonCredit> = withContext(Dispatchers.IO) {
        if (!tokens.isPaired) return@withContext emptyList()
        val token = tokens.token() ?: return@withContext emptyList()
        val base = WorkerUrl.normalize(prefs.flow.first().workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG) ?: return@withContext emptyList()
        fun get(path: String): String? = http.newCall(Request.Builder().url("$base/api/tmdb/$path").header("Authorization", "Bearer $token").get().build())
            .execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        val q = java.net.URLEncoder.encode(name, "UTF-8")
        val id = get("search/person?query=$q")?.let { PersonLogic.searchBest(it, name) } ?: return@withContext emptyList()
        get("person/$id/combined_credits")?.let { PersonLogic.credits(it) }.orEmpty()
    }

    private companion object { const val MAX_CREDITS = 150 }
}

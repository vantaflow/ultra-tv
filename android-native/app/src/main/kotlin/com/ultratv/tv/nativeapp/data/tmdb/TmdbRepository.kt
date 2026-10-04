package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.i18n.AppLang
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Accès réseau aux métadonnées TMDB (via le Worker). Interface : le dépôt se teste sans réseau. */
interface TmdbApi {
    /** Faux tant que l'appareil n'est pas appairé au Worker : aucune requête TMDB n'est alors émise. */
    fun isEnabled(): Boolean
    suspend fun searchId(kind: TmdbKind, query: TmdbQuery, lang: String): Int?
    suspend fun details(kind: TmdbKind, id: Int, lang: String): TmdbDetails?
}

/** Client du proxy TMDB du Worker (/api/tmdb/…) : jeton d'appareil en Bearer, pas de redirection suivie. */
@Singleton
class TmdbHttpApi @Inject constructor(
    okHttp: OkHttpClient,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
) : TmdbApi {
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override fun isEnabled() = tokens.isPaired

    private suspend fun get(pathAndQuery: String): String? = withContext(Dispatchers.IO) {
        val token = tokens.token() ?: return@withContext null
        val base = WorkerUrl.normalize(prefs.flow.first().workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG) ?: return@withContext null
        val req = Request.Builder().url("$base/api/tmdb/$pathAndQuery").header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { r ->
            when {
                r.code == 404 -> null
                r.isSuccessful -> r.body?.string()
                else -> throw java.io.IOException("TMDB proxy HTTP ${r.code}")   // 401/429/5xx : on garde le cache
            }
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    override suspend fun searchId(kind: TmdbKind, query: TmdbQuery, lang: String): Int? {
        val yearParam = query.year?.let { (if (kind == TmdbKind.MOVIE) "&year=" else "&first_air_date_year=") + it } ?: ""
        val body = get("search/${kind.path}?query=${enc(query.title)}$yearParam&language=$lang") ?: return null
        return TmdbParser.searchBest(body, query.year)
    }

    override suspend fun details(kind: TmdbKind, id: Int, lang: String): TmdbDetails? =
        get("${kind.path}/$id?append_to_response=credits,videos&language=$lang")?.let(TmdbParser::details)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TmdbModule {
    @Binds abstract fun api(impl: TmdbHttpApi): TmdbApi
}

/** Langue demandée à TMDB d'après la langue de l'application. */
fun tmdbLang(appLang: AppLang): String = when (appLang) {
    AppLang.French -> "fr-FR"
    AppLang.Spanish -> "es-ES"
    AppLang.Arabic -> "ar"
    else -> "en-US"
}

/**
 * Fiches enrichies : cache Room (30 jours), recherche par `tmdb_id` de la source sinon par titre nettoyé + année.
 * Désactivé (renvoie null, aucune requête) tant que l'appareil n'est pas appairé au Worker. Une erreur réseau
 * rend la ligne périmée si elle existe : la fiche reste lisible.
 */
@Singleton
class TmdbRepository @Inject constructor(
    private val api: TmdbApi,
    private val dao: TmdbDao,
    private val prefs: UserPreferencesStore,
) {
    suspend fun forMovie(m: MovieEntity, sourceTmdbId: String? = null, now: Long = System.currentTimeMillis()): TmdbInfoEntity? =
        load(TmdbKind.MOVIE, m.providerId, m.remoteId, tmdbQueryFor(m.name, m.year), parseSourceTmdbId(sourceTmdbId), now)

    suspend fun forSeries(s: SeriesEntity, sourceTmdbId: String? = null, now: Long = System.currentTimeMillis()): TmdbInfoEntity? =
        load(TmdbKind.TV, s.providerId, s.remoteId, tmdbQueryFor(s.name, s.year), parseSourceTmdbId(sourceTmdbId), now)

    /** Langue d'origine TMDB (« fr », « ja »…) déjà en cache, pour le détecteur de langue ; ne déclenche aucune requête. */
    suspend fun cachedOriginalLanguage(kind: TmdbKind, providerId: Long, remoteId: String): String? =
        dao.get(kind.path, providerId, remoteId)?.originalLanguage

    internal suspend fun load(kind: TmdbKind, pid: Long, rid: String, q: TmdbQuery, sourceId: Int?, now: Long): TmdbInfoEntity? {
        if (!api.isEnabled()) return null
        val lang = tmdbLang(AppLang.fromCode(prefs.flow.first().language).let { if (it == AppLang.System) systemLang() else it })
        val cached = dao.get(kind.path, pid, rid)
        if (cached != null && isFresh(cached, lang, now)) return cached
        return try {
            val id = sourceId ?: api.searchId(kind, q, lang)
            val d = id?.let { api.details(kind, it, lang) }
            val row = TmdbInfoEntity(
                kind = kind.path, providerId = pid, remoteId = rid, tmdbId = d?.tmdbId, overview = d?.overview,
                posterPath = d?.posterPath, backdropPath = d?.backdropPath, rating = d?.rating,
                cast = d?.cast?.takeIf { it.isNotEmpty() }?.joinToString(", "), trailerKey = d?.trailerKey,
                originalLanguage = d?.originalLanguage, lang = lang, fetchedAt = now,
            )
            dao.upsert(row)
            row
        } catch (e: java.io.IOException) {
            cached
        }
    }

    private fun systemLang(): AppLang = when (java.util.Locale.getDefault().language) {
        "fr" -> AppLang.French; "es" -> AppLang.Spanish; "ar" -> AppLang.Arabic; else -> AppLang.English
    }
}

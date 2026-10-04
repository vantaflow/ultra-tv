package com.ultratv.tv.nativeapp.data.tmdb

import android.content.Context
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.SearchDedup
import com.ultratv.tv.nativeapp.i18n.AppLang
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

const val POSTER_TTL_MS = 30L * 24 * 3_600_000
private const val POSTER_MAX_PARALLEL = 3

/**
 * Affiches de repli : quand un film / une série n'a pas d'affiche (ou qu'elle ne charge pas), on la cherche sur
 * TMDB (via le proxy du Worker) avec le titre nettoyé + l'année, puis sans l'année. Le résultat — « rien trouvé »
 * compris — est mémorisé 30 jours dans `tmdb_info` (clé `poster_<kind>` / titre normalisé). 3 requêtes
 * simultanées au plus ; aucune requête tant que l'appareil n'est pas appairé ; une erreur réseau n'est pas mémorisée.
 */
@Singleton
class PosterResolver internal constructor(
    private val api: TmdbApi,
    private val dao: TmdbDao,
    private val lang: suspend () -> String,
    private val clock: () -> Long,
) {
    @Inject constructor(api: TmdbApi, dao: TmdbDao, prefs: UserPreferencesStore) :
        this(api, dao, { defaultLang(prefs) }, System::currentTimeMillis)

    private val gate = Semaphore(POSTER_MAX_PARALLEL)
    private val memory = ConcurrentHashMap<String, String>()   // « » = rien trouvé

    /** URL de l'affiche TMDB (w342) ou null. À appeler hors du fil principal (la requête réseau est en IO). */
    suspend fun resolve(kind: TmdbKind, rawTitle: String, year: Int?): String? {
        if (!api.isEnabled() || rawTitle.isBlank()) return null
        val q = tmdbQueryFor(rawTitle, year)
        val rowKind = "poster_${kind.path}"
        val key = SearchDedup.fold(q.title) + "|" + (q.year ?: 0)
        if (key.startsWith("|")) return null
        val mk = "$rowKind/$key"
        memory[mk]?.let { return it.ifEmpty { null } }
        val cached = dao.get(rowKind, 0L, key)
        if (cached != null && clock() - cached.fetchedAt < POSTER_TTL_MS) {
            val p = cached.posterPath
            memory[mk] = p?.let { TmdbImages.poster342(it) }.orEmpty()
            return p?.let { TmdbImages.poster342(it) }
        }
        val path = try {
            gate.withPermit {
                val l = lang()
                api.searchPoster(kind, q, l) ?: if (q.year != null) api.searchPoster(kind, q.copy(year = null), l) else null
            }
        } catch (e: java.io.IOException) {
            return cached?.posterPath?.let { TmdbImages.poster342(it) }   // erreur transitoire : on ne mémorise pas
        }
        dao.upsert(TmdbInfoEntity(kind = rowKind, providerId = 0L, remoteId = key, posterPath = path, fetchedAt = clock()))
        val url = path?.let { TmdbImages.poster342(it) }
        memory[mk] = url.orEmpty()
        return url
    }

    companion object {
        private suspend fun defaultLang(prefs: UserPreferencesStore): String {
            val app = AppLang.fromCode(prefs.flow.first().language)
            val sys = when (java.util.Locale.getDefault().language) { "fr" -> AppLang.French; "es" -> AppLang.Spanish; "ar" -> AppLang.Arabic; else -> AppLang.English }
            return tmdbLang(if (app == AppLang.System) sys else app)
        }

        fun from(ctx: Context): PosterResolver =
            EntryPointAccessors.fromApplication(ctx.applicationContext, PosterEntryPoint::class.java).posterResolver()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PosterEntryPoint { fun posterResolver(): PosterResolver }

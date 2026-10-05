package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.VodInfoDao
import com.ultratv.tv.nativeapp.data.db.VodInfoEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeDao
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.FavoriteDao
import com.ultratv.tv.nativeapp.data.db.FavoriteEntity
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side catalog repository — joins providers with content tables.
 * Most flows take a provider id; when null, callers should resolve the active
 * provider via [ProviderRepository.firstActive] first.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val epgDao: EpgDao,
    private val vodInfoDao: VodInfoDao,
    private val xtream: XtreamClient,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
) {
    fun heroSeries(pid: Long): Flow<SeriesEntity?> = seriesDao.observeHero(pid)
    fun heroMovie(pid: Long): Flow<MovieEntity?> = movieDao.observeHero(pid)
    fun latestMovies(pid: Long, limit: Int): Flow<List<MovieEntity>> = movieDao.observeLatest(pid, limit)
    fun latestSeries(pid: Long, limit: Int): Flow<List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>> = seriesDao.observeLatest(pid, limit)
    fun channelsWithLogo(pid: Long, limit: Int): Flow<List<ChannelEntity>> = channelDao.observeTopWithLogo(pid, limit)
    fun favoriteChannels(pid: Long, limit: Int): Flow<List<ChannelEntity>> =
        profiles.currentId.flatMapLatest { channelDao.observeFavoritesTop(pid, limit, it) }
    fun channels(pid: Long): Flow<List<ChannelEntity>> = channelDao.observeForProvider(pid)
    fun topChannels(pid: Long, limit: Int): Flow<List<ChannelEntity>> = channelDao.observeTop(pid, limit)
    fun topMovies(pid: Long, limit: Int): Flow<List<MovieEntity>> = movieDao.observeTop(pid, limit)
    fun topSeries(pid: Long, limit: Int): Flow<List<SeriesEntity>> = seriesDao.observeTop(pid, limit)
    fun channelsForCategory(pid: Long, categoryRemoteId: String): Flow<List<ChannelEntity>> =
        channelDao.observeForCategory(pid, categoryRemoteId)
    fun movies(pid: Long): Flow<List<MovieEntity>> = movieDao.observeForProvider(pid)
    fun seriesList(pid: Long): Flow<List<SeriesEntity>> = seriesDao.observeForProvider(pid)
    fun episodes(seriesId: Long): Flow<List<EpisodeEntity>> = episodeDao.observeForSeries(seriesId)

    fun categories(pid: Long, kind: String): Flow<List<CategoryEntity>> =
        categoryDao.observeForProviderKind(pid, kind)

    suspend fun channelById(id: Long): ChannelEntity? = channelDao.byId(id)
    suspend fun movieById(id: Long): MovieEntity? = movieDao.byId(id)
    suspend fun seriesById(id: Long): SeriesEntity? = seriesDao.byId(id)
    suspend fun seriesByRemote(pid: Long, remoteId: String): SeriesEntity? = seriesDao.byRemoteId(pid, remoteId)
    suspend fun episodeById(id: Long): EpisodeEntity? = episodeDao.byId(id)

    /**
     * Lazily syncs episodes when the user opens a series detail. Dispatches
     * to the right client based on provider kind — Xtream uses `get_series_info`
     * (per-season episode map).
     * M3U has no concept of series and is silently skipped.
     */
    suspend fun loadEpisodes(seriesId: Long) {
        val s = seriesDao.byId(seriesId) ?: return
        val p = providerDao.byId(s.providerId) ?: return
        val eps = when (p.kind) {
            "XTREAM" -> {
                val d = xtream.fetchSeriesDetail(p, s.remoteId, s.id) ?: return
                seriesDao.updateInfo(s.id, d.plot, d.genre, d.cast, d.backdrop, d.year, d.rating)
                d.episodes
            }
            else -> return
        }
        episodeDao.deleteForSeries(seriesId)
        episodeDao.upsertAll(eps)
    }

    /**
     * Détails d'un film : cache Room (TTL 7 jours ; 1 jour si la source n'a rien renvoyé), sinon get_vod_info.
     * Xtream seulement ; une erreur réseau rend le cache périmé s'il existe, sinon null (la fiche reste lisible).
     */
    suspend fun vodInfo(m: MovieEntity, now: Long = System.currentTimeMillis()): VodInfoEntity? {
        val cached = vodInfoDao.get(m.providerId, m.remoteId)
        if (cached != null && now - cached.fetchedAt < vodInfoTtlMs(cached)) return cached
        val p = providerDao.byId(m.providerId)
        if (p == null || p.kind != "XTREAM") return cached
        val fetched = runCatching { xtream.fetchVodInfo(p, m.remoteId) }.getOrNull() ?: return cached
        val row = VodInfoEntity(
            providerId = m.providerId, remoteId = m.remoteId, plot = fetched.plot, cast = fetched.cast, director = fetched.director,
            genre = fetched.genre, duration = fetched.duration, releaseDate = fetched.releaseDate, rating = fetched.rating,
            backdrop = fetched.backdrop, trailer = fetched.trailer, tmdbId = fetched.tmdbId, country = fetched.country,
            originalName = fetched.originalName, fetchedAt = now,
        )
        vodInfoDao.upsert(row)
        return row
    }

    /**
     * Recherche plein texte (FTS4) : instantanée même sur 180 000 titres. Une seule entrée par œuvre
     * ([SearchDedup]) ; les programmes EPG (7 jours, une ligne par chaîne) s'ajoutent aux chaînes/films/séries.
     */
    suspend fun search(pid: Long, query: String, limit: Int = 40, nowMs: Long = System.currentTimeMillis()): SearchResults {
        val match = FtsQuery.of(query) ?: return SearchResults()
        return SearchResults(
            channels = channelDao.searchFts(pid, match, limit),
            movies = SearchDedup.movies(movieDao.searchFts(pid, match, limit * 3)).take(limit),
            series = SearchDedup.series(seriesDao.searchFts(pid, match, limit * 3)).take(limit),
            programs = searchPrograms(pid, query, nowMs),
        )
    }

    suspend fun searchPrograms(pid: Long, query: String, nowMs: Long = System.currentTimeMillis()): List<ProgramHit> {
        val pattern = EpgSearch.likePattern(query) ?: return emptyList()
        val rows = epgDao.searchPrograms(pid, pattern, nowMs, nowMs + EpgSearch.WINDOW_MS, EpgSearch.SQL_LIMIT)
        return EpgSearch.select(rows, query, nowMs)
    }

    suspend fun channelsByRemoteIds(pid: Long, ids: List<String>): List<ChannelEntity> =
        ids.chunked(500).flatMap { channelDao.byRemoteIds(pid, it) }

    suspend fun searchChannels(pid: Long, query: String, limit: Int = 60): List<ChannelEntity> {
        val match = FtsQuery.of(query) ?: return emptyList()
        return channelDao.searchFts(pid, match, limit)
    }

    fun favoritesByKind(pid: Long, kind: String): Flow<List<FavoriteEntity>> =
        profiles.currentId.flatMapLatest { favoriteDao.observeForKind(it, pid, kind) }

    fun favoriteCount(pid: Long, kind: String): Flow<Int> =
        profiles.currentId.flatMapLatest { favoriteDao.observeCount(it, pid, kind) }

    fun isFavorite(pid: Long, kind: String, rid: String): Flow<Boolean> =
        profiles.currentId.flatMapLatest { favoriteDao.observeIsFavorite(it, pid, kind, rid) }

    suspend fun setFavorite(pid: Long, kind: String, rid: String, on: Boolean) {
        val prof = profiles.currentIdNow
        if (on) favoriteDao.add(FavoriteEntity(pid, kind, rid, prof))
        else favoriteDao.remove(prof, pid, kind, rid)
    }

    suspend fun setCategoryLocked(catId: Long, locked: Boolean) =
        categoryDao.setLocked(catId, locked)

    fun upcomingEpg(channelId: Long): kotlinx.coroutines.flow.Flow<List<EpgEntity>> =
        epgDao.observeUpcoming(channelId, System.currentTimeMillis())

    private val shortEpgTried = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val shortEpgGate = kotlinx.coroutines.sync.Semaphore(3)

    /**
     * Secours quand le guide XMLTV n'est pas (encore) là : programme court du fournisseur pour les chaînes données,
     * au plus une tentative par chaîne toutes les 15 min, 3 requêtes à la fois. Renvoie true si au moins une a été écrite.
     */
    suspend fun ensureShortEpg(channelIds: List<Long>): Boolean = kotlinx.coroutines.coroutineScope {
        val now = System.currentTimeMillis()
        val todo = channelIds.filter { now - (shortEpgTried[it] ?: 0L) > 15 * 60_000L }.take(12)
        todo.forEach { shortEpgTried[it] = now }
        todo.map { id -> async { shortEpgGate.withPermit { refreshShortEpgCount(id) > 0 } } }.awaitAll().any { it }
    }

    private suspend fun refreshShortEpgCount(channelId: Long): Int {
        val ch = channelDao.byId(channelId) ?: return 0
        val p = providerDao.byId(ch.providerId) ?: return 0
        if (p.kind != "XTREAM") return 0
        var rows = runCatching { xtream.fetchShortEpg(p, ch.remoteId, ch.id) }.getOrDefault(emptyList())
        // Chaîne décalée sans programme propre : celui de la chaîne de base, décalé de N heures.
        // Sinon, même chaîne sous le même nom dans une autre catégorie (seule l'une porte souvent l'identifiant EPG).
        if (rows.isEmpty()) (TimeshiftChannels.parse(ch.title) ?: (ch.title.trim() to 0)).let { (base, h) ->
            val shift = h * 3_600_000L
            val now = System.currentTimeMillis()
            val baseIds = channelDao.idsByTitle(p.id, base).filter { it != ch.id }
            for (bid in baseIds) {
                var src = epgDao.rangeForChannels(listOf(bid), now - shift - 30 * 60_000, now + 6 * 3_600_000L)
                if (src.isEmpty()) { refreshShortEpg(bid); src = epgDao.rangeForChannels(listOf(bid), now - shift - 30 * 60_000, now + 6 * 3_600_000L) }
                if (src.isNotEmpty()) { rows = src.map { it.copy(id = 0, channelId = ch.id, startMs = it.startMs + shift, endMs = it.endMs + shift) }; break }
            }
        }
        if (rows.isNotEmpty()) { epgDao.deleteForChannel(ch.id); epgDao.upsertAll(rows) }
        return rows.size
    }

    /** Pulls short EPG for one channel from Xtream. Safe to call repeatedly — no-op on failure. */
    suspend fun refreshShortEpg(channelId: Long) {
        val ch = channelDao.byId(channelId) ?: return
        val p = providerDao.byId(ch.providerId) ?: return
        val rows = runCatching { xtream.fetchShortEpg(p, ch.remoteId, ch.id) }.getOrDefault(emptyList())
        if (rows.isNotEmpty()) {
            epgDao.deleteForChannel(ch.id)
            epgDao.upsertAll(rows)
        }
    }
}

data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val programs: List<ProgramHit> = emptyList(),
)

private const val DAY_MS = 24L * 3_600_000

/** Durée de validité d'une ligne de cache : 7 jours si elle porte des détails, 1 jour si elle est vide. */
internal fun vodInfoTtlMs(v: VodInfoEntity): Long =
    if (v.plot == null && v.cast == null && v.genre == null && v.backdrop == null) DAY_MS else 7 * DAY_MS

package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
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
import kotlinx.coroutines.flow.Flow
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
    private val xtream: XtreamClient,
    private val stalker: com.ultratv.tv.nativeapp.data.stalker.StalkerClient,
) {
    fun heroSeries(pid: Long): Flow<SeriesEntity?> = seriesDao.observeHero(pid)
    fun heroMovie(pid: Long): Flow<MovieEntity?> = movieDao.observeHero(pid)
    fun channelsWithLogo(pid: Long, limit: Int): Flow<List<ChannelEntity>> = channelDao.observeTopWithLogo(pid, limit)
    fun favoriteChannels(pid: Long, limit: Int): Flow<List<ChannelEntity>> = channelDao.observeFavoritesTop(pid, limit)
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
    suspend fun episodeById(id: Long): EpisodeEntity? = episodeDao.byId(id)

    /**
     * Lazily syncs episodes when the user opens a series detail. Dispatches
     * to the right client based on provider kind — Xtream uses `get_series_info`
     * (per-season episode map), Stalker uses `get_ordered_list` (flat list).
     * M3U has no concept of series and is silently skipped.
     */
    suspend fun loadEpisodes(seriesId: Long) {
        val s = seriesDao.byId(seriesId) ?: return
        val p = providerDao.byId(s.providerId) ?: return
        val eps = when (p.kind) {
            "XTREAM" -> xtream.fetchSeriesEpisodes(p, s.remoteId, s.id)
            "STALKER" -> {
                val session = runCatching { stalker.handshake(p) }.getOrNull() ?: return
                stalker.fetchSeriesEpisodes(p, session, s.remoteId, s.id)
            }
            else -> return
        }
        episodeDao.deleteForSeries(seriesId)
        episodeDao.upsertAll(eps)
    }

    /** Recherche plein texte (FTS4) : instantanée même sur 180 000 titres. */
    suspend fun search(pid: Long, query: String, limit: Int = 40): SearchResults {
        val match = FtsQuery.of(query) ?: return SearchResults()
        return SearchResults(
            channels = channelDao.searchFts(pid, match, limit),
            movies = movieDao.searchFts(pid, match, limit),
            series = seriesDao.searchFts(pid, match, limit),
        )
    }

    suspend fun channelsByRemoteIds(pid: Long, ids: List<String>): List<ChannelEntity> =
        ids.chunked(500).flatMap { channelDao.byRemoteIds(pid, it) }

    suspend fun searchChannels(pid: Long, query: String, limit: Int = 60): List<ChannelEntity> {
        val match = FtsQuery.of(query) ?: return emptyList()
        return channelDao.searchFts(pid, match, limit)
    }

    fun favoritesByKind(pid: Long, kind: String): Flow<List<FavoriteEntity>> =
        favoriteDao.observeForKind(pid, kind)

    fun isFavorite(pid: Long, kind: String, rid: String): Flow<Boolean> =
        favoriteDao.observeIsFavorite(pid, kind, rid)

    suspend fun setFavorite(pid: Long, kind: String, rid: String, on: Boolean) {
        if (on) favoriteDao.add(FavoriteEntity(pid, kind, rid))
        else favoriteDao.remove(pid, kind, rid)
    }

    suspend fun setCategoryLocked(catId: Long, locked: Boolean) =
        categoryDao.setLocked(catId, locked)

    fun upcomingEpg(channelId: Long): kotlinx.coroutines.flow.Flow<List<EpgEntity>> =
        epgDao.observeUpcoming(channelId, System.currentTimeMillis())

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
)

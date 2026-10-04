package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.WatchHistoryDao
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: WatchHistoryDao,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
) {
    fun recent(pid: Long, limit: Int = 30): Flow<List<WatchHistoryEntity>> =
        profiles.currentId.flatMapLatest { dao.observeRecent(it, pid, limit) }

    fun recentByKind(pid: Long, kind: String, limit: Int = 30): Flow<List<WatchHistoryEntity>> =
        profiles.currentId.flatMapLatest { dao.observeRecentByKind(it, pid, kind, limit) }

    fun continueWatching(pid: Long, limit: Int = 20): Flow<List<WatchHistoryEntity>> =
        profiles.currentId.flatMapLatest { dao.observeContinueWatching(it, pid, limit) }

    fun episodesOf(pid: Long, seriesRemoteId: String): Flow<List<WatchHistoryEntity>> =
        profiles.currentId.flatMapLatest { dao.observeEpisodesOf(it, pid, seriesRemoteId) }

    suspend fun record(
        providerId: Long,
        kind: String,
        remoteId: String,
        title: String,
        poster: String?,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long,
        parentRemoteId: String? = null,
    ) {
        dao.upsert(
            WatchHistoryEntity(
                providerId = providerId,
                kind = kind,
                remoteId = remoteId,
                title = title,
                poster = poster,
                streamUrl = streamUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAt = System.currentTimeMillis(),
                parentRemoteId = parentRemoteId,
                profileId = profiles.currentIdNow,
            ),
        )
    }

    suspend fun remove(providerId: Long, kind: String, remoteId: String) =
        dao.remove(profiles.currentIdNow, providerId, kind, remoteId)

    /** Resume position in ms for VOD/episode. 0 (or near-end) → start from the
     *  beginning. Caller decides the threshold for "near-end". */
    suspend fun resumePositionMs(providerId: Long, kind: String, remoteId: String): Long =
        dao.positionFor(profiles.currentIdNow, providerId, kind, remoteId) ?: 0L
}

package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.FavoriteDao
import com.ultratv.tv.nativeapp.data.db.FavoriteEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryDao
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup / restore of the user's state. The export bundles providers
 * (including plaintext credentials — caller's responsibility to store the
 * file securely), favorites, and recent watch history. Catalogs (channels /
 * movies / series / episodes / EPG) are NOT backed up: re-syncing pulls
 * them fresh and they're orders of magnitude larger.
 *
 * Format version is bumped when the schema changes; restore refuses unknown
 * versions to avoid silently dropping data.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: WatchHistoryDao,
) {
    @Serializable
    data class Bundle(
        val version: Int = CURRENT_VERSION,
        val exportedAt: Long = System.currentTimeMillis(),
        val providers: List<Provider> = emptyList(),
        val favorites: List<Favorite> = emptyList(),
        val history: List<History> = emptyList(),
    )

    @Serializable
    data class Provider(
        val name: String, val kind: String, val baseUrl: String,
        val username: String, val password: String, val active: Boolean,
    )

    @Serializable
    data class Favorite(val providerKey: String, val kind: String, val remoteId: String)

    @Serializable
    data class History(
        val providerKey: String, val kind: String, val remoteId: String,
        val title: String, val poster: String?, val streamUrl: String,
        val positionMs: Long, val durationMs: Long, val watchedAt: Long,
        val parentRemoteId: String?,
    )

    companion object {
        const val CURRENT_VERSION = 1
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Snapshot the current state into a JSON string. */
    suspend fun export(): String {
        val providers = providerDao.observeAll().first()
        // Provider key = (kind|baseUrl|username) — stable across re-installs, lets
        // us re-link favorites/history without relying on local ids.
        fun keyFor(p: ProviderEntity) = "${p.kind}|${p.baseUrl}|${p.username}"
        val idToKey = providers.associate { it.id to keyFor(it) }

        val favs = mutableListOf<Favorite>()
        val hist = mutableListOf<History>()
        for (p in providers) {
            favoriteDao.observeForKind(p.id, "MOVIE").first().forEach {
                favs += Favorite(keyFor(p), it.kind, it.remoteId)
            }
            favoriteDao.observeForKind(p.id, "SERIES").first().forEach {
                favs += Favorite(keyFor(p), it.kind, it.remoteId)
            }
            historyDao.observeRecent(p.id, 200).first().forEach { h ->
                val key = idToKey[h.providerId] ?: return@forEach
                hist += History(
                    providerKey = key,
                    kind = h.kind, remoteId = h.remoteId,
                    title = h.title, poster = h.poster, streamUrl = h.streamUrl,
                    positionMs = h.positionMs, durationMs = h.durationMs,
                    watchedAt = h.watchedAt, parentRemoteId = h.parentRemoteId,
                )
            }
        }

        val bundle = Bundle(
            providers = providers.map {
                Provider(it.name, it.kind, it.baseUrl, it.username, it.password, it.active)
            },
            favorites = favs,
            history = hist,
        )
        return json.encodeToString(Bundle.serializer(), bundle)
    }

    /** Restore a backup. Returns RestoreResult with counters; throws on bad input. */
    suspend fun import(text: String): RestoreResult {
        val bundle = json.decodeFromString(Bundle.serializer(), text)
        require(bundle.version == CURRENT_VERSION) {
            "Unsupported backup version ${bundle.version}"
        }

        // Insert / dedup providers, build (key → new id) map.
        val newIds = mutableMapOf<String, Long>()
        for (p in bundle.providers) {
            val existing = providerDao.findByIdentity(p.kind, p.baseUrl, p.username)
            val id = existing?.id ?: providerDao.upsert(
                ProviderEntity(
                    name = p.name, kind = p.kind, baseUrl = p.baseUrl,
                    username = p.username, password = p.password, active = p.active,
                )
            )
            newIds["${p.kind}|${p.baseUrl}|${p.username}"] = id
        }

        var favs = 0
        for (f in bundle.favorites) {
            val pid = newIds[f.providerKey]
            if (pid == null) continue
            favoriteDao.add(FavoriteEntity(providerId = pid, kind = f.kind, remoteId = f.remoteId))
            favs++
        }
        var hist = 0
        for (h in bundle.history) {
            val pid = newIds[h.providerKey]
            if (pid == null) continue
            historyDao.upsert(
                WatchHistoryEntity(
                    providerId = pid, kind = h.kind, remoteId = h.remoteId,
                    title = h.title, poster = h.poster, streamUrl = h.streamUrl,
                    positionMs = h.positionMs, durationMs = h.durationMs,
                    watchedAt = h.watchedAt, parentRemoteId = h.parentRemoteId,
                )
            )
            hist++
        }
        return RestoreResult(
            providers = bundle.providers.size,
            favorites = favs,
            historyEntries = hist,
        )
    }

    data class RestoreResult(val providers: Int, val favorites: Int, val historyEntries: Int)
}

package com.ultratv.tv.nativeapp.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Clé de tri stockée (et indexée) : minuscules sans les décorations de tête (« ## », « ★ »…).
 * Permet un ORDER BY servi par l'index (pagination instantanée sur 180 000 lignes) au lieu
 * d'un tri complet par page.
 */
fun sortKeyOf(name: String): String {
    val trimmed = name.trimStart { !it.isLetterOrDigit() }.ifEmpty { name }
    return trimmed.lowercase(java.util.Locale.ROOT)
}

@Entity(tableName = "provider")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,           // "XTREAM" for Phase 1
    val baseUrl: String,        // e.g. http://provider.com:8080
    val username: String,
    val password: String,
    val active: Boolean = true,
    /** Dernière synchro réussie de chaque partie du catalogue (0 = jamais) : TTL incrémental. */
    val lastLiveSyncAt: Long = 0,
    val lastVodSyncAt: Long = 0,
    val lastSeriesSyncAt: Long = 0,
    val lastEpgSyncAt: Long = 0,
)

@Entity(
    tableName = "channel",
    indices = [
        Index(value = ["providerId", "sortKey"]),
        Index(value = ["providerId", "categoryId", "sortKey"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val remoteId: String,       // stream_id from Xtream
    val name: String,
    val logo: String?,
    val categoryId: String?,
    val streamUrl: String,
    /** XMLTV channel id (`tvg-id` for M3U, `epg_channel_id` for Xtream). Used
     *  to match programmes loaded from a full xmltv feed. */
    val epgChannelId: String? = null,
    /** When non-null, this channel supports catch-up replay. The string is the
     *  M3U `catchup-source` URL template (placeholders: `${start}`, `${end}`,
     *  `${duration}`, `${timestamp}`, `${utc}`, `{Y}/{m}/{d}/{H}/{M}/{S}`).
     *  For Xtream providers we synthesise a template using the `tv_archive`
     *  flag + the standard `…/streaming/timeshift.php` endpoint. */
    val catchupSource: String? = null,
    /** Days of catch-up window the provider exposes (Xtream tv_archive_duration). */
    val catchupDays: Int = 0,
    /** User-defined display position. 0 = natural order (sort by name). >0
     *  pushes the channel to that absolute slot in the Live list, allowing
     *  the favourites + frequently-watched to bubble to the top. */
    val userPosition: Int = 0,
    /** Nom d'affichage nettoyé (préfixes de langue, qualité…) ; [name] reste le nom brut, base de la recherche. */
    val title: String = com.ultratv.tv.nativeapp.data.repo.TitleCleaner.clean(name, live = true).title,
    val sortKey: String = sortKeyOf(title),
)

@Entity(tableName = "category", indices = [Index(value = ["providerId", "kind", "remoteId"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val kind: String,           // "LIVE", "MOVIE", "SERIES"
    val remoteId: String,
    val name: String,
    val locked: Boolean = false,
)

@Entity(
    tableName = "movie",
    indices = [
        Index(value = ["providerId", "sortKey"]),
        Index(value = ["providerId", "categoryId", "sortKey"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val remoteId: String,
    val name: String,
    val poster: String?,
    val categoryId: String?,
    val streamUrl: String,
    val container: String?,
    val year: Int?,
    val rating: Double?,
    val plot: String?,
    val title: String = com.ultratv.tv.nativeapp.data.repo.TitleCleaner.clean(name).title,
    val sortKey: String = sortKeyOf(title),
    /** Image PAYSAGE (hero, fiche) : seulement si la source en fournit une (get_vod_info). */
    val backdrop: String? = null,
    val genre: String? = null,
    val cast: String? = null,
    val duration: String? = null,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["providerId", "sortKey"]),
        Index(value = ["providerId", "categoryId", "sortKey"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val remoteId: String,
    val name: String,
    val poster: String?,
    val categoryId: String?,
    val year: Int?,
    val rating: Double?,
    val plot: String?,
    val title: String = com.ultratv.tv.nativeapp.data.repo.TitleCleaner.clean(name).title,
    val sortKey: String = sortKeyOf(title),
    val backdrop: String? = null,
    val genre: String? = null,
    val cast: String? = null,
)

@Entity(
    tableName = "episode",
    indices = [Index("seriesId"), Index(value = ["seriesId", "remoteId"], unique = true)],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val remoteId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val streamUrl: String,
    val container: String?,
    val plot: String?,
    val image: String? = null,
)

@Entity(
    tableName = "favorite",
    primaryKeys = ["providerId", "kind", "remoteId"],
)
data class FavoriteEntity(
    val providerId: Long,
    val kind: String,           // "LIVE", "MOVIE", "SERIES"
    val remoteId: String,
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["providerId", "kind", "remoteId"],
)
data class WatchHistoryEntity(
    val providerId: Long,
    val kind: String,           // "LIVE", "MOVIE", "EPISODE"
    val remoteId: String,
    val title: String,
    val poster: String?,
    val streamUrl: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watchedAt: Long = System.currentTimeMillis(),
    // For episodes: parent series id so we can group "continue watching this show".
    val parentRemoteId: String? = null,
)

@Entity(tableName = "recording")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val kind: String,           // "MOVIE" | "EPISODE"
    val remoteId: String,
    val title: String,
    val sourceUrl: String,
    val filePath: String,       // absolute path under context.getExternalFilesDir
    val status: String,         // "queued" | "running" | "done" | "failed" (retryable) | "error" (terminal) | "cancelled"
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
)

@Entity(
    tableName = "epg",
    indices = [Index("channelId"), Index(value = ["channelId", "startMs"])],
)
data class EpgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val title: String,
    val description: String?,
    val startMs: Long,
    val endMs: Long,
)

/** Index plein texte (FTS4, unicode61 : accents, arabe, cyrillique) — recherche instantanée. */
@Fts4(contentEntity = ChannelEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "channel_fts")
data class ChannelFts(val name: String)

@Fts4(contentEntity = MovieEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "movie_fts")
data class MovieFts(val name: String)

@Fts4(contentEntity = SeriesEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "series_fts")
data class SeriesFts(val name: String)

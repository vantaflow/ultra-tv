package com.ultratv.tv.nativeapp.data.downloads

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Un film ou un épisode téléchargé (ou en cours) pour la lecture hors ligne.
 *
 * L'URL du flux n'est JAMAIS stockée ici (elle contient les identifiants de la source) : le travailleur la relit dans
 * le catalogue au moment de télécharger ; seule son empreinte [urlHash] est gardée pour savoir si un fichier partiel
 * correspond encore au même flux. Le nom du fichier ne contient ni identifiant ni URL (voir [DownloadPlan.fileName]).
 *
 * Pas de clé étrangère vers `provider` : ProviderRawDao.upsert est un REPLACE (supprime puis réinsère) et une cascade
 * effacerait les téléchargements à chaque modification d'une source. La suppression d'une source est suivie par
 * [DownloadRepository.purgeOrphans] (lignes ET fichiers).
 */
@Entity(
    tableName = "download",
    indices = [
        Index(value = ["providerId", "kind", "remoteId"], unique = true),
        Index(value = ["state"]),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    /** [DownloadKind.MOVIE] ou [DownloadKind.EPISODE]. */
    val kind: String,
    val remoteId: String,
    /** Épisode : identifiant distant de la série ; film : null. */
    val parentRemoteId: String? = null,
    val title: String,
    val poster: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val urlHash: String,
    /** Chemin absolu du fichier FINAL (le partiel est `filePath + ".part"`), dans le stockage propre à l'appli. */
    val filePath: String,
    val totalBytes: Long = 0,
    val doneBytes: Long = 0,
    /** Voir [DownloadState]. */
    val state: String = DownloadState.QUEUED,
    /** Code d'erreur court (voir [DownloadError]) — jamais un message brut, qui pourrait contenir l'URL. */
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object DownloadKind {
    const val MOVIE = "MOVIE"
    const val EPISODE = "EPISODE"
}

/** Codes d'erreur affichés (traduits) par l'écran Téléchargements. */
object DownloadError {
    const val NETWORK = "network"
    const val NO_SPACE = "no_space"
    const val HTTP = "http"
    const val NOT_DOWNLOADABLE = "not_downloadable"
    const val SOURCE_MISSING = "source_missing"
    const val STORAGE = "storage"
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE state = 'COMPLETED'")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE providerId = :pid AND kind = :kind AND remoteId = :rid LIMIT 1")
    fun observeItem(pid: Long, kind: String, rid: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM download WHERE providerId = :pid AND kind = 'EPISODE' AND parentRemoteId = :seriesRid")
    fun observeForSeries(pid: Long, seriesRid: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM download WHERE id = :id")
    suspend fun byId(id: Long): DownloadEntity?

    @Query("SELECT * FROM download WHERE providerId = :pid AND kind = :kind AND remoteId = :rid LIMIT 1")
    suspend fun byItem(pid: Long, kind: String, rid: String): DownloadEntity?

    /** File d'attente : les plus anciens d'abord ; [exclude] = ceux déjà pris par le travailleur. */
    @Query("SELECT * FROM download WHERE state = 'QUEUED' AND id NOT IN (:exclude) ORDER BY createdAt, id LIMIT :limit")
    suspend fun nextQueued(exclude: List<Long>, limit: Int): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM download WHERE state IN ('QUEUED', 'RUNNING')")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM download WHERE state = 'RUNNING'")
    fun observeRunningCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(d: DownloadEntity): Long

    @Query("UPDATE download SET state = :state, error = :error WHERE id = :id")
    suspend fun setState(id: Long, state: String, error: String?)

    @Query("UPDATE download SET doneBytes = :done, totalBytes = :total WHERE id = :id")
    suspend fun setProgress(id: Long, done: Long, total: Long)

    @Query("UPDATE download SET urlHash = :hash WHERE id = :id")
    suspend fun setUrlHash(id: Long, hash: String)

    /** Reprise après un arrêt brutal (processus tué) : rien ne tourne plus, tout ce qui tournait repart en file. */
    @Query("UPDATE download SET state = 'QUEUED' WHERE state = 'RUNNING'")
    suspend fun requeueRunning()

    @Query("DELETE FROM download WHERE id = :id")
    suspend fun delete(id: Long)
}

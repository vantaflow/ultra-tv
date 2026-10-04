package com.ultratv.tv.nativeapp.data.recording

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ultratv.tv.nativeapp.data.db.RecordingDao
import com.ultratv.tv.nativeapp.data.db.RecordingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

fun workName(recordingId: Long) = "rec-$recordingId"

@Singleton
class RecordingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: RecordingDao,
) {
    fun observeAll(): Flow<List<RecordingEntity>> = dao.observeAll()

    /**
     * Queues a recording: writes the row, enqueues the worker. Caller passes the
     * already-resolved URL (Xtream a déjà résolu l'URL). File goes
     * under app-private external storage so no permission is required.
     */
    suspend fun enqueue(
        providerId: Long, kind: String, remoteId: String, title: String, url: String,
        maxDurationMinutes: Int = 120,   // applies to HLS Live recordings; ignored for VOD
    ): Long {
        val safeTitle = title.replace(Regex("[^A-Za-z0-9_.\\- ]"), "_").take(80)
        // For HLS we always write a .ts (concatenated raw segments); for everything
        // else we trust the URL extension (mp4 fallback).
        val ext = if (url.contains(".m3u8", ignoreCase = true)) "ts"
        else url.substringAfterLast('.', "mp4")
            .substringBefore('?').lowercase()
            .takeIf { it.length in 2..5 } ?: "mp4"
        val dir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val file = File(dir, "${safeTitle}-${System.currentTimeMillis()}.$ext")

        val id = dao.upsert(
            RecordingEntity(
                providerId = providerId, kind = kind, remoteId = remoteId,
                title = title, sourceUrl = url, filePath = file.absolutePath,
                status = "queued",
            ),
        )

        enqueueWork(id, maxDurationMinutes * 60L * 1000)
        return id
    }

    /** Une seule exécution par enregistrement (nom unique) : permet aussi de l'arrêter proprement. */
    internal fun enqueueWork(id: Long, maxDurationMs: Long) {
        val req = OneTimeWorkRequestBuilder<RecordingWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(RecordingWorker.KEY_RECORDING_ID, id)
                    .putLong(RecordingWorker.KEY_MAX_DURATION_MS, maxDurationMs)
                    .build(),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, req)
    }

    /** Chemin du fichier d'un futur enregistrement (même convention que [enqueue]). */
    fun newFilePath(title: String, url: String): String {
        val safeTitle = title.replace(Regex("[^A-Za-z0-9_.\\- ]"), "_").take(80)
        val ext = if (url.contains(".m3u8", ignoreCase = true)) "ts"
        else url.substringAfterLast('.', "ts").substringBefore('?').lowercase().takeIf { it.length in 2..5 } ?: "ts"
        val dir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
        return File(dir, "$safeTitle-${System.currentTimeMillis()}.$ext").absolutePath
    }

    /** Octets libres sur le volume des enregistrements. */
    fun freeBytes(): Long = runCatching {
        android.os.StatFs((context.getExternalFilesDir(null) ?: context.filesDir).path).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    /**
     * Arrête un enregistrement en cours : le travailleur voit l'annulation, conserve ce qui est déjà
     * écrit et passe la ligne en « cancelled ». Une ligne encore en file est simplement marquée.
     */
    suspend fun stop(id: Long) {
        val r = dao.byId(id) ?: return
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        if (r.status == "queued") dao.setStatus(id, "cancelled", null)
    }

    suspend fun delete(id: Long) {
        val r = dao.byId(id) ?: return
        runCatching { File(r.filePath).delete() }
        dao.delete(id)
    }
}

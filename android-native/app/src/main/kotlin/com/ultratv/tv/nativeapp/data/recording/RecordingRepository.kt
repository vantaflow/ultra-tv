package com.ultratv.tv.nativeapp.data.recording

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
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

@Singleton
class RecordingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: RecordingDao,
) {
    fun observeAll(): Flow<List<RecordingEntity>> = dao.observeAll()

    /**
     * Queues a recording: writes the row, enqueues the worker. Caller passes the
     * already-resolved URL (Stalker/Xtream have done their thing). File goes
     * under app-private external storage so no permission is required.
     */
    suspend fun enqueue(providerId: Long, kind: String, remoteId: String, title: String, url: String): Long {
        val safeTitle = title.replace(Regex("[^A-Za-z0-9_.\\- ]"), "_").take(80)
        val ext = url.substringAfterLast('.', "mp4")
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

        val req = OneTimeWorkRequestBuilder<RecordingWorker>()
            .setInputData(Data.Builder().putLong(RecordingWorker.KEY_RECORDING_ID, id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(req)
        return id
    }

    suspend fun delete(id: Long) {
        val r = dao.byId(id) ?: return
        runCatching { File(r.filePath).delete() }
        dao.delete(id)
    }
}

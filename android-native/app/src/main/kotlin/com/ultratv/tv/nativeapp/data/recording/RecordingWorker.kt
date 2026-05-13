package com.ultratv.tv.nativeapp.data.recording

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ultratv.tv.nativeapp.data.db.RecordingDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads a single VOD URL to local app-private storage. Intended for
 * movies and series episodes only — HLS live streams are intentionally
 * out of scope here (would require segment-by-segment recording, much
 * more code).
 *
 * Run via WorkManager so it survives the user leaving the app. Progress
 * is mirrored into [RecordingEntity] so the UI can render a percent bar.
 */
@HiltWorker
class RecordingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ok: OkHttpClient,
    private val dao: RecordingDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_RECORDING_ID, -1L)
        if (id < 0) return Result.failure()
        val r = dao.byId(id) ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            val file = File(r.filePath).apply { parentFile?.mkdirs() }
            try {
                dao.updateProgress(id, "running", 0, 0, null, null)
                val resp = ok.newCall(Request.Builder().url(r.sourceUrl).build()).execute()
                if (!resp.isSuccessful) {
                    dao.updateProgress(id, "failed", 0, 0, "HTTP ${resp.code}", System.currentTimeMillis())
                    return@withContext Result.failure()
                }
                val total = resp.body?.contentLength() ?: -1L
                var down = 0L
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var n: Int
                        var lastPct = -1L
                        while (input.read(buf).also { n = it } > 0) {
                            if (isStopped) {
                                dao.updateProgress(id, "cancelled", down, total, "Cancelled", System.currentTimeMillis())
                                return@withContext Result.failure()
                            }
                            out.write(buf, 0, n)
                            down += n
                            // Throttle DB writes — only when we cross a whole percent.
                            val pct = if (total > 0) (down * 100 / total) else -1
                            if (pct != lastPct) {
                                dao.updateProgress(id, "running", down, total, null, null)
                                lastPct = pct
                            }
                        }
                    }
                }
                dao.updateProgress(id, "done", down, total, null, System.currentTimeMillis())
                Result.success()
            } catch (t: Throwable) {
                dao.updateProgress(id, "failed", 0, 0, t.message, System.currentTimeMillis())
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
    }
}

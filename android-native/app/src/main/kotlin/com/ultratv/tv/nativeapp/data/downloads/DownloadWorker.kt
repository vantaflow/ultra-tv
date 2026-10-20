package com.ultratv.tv.nativeapp.data.downloads

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ultratv.tv.nativeapp.ui.downloads.DownloadStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Exécute la file des téléchargements : UN seul travail unique (voir [DownloadRepository.kick]) qui prend au plus
 * [MAX_PARALLEL] fichiers à la fois, jusqu'à épuisement de la file. Service de premier plan « dataSync » (notification
 * avec progression) : un film de plusieurs Go ne doit pas être tué par le système.
 *
 * Arrêt par WorkManager (réseau perdu, Wi-Fi exigé, quota) : les lignes en cours repassent en file, leurs `.part` sont
 * gardés et repris par plage au prochain passage. Échec passager après les tentatives internes : la ligne repasse en
 * file et le travail est relancé plus tard (attente exponentielle de WorkManager) ; au-delà de [MAX_RUNS] passages, échec.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ok: OkHttpClient,
    private val dao: DownloadDao,
    private val repo: DownloadRepository,
    private val userPrefs: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
) : CoroutineWorker(appContext, params) {

    private val progress = ConcurrentHashMap<Long, Pair<Long, Long>>()
    private lateinit var strings: DownloadStrings

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(stringsOrDefault(), null)

    override suspend fun doWork(): Result {
        strings = runCatching { DownloadStrings(com.ultratv.tv.nativeapp.i18n.AppLang.fromCode(userPrefs.flow.first().language)) }
            .getOrElse { DownloadStrings(com.ultratv.tv.nativeapp.i18n.AppLang.System) }
        // Un seul travail à la fois (travail unique) : ce qui était « en cours » vient d'un arrêt brutal.
        dao.requeueRunning()
        if (dao.pendingCount() == 0) return Result.success()
        runCatching { setForeground(foregroundInfo(strings, null)) }

        // Client dédié : pas de délai global d'appel (un film prend des minutes), pas de mesure de débit du lecteur.
        val client = ok.newBuilder()
            .apply { networkInterceptors().clear() }
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val downloader = HttpRangeDownloader(client, freeBytes = { repo.freeBytes(it) })
        var transient = false
        try {
            coroutineScope {
                val taken = mutableSetOf<Long>()
                val jobs = mutableListOf<Job>()
                val notifier = launch {
                    while (isActive) { delay(2_000); runCatching { setForeground(foregroundInfo(strings, snapshot())) } }
                }
                while (true) {
                    jobs.removeAll { !it.isActive }
                    val slots = MAX_PARALLEL - jobs.size
                    val batch = if (slots > 0) dao.nextQueued(taken.toList().ifEmpty { listOf(-1L) }, slots) else emptyList()
                    for (d in batch) {
                        taken += d.id
                        jobs += launch { if (runOne(downloader, d)) transient = true }
                    }
                    if (jobs.isEmpty() && batch.isEmpty()) break
                    delay(1_000)
                }
                notifier.cancel()
            }
        } finally {
            // Arrêt par WorkManager (contrainte perdue) : rien ne reste « en cours ».
            withContext(NonCancellable) { dao.requeueRunning() }
        }
        if (transient) {
            if (runAttemptCount + 1 >= MAX_RUNS) {
                // Trop de passages en échec : on arrête d'insister ; l'utilisateur relance avec « Réessayer ».
                dao.all().filter { it.state == DownloadState.QUEUED && it.error == DownloadError.NETWORK }
                    .forEach { dao.setState(it.id, DownloadState.FAILED, DownloadError.NETWORK) }
                return Result.failure()
            }
            return Result.retry()
        }
        return Result.success()
    }

    /** Un fichier. Renvoie true si l'échec est passager (relancer le travail plus tard). */
    private suspend fun runOne(downloader: HttpRangeDownloader, queued: DownloadEntity): Boolean {
        val d = dao.byId(queued.id) ?: return false
        val start = DownloadState.next(d.state, DownloadState.Event.START) ?: return false
        dao.setState(d.id, start, null)
        val catalogUrl = repo.currentUrl(d)
        if (catalogUrl.isNullOrBlank()) { fail(d.id, DownloadError.SOURCE_MISSING); return false }
        // Portail Stalker : lien temporaire demandé maintenant (sans effet sur une URL Xtream / M3U).
        val url = com.ultratv.tv.nativeapp.data.stalker.StreamUrls.resolve(applicationContext, catalogUrl)
        if (com.ultratv.tv.nativeapp.data.stalker.StalkerLink.isStalker(url) || !DownloadPlan.isDownloadable(url)) {
            fail(d.id, DownloadError.NOT_DOWNLOADABLE); return false
        }
        val file = File(d.filePath)
        // Flux changé (identifiants modifiés, autre conteneur) : le partiel ne correspond plus forcément, on repart de zéro.
        // Empreinte de l'URL du CATALOGUE (stable), pas du lien temporaire d'un portail.
        val hash = DownloadPlan.sha256(catalogUrl)
        if (hash != d.urlHash) { runCatching { DownloadPlan.partFile(file).delete() }; dao.setUrlHash(d.id, hash) }
        progress[d.id] = d.doneBytes to d.totalBytes
        val outcome = try {
            downloader.download(
                url, file,
                onProgress = { done, total -> progress[d.id] = done to total; dao.setProgress(d.id, done, total) },
                shouldStop = { dao.byId(d.id)?.state != DownloadState.RUNNING },
            )
        } finally {
            progress.remove(d.id)
        }
        val now = dao.byId(d.id)
        return when (outcome) {
            is HttpRangeDownloader.Outcome.Completed -> {
                if (now == null) { DownloadRepository.deleteFiles(d.filePath); return false }
                dao.setProgress(d.id, outcome.bytes, outcome.bytes)
                DownloadState.next(now.state, DownloadState.Event.COMPLETE)?.let { dao.setState(d.id, it, null) }
                false
            }
            // Pause : le partiel reste. Annulation (ligne supprimée) : le partiel a pu être recréé entre-temps.
            HttpRangeDownloader.Outcome.Stopped -> { if (now == null) DownloadRepository.deleteFiles(d.filePath); false }
            is HttpRangeDownloader.Outcome.Failed -> {
                if (now == null) { DownloadRepository.deleteFiles(d.filePath); return false }
                if (outcome.retryable) {
                    DownloadState.next(now.state, DownloadState.Event.INTERRUPT)?.let { dao.setState(d.id, it, DownloadError.NETWORK) }
                    true
                } else {
                    fail(d.id, outcome.error); false
                }
            }
        }
    }

    private suspend fun fail(id: Long, code: String) {
        val cur = dao.byId(id) ?: return
        DownloadState.next(cur.state, DownloadState.Event.FAIL)?.let { dao.setState(id, it, code) }
    }

    /** (nombre en cours, pourcentage global ou -1 si tailles inconnues). */
    private fun snapshot(): Pair<Int, Int> {
        val items = progress.values.toList()
        val total = items.sumOf { it.second.coerceAtLeast(0) }
        val done = items.sumOf { it.first }
        val pct = if (items.isNotEmpty() && items.all { it.second > 0 } && total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
        return items.size to pct
    }

    private fun stringsOrDefault() = if (::strings.isInitialized) strings else DownloadStrings(com.ultratv.tv.nativeapp.i18n.AppLang.System)

    private fun foregroundInfo(s: DownloadStrings, state: Pair<Int, Int>?): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL_ID, s.notifChannel, android.app.NotificationManager.IMPORTANCE_LOW))
        }
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            android.app.PendingIntent.getActivity(ctx, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val (count, pct) = state ?: (0 to -1)
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(s.notifChannel)
            .setContentText(if (count == 0) s.notifStarting else s.notifProgress(count, pct.coerceAtLeast(0)))
            .setProgress(100, pct.coerceAtLeast(0), count == 0 || pct < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        const val WORK_NAME = "downloads-queue"
        const val TAG = "downloads"
        const val MAX_PARALLEL = 2
        const val MAX_RUNS = 6
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 4103
    }
}

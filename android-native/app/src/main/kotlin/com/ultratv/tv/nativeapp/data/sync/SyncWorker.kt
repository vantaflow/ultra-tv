package com.ultratv.tv.nativeapp.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ultratv.tv.nativeapp.data.net.NetErrors
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchro du catalogue en arrière-plan (WorkManager) :
 *   - `providerId >= 0` : une source (déclenchée à l'ajout, au lancement ou par « Actualiser ») ;
 *   - `providerId = -1` : toutes les sources (travail périodique).
 * Elle est promue en service de premier plan (dataSync) : le catalogue pèse des dizaines de Mo
 * et prend des minutes sur une box d'entrée de gamme, le système ne doit pas tuer le processus.
 * La progression est publiée sur le SyncStatusBus (bannière « Synchronisation… »).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val providerRepo: ProviderRepository,
    private val prefs: UserPreferencesStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(appContext)

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_PROVIDER, ALL)
        val force = inputData.getBoolean(KEY_FORCE, false)
        val catKind = inputData.getString(KEY_CAT_KIND)
        // Foreground best-effort : sur certaines box le démarrage d'un service de premier plan
        // depuis l'arrière-plan est refusé ; la synchro continue alors en tâche normale.
        runCatching { setForeground(foregroundInfo(appContext)) }
        return try {
            if (catKind != null && id >= 0) {
                providerRepo.syncCategoriesNow(id, catKind, inputData.getString(KEY_CAT_IDS).orEmpty().split('\u0001').filter { it.isNotEmpty() })
                return Result.success()
            }
            val ids = if (id >= 0) listOf(id) else providerRepo.observeProviders().first().map { it.id }
            var failure: Throwable? = null
            ids.forEach { pid -> runCatching { providerRepo.syncAll(pid, force = force) }.onFailure { failure = it } }
            prefs.setLastSyncAt(System.currentTimeMillis())
            val f = failure
            when {
                f == null -> Result.success()
                // Mauvais identifiants / serveur introuvable : réessayer ne sert à rien, la bannière
                // « Corriger la source » est déjà affichée.
                NetErrors.classify(f).let {
                    it == SyncErrorKind.UNAUTHORIZED || it == SyncErrorKind.HOST_NOT_FOUND || it == SyncErrorKind.NOT_FOUND ||
                        it == SyncErrorKind.PROVIDER_BLOCKED || it == SyncErrorKind.TLS
                } -> Result.failure()
                runAttemptCount < 3 -> Result.retry()
                else -> Result.failure()
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_PROVIDER = "providerId"
        const val KEY_FORCE = "force"
        const val KEY_CAT_KIND = "catKind"
        const val KEY_CAT_IDS = "catIds"
        const val ALL = -1L
        private const val CHANNEL_ID = "sync"
        private const val NOTIF_ID = 4101

        private fun foregroundInfo(ctx: Context): ForegroundInfo {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, ctx.getString(com.ultratv.tv.nativeapp.R.string.sync_channel), NotificationManager.IMPORTANCE_LOW),
                )
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(ctx.getString(com.ultratv.tv.nativeapp.R.string.sync_notification))
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else ForegroundInfo(NOTIF_ID, n)
        }
    }
}

/** Point d'entrée UNIQUE pour lancer une synchro : jamais depuis un ViewModel, toujours via WorkManager. */
@Singleton
class SyncCoordinator @Inject constructor(@ApplicationContext private val ctx: Context) {
    /**
     * Met en file la synchro d'une source. Un seul travail par source (KEEP) : relancer pendant
     * qu'elle tourne ne duplique rien. [force] ignore les TTL (« Actualiser »).
     */
    fun request(providerId: Long, force: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_PROVIDER to providerId, SyncWorker.KEY_FORCE to force))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("sync-$providerId", ExistingWorkPolicy.KEEP, req)
    }

    /** Télécharge seulement ces catégories (réactivation) : requêtes ciblées si le serveur filtre. */
    fun requestCategories(providerId: Long, kind: String, ids: List<String>) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_PROVIDER to providerId, SyncWorker.KEY_CAT_KIND to kind, SyncWorker.KEY_CAT_IDS to ids.joinToString("\u0001")))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }

    /** Une source de plus à synchroniser maintenant, TTL ignorés (première synchro). */
    fun requestAll(force: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_PROVIDER to SyncWorker.ALL, SyncWorker.KEY_FORCE to force))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("sync-all", ExistingWorkPolicy.KEEP, req)
    }
}

object SyncScheduler {
    private const val DAILY = "ultratv-daily-sync"

    /** Synchro quotidienne à [hour] h (réseau, batterie non faible, appareil au repos ; non facturé si demandé). */
    /**
     * Configuration déjà appliquée (par nom de travail) : au lancement, rien n'est réécrit si elle n'a pas changé.
     * Avant, chaque démarrage remplaçait les travaux (UPDATE), ce qui écrivait dans la base de WorkManager et
     * repoussait la fenêtre de déclenchement de la synchro quotidienne.
     */
    private fun alreadyApplied(context: Context, name: String, sig: String): Boolean {
        val sp = context.getSharedPreferences("sync_schedule", Context.MODE_PRIVATE)
        if (sp.getString(name, null) == sig) return true
        sp.edit().putString(name, sig).apply()
        return false
    }

    fun scheduleDaily(context: Context, hour: Int, unmeteredOnly: Boolean) {
        if (alreadyApplied(context, DAILY, "daily:$hour:$unmeteredOnly")) return
        val now = java.util.Calendar.getInstance()
        val next = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
            if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val delay = next.timeInMillis - now.timeInMillis
        val req = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true).setRequiresDeviceIdle(false).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private const val UNIQUE_NAME = "ultratv-bg-sync"

    /** (Re-)schedules background sync. Pass 0 to cancel. */
    fun schedule(context: Context, intervalHours: Int) {
        if (alreadyApplied(context, UNIQUE_NAME, "every:${intervalHours.coerceAtLeast(0)}")) return
        val wm = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        // PeriodicWorkRequest minimum is 15 minutes; we use the user's
        // interval directly. flex = 25% of interval lets the system pick the
        // exact firing time within that window to batch with other work.
        val req = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS,
            (intervalHours / 4).coerceAtLeast(1).toLong(), TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

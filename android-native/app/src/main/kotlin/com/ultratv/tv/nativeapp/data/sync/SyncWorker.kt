package com.ultratv.tv.nativeapp.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes provider catalogs in the background. Scheduled by
 * [SyncScheduler.schedule] based on the user's `syncIntervalHours` preference;
 * cancelled when the user picks "Every launch" (interval = 0).
 *
 * The worker:
 *   - Only runs on a network. Wi-Fi-or-cellular by default; we don't gate on
 *     Wi-Fi since some users only have cellular on their TV box.
 *   - Calls `providerRepo.syncAll(p.id)` for every configured provider in turn.
 *   - Records the timestamp into prefs so the in-app "auto-sync on launch"
 *     check skips when WorkManager has already refreshed recently.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val providerRepo: ProviderRepository,
    private val prefs: UserPreferencesStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val providers = providerRepo.observeProviders().first()
            providers.forEach { p -> runCatching { providerRepo.syncAll(p.id) } }
            prefs.setLastSyncAt(System.currentTimeMillis())
            Result.success()
        } catch (t: Throwable) {
            // Retry on transient failures (network, server timeout); WorkManager
            // applies exponential backoff up to its limit.
            Result.retry()
        }
    }
}

object SyncScheduler {
    private const val UNIQUE_NAME = "ultratv-bg-sync"

    /** (Re-)schedules background sync. Pass 0 to cancel. */
    fun schedule(context: Context, intervalHours: Int) {
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

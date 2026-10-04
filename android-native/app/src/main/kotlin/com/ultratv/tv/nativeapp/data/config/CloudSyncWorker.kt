package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Synchro cloud périodique (toutes les 6 h, réseau requis). Les retraits qui demandent confirmation restent en attente. */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val manager: CloudSyncManager,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!manager.isPaired) return Result.success()
        return when (manager.sync()) {
            CloudSyncManager.Result.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE = "cloud-sync-6h"
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

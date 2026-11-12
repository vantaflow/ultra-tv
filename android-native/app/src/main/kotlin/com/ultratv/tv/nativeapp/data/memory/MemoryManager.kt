package com.ultratv.tv.nativeapp.data.memory

import android.content.Context
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.downloads.DownloadPrefs
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** RAM de l'appareil (octets). */
data class RamState(val total: Long, val available: Long, val lowRamFlag: Boolean) {
    val limited: Boolean get() = RamClass.isLimited(total, lowRamFlag)
}

/** Résultat d'une action : octets libérés, ou refus (synchro en cours, tampon utilisé, pas assez de place). */
sealed interface ActionResult {
    data class Done(val freed: Long, val count: Int = 0) : ActionResult
    data object BlockedBySync : ActionResult
    data object TimeshiftInUse : ActionResult
    data object NoSpace : ActionResult
    data object Failed : ActionResult
}

/**
 * Calculs et actions de « Mémoire et stockage ». Tout le travail disque tourne sur [Dispatchers.IO] ; rien sur le fil
 * principal. Les actions qui toucheraient des fichiers ou la base utilisés par une synchro sont refusées pendant celle-ci.
 */
@Singleton
class MemoryManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: UltraDb,
    private val profiles: ProfileRepository,
    private val syncStatus: SyncStatusBus,
    private val downloadPrefs: DownloadPrefs,
    private val prefs: MemoryPrefs,
    private val adaptive: com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile,
) {
    private val dbFile: File get() = ctx.getDatabasePath(DB_NAME)

    /** Synchro du catalogue en cours (bannière « Synchronisation… » publiée) : ni VACUUM ni purge de ses fichiers. */
    fun syncRunning(): Boolean = syncStatus.status.value.let { it != null && it.step != "Done" }

    fun ram(): RamState = ctx.ramState()

    /** Appareil à mémoire limitée : profil adaptatif « LOW » ou RAM < 2 Go / drapeau système. */
    fun limitedDevice(): Boolean = adaptive.state.value.auto.lowRam || ram().limited

    fun freeBytes(): Long = runCatching { StatFs(ctx.filesDir.path).availableBytes }.getOrDefault(0L)

    /** Limite effective du cache d'images, selon le réglage (Auto → espace libre). */
    fun imageCacheLimitBytes(): Long = ImageCacheLimit.bytes(prefs.state.value.imageCache, freeBytes(), limitedDevice())

    private fun imageDir() = File(ctx.cacheDir, IMAGE_DIR)
    private fun timeshiftDir() = File(ctx.cacheDir, TimeshiftRegistry.DIR_NAME)
    private fun downloadDirs() = listOfNotNull(
        ctx.getExternalFilesDir(null)?.let { File(it, DownloadPrefs.DIR_NAME) },
        File(ctx.filesDir, DownloadPrefs.DIR_NAME),
    )
    private fun logFiles() = listOfNotNull(
        ctx.getExternalFilesDir(null)?.resolve("crash.txt"), File(ctx.filesDir, "crash.txt"),
    )

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val base = dbFile.path
        StorageUsage(
            database = listOf(base, "$base-wal", "$base-shm", "$base-journal").sumOf { StorageMath.sizeOf(File(it)) },
            images = StorageMath.sizeOf(imageDir()),
            downloads = downloadDirs().distinct().sumOf { StorageMath.sizeOf(it) },
            timeshift = StorageMath.sizeOf(timeshiftDir()),
            temp = TempFiles.size(ctx.cacheDir),
            logs = logFiles().sumOf { StorageMath.sizeOf(it) },
            free = freeBytes(),
        )
    }

    suspend fun clearImageCache(): ActionResult = withContext(Dispatchers.IO) {
        runCatching {
            val before = StorageMath.sizeOf(imageDir())
            clearCoilDisk()
            ActionResult.Done((before - StorageMath.sizeOf(imageDir())).coerceAtLeast(0L))
        }.getOrDefault(ActionResult.Failed)
    }

    /** Vide le cache disque ET mémoire de Coil par son API (le dossier appartient à son journal). */
    private fun clearCoilDisk() {
        val loader = coil3.SingletonImageLoader.get(ctx)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    suspend fun purgeTemp(): ActionResult = withContext(Dispatchers.IO) {
        if (syncRunning()) return@withContext ActionResult.BlockedBySync
        runCatching {
            val freed = TempFiles.purge(ctx.cacheDir, System.currentTimeMillis())
            ActionResult.Done(freed)
        }.getOrDefault(ActionResult.Failed)
    }

    suspend fun clearTimeshift(): ActionResult = withContext(Dispatchers.IO) {
        if (TimeshiftRegistry.active) return@withContext ActionResult.TimeshiftInUse
        runCatching { ActionResult.Done(StorageMath.clearDirectory(timeshiftDir())) }.getOrDefault(ActionResult.Failed)
    }

    /** Supprime les tampons de pause du direct orphelins (appli tuée, plantage) ; sans effet si un relais est actif. */
    suspend fun purgeTimeshiftOrphans(): Long = withContext(Dispatchers.IO) {
        if (TimeshiftRegistry.active) return@withContext 0L
        val files = timeshiftDir().listFiles()?.filter { it.isFile }.orEmpty()
        val names = TimeshiftOrphans.toDelete(files.map { TimeshiftOrphans.Entry(it.name, it.lastModified()) }, emptySet(), System.currentTimeMillis())
        var freed = 0L
        for (f in files.filter { it.name in names }) {
            val len = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) freed += len
        }
        freed
    }

    /** VACUUM : refusé pendant une synchro, et sans la place de la copie (≈ taille de la base). */
    suspend fun compactDatabase(): ActionResult = withContext(Dispatchers.IO) {
        if (syncRunning()) return@withContext ActionResult.BlockedBySync
        val base = dbFile.path
        fun size() = listOf(base, "$base-wal", "$base-shm").sumOf { StorageMath.sizeOf(File(it)) }
        val before = size()
        if (freeBytes() < before + before / 5 + 16L * ImageCacheLimit.MB) return@withContext ActionResult.NoSpace
        runCatching {
            val sdb = db.openHelper.writableDatabase
            sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            sdb.execSQL("VACUUM")
            sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            ActionResult.Done((before - size()).coerceAtLeast(0L))
        }.getOrDefault(ActionResult.Failed)
    }

    /** Historique (lignes supprimées). [allProfiles] : tous les profils, sinon le profil courant. */
    suspend fun clearHistory(allProfiles: Boolean): ActionResult = withContext(Dispatchers.IO) {
        runCatching {
            val dao = db.watchHistoryDao()
            ActionResult.Done(0L, if (allProfiles) dao.clearAll() else dao.clearForProfile(profiles.currentIdNow))
        }.getOrDefault(ActionResult.Failed)
    }

    /** Positions de reprise remises à zéro (l'historique reste). */
    suspend fun clearResumePositions(allProfiles: Boolean): ActionResult = withContext(Dispatchers.IO) {
        runCatching {
            val dao = db.watchHistoryDao()
            ActionResult.Done(0L, if (allProfiles) dao.resetPositionsAll() else dao.resetPositionsForProfile(profiles.currentIdNow))
        }.getOrDefault(ActionResult.Failed)
    }

    /**
     * Nettoyage automatique : fichiers temporaires de synchro anciens + cache d'images s'il dépasse la limite.
     * Sans effet pendant une synchro ni si le réglage est coupé (sauf [force]). Renvoie les octets libérés.
     */
    suspend fun autoClean(force: Boolean = false): Long = withContext(Dispatchers.IO) {
        if (!force && !prefs.state.value.autoClean) return@withContext 0L
        if (syncRunning()) return@withContext 0L
        var freed = runCatching { TempFiles.purge(ctx.cacheDir, System.currentTimeMillis()) }.getOrDefault(0L)
        runCatching {
            val size = StorageMath.sizeOf(imageDir())
            if (ImageCacheLimit.overLimit(size, imageCacheLimitBytes())) {
                clearCoilDisk()
                freed += (size - StorageMath.sizeOf(imageDir())).coerceAtLeast(0L)
            }
        }
        freed
    }

    /** (Dés)active le nettoyage périodique (WorkManager) selon le réglage : un seul travail, jamais dupliqué. */
    fun applySchedule() {
        val wm = runCatching { WorkManager.getInstance(ctx) }.getOrNull() ?: return
        if (prefs.state.value.autoClean) {
            val req = PeriodicWorkRequestBuilder<MemoryCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(CLEANUP_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else wm.cancelUniqueWork(CLEANUP_WORK)
    }

    companion object {
        const val DB_NAME = "ultra-tv.db"
        const val IMAGE_DIR = "image_cache"
        const val CLEANUP_WORK = "ultratv-memory-cleanup"
    }
}

/** Nettoyage automatique périodique (une fois par jour, batterie non faible). */
@HiltWorker
class MemoryCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val manager: MemoryManager,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { manager.autoClean() }
        return Result.success()
    }
}

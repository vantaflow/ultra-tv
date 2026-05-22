package com.ultratv.tv.nativeapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application root. Implements Coil's [ImageLoaderFactory] for app-wide image
 * caching and WorkManager's [Configuration.Provider] so [SyncWorker] can be
 * instantiated through Hilt with its repository dependencies.
 *
 * Also hooks the uncaught-exception handler to ship crashes directly to the
 * Cloudflare Worker via [RemoteLog.crashSync], with no local file buffer.
 */
@HiltAndroidApp
class UltraTvApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var deviceMac: com.ultratv.tv.nativeapp.data.config.DeviceMac

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        // Tell RemoteLog who we are before anyone calls it.
        val pkg = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        RemoteLog.init(
            ctx = this,
            mac = deviceMac.mac,
            versionName = pkg.versionName ?: "",
            versionCode = pkg.versionCode,
        )
        RemoteLog.info("app", "onCreate")

        // Pipe every uncaught crash straight to the worker. crashSync blocks
        // briefly (≤ 3 s) so the request actually leaves the device before the
        // process is replaced by the system death dialog. The previous handler
        // (if any) is invoked afterwards so the OS still gets to log + kill.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            RemoteLog.crashSync(t, e)
            previous?.uncaughtException(t, e)
        }

        // Initialise the Cast SDK eagerly so the first time the player asks
        // for CastContext.getSharedInstance() it doesn't block. We swallow the
        // exception when Google Play Services are absent (some Android TV
        // builds strip them) — the player will just not show the Cast button.
        runCatching {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this) { it.run() }
        }
    }

    override fun onTerminate() {
        // Rarely called on real devices, but useful when the simulator quits.
        RemoteLog.info("app", "onTerminate")
        super.onTerminate()
    }
}

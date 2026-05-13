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
 */
@HiltAndroidApp
class UltraTvApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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
        // Write any uncaught crash to /sdcard/Android/data/<pkg>/files/crash.txt
        // so users on boxes without ADB can pull the stack trace via SAF.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                val body = "$ts ${t.name} ${e.javaClass.name}: ${e.message}\n$sw\n\n"
                getExternalFilesDir(null)?.resolve("crash.txt")
                    ?.appendText(body, Charsets.UTF_8)
            }
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
}

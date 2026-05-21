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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Application root. Implements Coil's [ImageLoaderFactory] for app-wide image
 * caching and WorkManager's [Configuration.Provider] so [SyncWorker] can be
 * instantiated through Hilt with its repository dependencies.
 */
private const val CRASH_WORKER_URL = "https://ultratv-config.khalilbenaz.workers.dev"
private const val CRASH_TOKEN = "f-w31zHuqg0ntBPRSJtOVEXGB55B9uv5"

@HiltAndroidApp
class UltraTvApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var deviceMac: com.ultratv.tv.nativeapp.data.config.DeviceMac

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // If a previous run died and left a pending crash.txt, ship it to the
        // hard-coded crash-reporting Worker (POST /api/crash). Worker URL and
        // token are constants at the top of the file so every install uploads
        // automatically.
        bgScope.launch { uploadPendingCrashes() }
    }

    private fun uploadPendingCrashes() {
        val file = getExternalFilesDir(null)?.resolve("crash.txt") ?: return
        if (!file.exists() || file.length() == 0L) return

        // Hardcoded so every install uploads crashes automatically — no per-user
        // setup. Rotate the token on the worker (`wrangler secret put CRASH_TOKEN`)
        // and bump the constant here in lock-step when needed.
        val base = CRASH_WORKER_URL
        val token = CRASH_TOKEN

        val stack = runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        if (stack.isBlank()) return

        val pkg = packageManager.getPackageInfo(packageName, 0)
        val payload = JSONObject().apply {
            put("mac", deviceMac.mac)
            put("version", pkg.versionName ?: "")
            @Suppress("DEPRECATION")
            put("versionCode", pkg.versionCode)
            put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("androidSdk", android.os.Build.VERSION.SDK_INT)
            put("stack", stack.take(60_000)) // worker caps at 64 KB
        }.toString()

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("$base/api/crash")
            .header("X-Crash-Token", token)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) file.delete()
            }
        }
    }
}

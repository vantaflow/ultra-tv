package com.ultratv.tv.nativeapp

import android.os.Build
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
 * RemoteLog — fire-and-forget HTTP transport for crashes and ad-hoc events.
 *
 * Direct POSTs, no local buffer file: crashes go to /api/crash inside the
 * UncaughtExceptionHandler with a short timeout (the process is dying — we
 * can't afford to block the OS death dialog); events go to /api/event via a
 * background scope.
 *
 * Endpoint + token are baked into the APK so every install reports without
 * setup. Rotate them in lock-step with the worker secret.
 */
object RemoteLog {

    private const val WORKER_URL = "https://ultratv-config.khalilbenaz.workers.dev"
    private const val TOKEN      = "f-w31zHuqg0ntBPRSJtOVEXGB55B9uv5"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Two clients: events tolerate slow networks; the crash path must return
    // quickly because the process is about to be killed.
    private val eventClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val crashClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    /** Set once on Application.onCreate() so each entry knows who it came from. */
    @Volatile private var contextInfo: ContextInfo? = null

    data class ContextInfo(
        val mac: String,
        val versionName: String,
        val versionCode: Int,
        val device: String,
        val androidSdk: Int,
    )

    fun init(mac: String, versionName: String, versionCode: Int) {
        contextInfo = ContextInfo(
            mac = mac,
            versionName = versionName,
            versionCode = versionCode,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidSdk = Build.VERSION.SDK_INT,
        )
    }

    /** Ship a non-fatal log/event. Returns immediately; HTTP happens off-thread. */
    fun event(tag: String, message: String, level: String = "info") {
        val ctx = contextInfo ?: return
        scope.launch {
            runCatching {
                val body = JSONObject().apply {
                    put("level", level)
                    put("tag", tag)
                    put("message", message)
                    put("mac", ctx.mac)
                    put("version", ctx.versionName)
                    put("versionCode", ctx.versionCode)
                    put("device", ctx.device)
                }.toString()
                val req = Request.Builder()
                    .url("$WORKER_URL/api/event")
                    .header("X-Crash-Token", TOKEN)
                    .post(body.toRequestBody(JSON))
                    .build()
                eventClient.newCall(req).execute().close()
            }
        }
    }

    fun info(tag: String, message: String) = event(tag, message, "info")
    fun warn(tag: String, message: String) = event(tag, message, "warn")
    fun error(tag: String, message: String) = event(tag, message, "error")
    fun debug(tag: String, message: String) = event(tag, message, "debug")

    /**
     * Blocking POST inside the uncaught handler. The process is about to be
     * killed; we have ~3s before Android replaces us with the crash dialog, so
     * the OkHttp timeouts above keep us within that budget.
     */
    fun crashSync(thread: Thread, error: Throwable) {
        val ctx = contextInfo ?: return
        runCatching {
            val sw = java.io.StringWriter()
            error.printStackTrace(java.io.PrintWriter(sw))
            val body = JSONObject().apply {
                put("mac", ctx.mac)
                put("version", ctx.versionName)
                put("versionCode", ctx.versionCode)
                put("device", ctx.device)
                put("androidSdk", ctx.androidSdk)
                put("stack", "${thread.name}: ${error.javaClass.name}: ${error.message}\n$sw")
            }.toString()
            val req = Request.Builder()
                .url("$WORKER_URL/api/crash")
                .header("X-Crash-Token", TOKEN)
                .post(body.toRequestBody(JSON))
                .build()
            crashClient.newCall(req).execute().close()
        }
    }

    private val JSON = "application/json".toMediaType()
}

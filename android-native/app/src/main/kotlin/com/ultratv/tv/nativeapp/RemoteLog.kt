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
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * RemoteLog — transport HTTP « tire et oublie » pour les crashs et événements.
 *
 * Authentification : jeton d'APPAREIL (Authorization: Bearer) obtenu par
 * appairage et rangé dans le Keystore. Plus aucun jeton partagé dans l'APK : un
 * secret embarqué dans un binaire public n'est pas un secret. Tant que
 * l'appareil n'est pas appairé, rien n'est envoyé ; les crashs restent dans
 * crash.txt et partent au premier démarrage suivant l'appairage.
 *
 * L'URL du Worker est configurable (Réglages, ou ULTRA_WORKER_URL au build) ;
 * HTTPS est exigé hors build debug. Tout texte est nettoyé par [LogSanitizer]
 * avant envoi (identifiants Xtream, jetons).
 */
object RemoteLog {

    /** URL choisie par l'utilisateur dans les Réglages ; vide = valeur du build. */
    @Volatile var workerUrlOverride: String = ""

    private fun workerBase(): String? =
        WorkerUrl.normalize(workerUrlOverride.ifBlank { BuildConfig.WORKER_URL }, allowCleartext = BuildConfig.DEBUG)

    @Volatile private var tokenStore: DeviceTokenStore? = null

    /** Requête authentifiée, ou null si l'appareil n'est pas appairé / URL invalide. */
    private fun authedRequest(path: String, body: String): Request? {
        val base = workerBase() ?: return null
        val token = tokenStore?.token() ?: return null
        return Request.Builder()
            .url("$base$path")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(JSON))
            .build()
    }

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
    @Volatile private var appCtx: android.content.Context? = null

    /** Mirrors UserPrefs.telemetryEnabled; toggled at runtime from Settings.
     *  Defaults to true so the dashboard keeps catching crashes out of the box. */
    @Volatile var telemetryEnabled: Boolean = true

    private fun sanitize(input: String): String = LogSanitizer.sanitize(input)

    data class ContextInfo(
        val mac: String,
        val versionName: String,
        val versionCode: Int,
        val device: String,
        val androidSdk: Int,
    )

    fun init(ctx: android.content.Context, mac: String, versionName: String, versionCode: Int) {
        appCtx = ctx.applicationContext
        tokenStore = DeviceTokenStore(ctx.applicationContext)
        contextInfo = ContextInfo(
            mac = mac,
            versionName = versionName,
            versionCode = versionCode,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidSdk = Build.VERSION.SDK_INT,
        )
        // Try to ship anything that survived a previous crash. crashSync's
        // synchronous POST can be cut short by the dying process; this is the
        // safety net that runs from the next clean start.
        scope.launch { flushPendingCrash() }
    }

    private fun pendingCrashFile(): java.io.File? {
        val ctx = appCtx ?: return null
        return ctx.getExternalFilesDir(null)?.resolve("crash.txt")
            ?: java.io.File(ctx.filesDir, "crash.txt")
    }

    private fun flushPendingCrash() {
        val ctx = contextInfo ?: return
        if (!telemetryEnabled) return
        val file = pendingCrashFile() ?: return
        if (!file.exists() || file.length() == 0L) return
        val stack = runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        if (stack.isBlank()) return
        runCatching {
            val body = JSONObject().apply {
                put("mac", ctx.mac)
                put("version", ctx.versionName)
                put("versionCode", ctx.versionCode)
                put("device", ctx.device)
                put("androidSdk", ctx.androidSdk)
                put("stack", sanitize(stack).take(60_000))
            }.toString()
            val req = authedRequest("/api/crash", body) ?: return@runCatching
            eventClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) file.delete()
            }
        }
    }

    /** Ship a non-fatal log/event. Returns immediately; HTTP happens off-thread. */
    fun event(tag: String, message: String, level: String = "info") {
        val ctx = contextInfo ?: return
        if (!telemetryEnabled) return
        val safeMessage = sanitize(message)
        scope.launch {
            runCatching {
                val body = JSONObject().apply {
                    put("level", level)
                    put("tag", tag)
                    put("message", safeMessage)
                    put("mac", ctx.mac)
                    put("version", ctx.versionName)
                    put("versionCode", ctx.versionCode)
                    put("device", ctx.device)
                }.toString()
                val req = authedRequest("/api/event", body) ?: return@runCatching
                eventClient.newCall(req).execute().close()
            }
        }
    }

    fun info(tag: String, message: String) = event(tag, message, "info")
    fun warn(tag: String, message: String) = event(tag, message, "warn")
    fun error(tag: String, message: String) = event(tag, message, "error")
    fun debug(tag: String, message: String) = event(tag, message, "debug")

    /**
     * Called from the uncaught handler. First writes the stack to a local
     * file so the next clean start can re-upload if the process dies before
     * the network round-trip completes — then attempts a best-effort
     * synchronous POST with a 3 s budget.
     */
    fun crashSync(thread: Thread, error: Throwable) {
        val ctx = contextInfo ?: return
        val sw = java.io.StringWriter()
        error.printStackTrace(java.io.PrintWriter(sw))
        val payload = sanitize("${thread.name}: ${error.javaClass.name}: ${error.message}\n$sw")

        // 1. Persist locally so we can retry on next launch if the network leg
        //    doesn't complete in time. The crash.txt file is the source of
        //    truth — flushPendingCrash() deletes it on a successful upload.
        runCatching { pendingCrashFile()?.appendText(payload + "\n\n", Charsets.UTF_8) }

        // 2. Best-effort live upload. Honour the user's telemetry toggle.
        if (!telemetryEnabled) return
        runCatching {
            val body = JSONObject().apply {
                put("mac", ctx.mac)
                put("version", ctx.versionName)
                put("versionCode", ctx.versionCode)
                put("device", ctx.device)
                put("androidSdk", ctx.androidSdk)
                put("stack", payload)
            }.toString()
            val req = authedRequest("/api/crash", body) ?: return@runCatching
            crashClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) pendingCrashFile()?.delete()
            }
        }
    }

    private val JSON = "application/json".toMediaType()
}

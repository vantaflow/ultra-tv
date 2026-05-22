package com.ultratv.tv.nativeapp.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.RemoteLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * GitHub-Releases-backed self-updater. No Play Store, no Firebase.
 *
 * Flow:
 *   1) [checkForUpdate] hits GitHub's REST API and parses tag_name + the
 *      UltraTV-debug.apk asset URL from the *latest* release. We compare
 *      versionCode (declared in build.gradle.kts) — if the remote tag's name
 *      maps to a higher one we surface an [UpdateInfo].
 *   2) [downloadAndInstall] streams the APK into the app's filesDir and uses
 *      PackageInstaller to commit a session. The system prompts the user the
 *      first time (Settings → Install unknown apps for Ultra TV).
 */
object UpdateChecker {

    private const val TAG = "update"
    private const val REPO = "khalilbenaz/ultra-tv"
    private const val APK_NAME = "UltraTV-debug.apk"

    data class UpdateInfo(
        val tag: String,
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val notes: String,
    )

    private val _state = MutableStateFlow<UpdateInfo?>(null)
    /** Latest known available update, or null if app is up-to-date / not checked. */
    val state: StateFlow<UpdateInfo?> = _state.asStateFlow()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    RemoteLog.warn(TAG, "github releases ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val tag = json.optString("tag_name").trim()           // e.g. "v1.0.5"
                val notes = json.optString("body").take(280)
                val verName = tag.removePrefix("v")
                val remoteCode = versionCodeFromName(verName) ?: run {
                    RemoteLog.warn(TAG, "unparseable tag $tag")
                    return@use null
                }
                if (remoteCode <= BuildConfig.VERSION_CODE) {
                    RemoteLog.debug(TAG, "up to date (local=${BuildConfig.VERSION_CODE} remote=$remoteCode)")
                    return@use null
                }
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").equals(APK_NAME, ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (apkUrl.isNullOrBlank()) {
                    RemoteLog.warn(TAG, "no $APK_NAME asset on $tag")
                    return@use null
                }
                val info = UpdateInfo(tag, verName, remoteCode, apkUrl!!, notes)
                _state.value = info
                RemoteLog.info(TAG, "update available: $tag (code $remoteCode)")
                info
            }
        }.onFailure {
            RemoteLog.warn(TAG, "check failed: ${it.javaClass.simpleName} ${it.message}")
        }.getOrNull()
    }

    /**
     * Encodes a "x.y.z" versionName as one integer for comparison. Matches the
     * scheme used in build.gradle.kts (versionCode is bumped manually but the
     * versionName follows semver), so 1.0.5 → 10005.
     */
    private fun versionCodeFromName(name: String): Int? {
        val parts = name.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return null
        val major = parts.getOrNull(0) ?: 0
        val minor = parts.getOrNull(1) ?: 0
        val patch = parts.getOrNull(2) ?: 0
        return major * 10_000 + minor * 100 + patch
    }

    /**
     * Compares against the actual installed VERSION_CODE (build.gradle.kts).
     * Falls back to the parsed name above when the live versionCode hasn't been
     * bumped (release script could lag a step).
     */
    private val BuildConfig.versionCodeNormalised: Int
        get() = maxOf(BuildConfig.VERSION_CODE, versionCodeFromName(BuildConfig.VERSION_NAME) ?: 0)

    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            val apk = downloadApk(ctx, info, onProgress)
            installApk(ctx, apk)
        }
    }

    private fun downloadApk(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.filesDir, "updates").apply { mkdirs() }
        // Clean previous APKs so we don't fill the disk on repeat updates.
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "ultra-tv-${info.versionName}.apk")
        val req = Request.Builder().url(info.apkUrl).build()
        http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "download failed ${resp.code}" }
            val body = resp.body ?: error("empty body")
            val total = body.contentLength().coerceAtLeast(1L)
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        sent += n
                        onProgress((sent.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        }
        RemoteLog.info(TAG, "downloaded ${out.length()} bytes for ${info.tag}")
        return out
    }

    private fun installApk(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = pi.createSession(params)
        try {
            pi.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(ctx, InstallReceiver::class.java).apply { action = INSTALL_ACTION }
                val flags = if (Build.VERSION.SDK_INT >= 31)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT
                val pending = PendingIntent.getBroadcast(ctx, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (t: Throwable) {
            pi.abandonSession(sessionId)
            throw t
        }
    }

    const val INSTALL_ACTION = "com.ultratv.tv.nativeapp.INSTALL_RESULT"

    /** Receives the install-session result so we can log the outcome. */
    class InstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // System needs the user to confirm — launch the confirm activity.
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (confirm != null) {
                        runCatching { context.startActivity(confirm) }
                    }
                    RemoteLog.info("update", "install requires user action")
                }
                PackageInstaller.STATUS_SUCCESS -> RemoteLog.info("update", "install success")
                else -> {
                    Log.w("update", "install failed $status $msg")
                    RemoteLog.warn("update", "install failed status=$status $msg")
                }
            }
        }
    }

    fun registerInstallReceiver(ctx: Context) {
        runCatching {
            val flags = if (Build.VERSION.SDK_INT >= 34) Context.RECEIVER_NOT_EXPORTED else 0
            ctx.applicationContext.registerReceiver(InstallReceiver(), IntentFilter(INSTALL_ACTION), flags)
        }
    }
}

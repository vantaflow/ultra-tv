package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import android.os.Build
import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.announce.AnnouncementPlan
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.ui.common.isTelevision
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Règles pures du relevé anonyme d'installation (testées sans Android). */
object AnonPingPlan {
    /** Au plus un relevé par jour (20 h : une marge pour ne pas glisser d'un jour à l'autre). */
    const val MIN_INTERVAL_MS = 20 * 3600_000L

    /** Dû si jamais envoyé, si 20 h au moins se sont écoulées, ou si l'horloge est revenue en arrière. */
    fun due(lastMs: Long, nowMs: Long): Boolean = lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= MIN_INTERVAL_MS

    const val PLATFORM_TV = "android-tv"
    const val PLATFORM_MOBILE = "android-mobile"

    private val CONTROL_CHARS = Regex("[\\u0000-\\u001f\\u007f]+")
    private val SPACES = Regex("\\s+")

    /** Application utilisée : `android-tv` (interface TV / leanback) ou `android-mobile` (téléphone, tablette). */
    fun platform(isTelevision: Boolean): String = if (isTelevision) PLATFORM_TV else PLATFORM_MOBILE

    /** Modèle court (« SKYWORTH 4K ») : fabricant + modèle sans doublon, caractères de contrôle retirés, 64 caractères au plus. */
    fun modelLabel(manufacturer: String?, model: String?): String {
        val man = manufacturer.orEmpty().trim()
        val mod = model.orEmpty().trim()
        val joined = if (man.isEmpty() || mod.startsWith(man, ignoreCase = true)) mod else "$man $mod"
        return joined.replace(CONTROL_CHARS, " ").replace(SPACES, " ").trim().take(64)
    }

    /**
     * Seul le nécessaire part : identifiant aléatoire, version, application (TV ou mobile) et modèle de l'appareil
     * (le pays est déduit par le Worker, jamais l'adresse IP gardée).
     */
    fun body(install: String, version: String, platform: String, model: String): String {
        val o = JSONObject().put("install", install).put("version", version).put("platform", platform)
        if (model.isNotBlank()) o.put("model", model)
        return o.toString()
    }

    /** Envoie le relevé ; vrai si le Worker l'a accepté. Aucune exception ne sort. */
    suspend fun post(http: OkHttpClient, base: String, install: String, version: String, platform: String, model: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$base/api/anon/ping").post(body(install, version, platform, model).toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

/**
 * Relevé anonyme d'installation : au démarrage, au plus une fois par jour, si la télémétrie est activée et si l'appareil n'a
 * pas de compte (un appareil appairé est déjà compté avec son compte). Rien d'autre que l'identifiant aléatoire, la version,
 * l'application (TV ou mobile) et le modèle de l'appareil.
 */
@Singleton
class AnonPingReporter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val http: OkHttpClient,
    private val prefs: UserPreferencesStore,
    private val tokens: DeviceTokenStore,
    private val installId: InstallIdStore,
) {
    suspend fun reportIfDue(nowMs: Long = System.currentTimeMillis()) {
        val p = prefs.flow.first()
        if (!p.telemetryEnabled || tokens.isPaired) return
        val sp = ctx.getSharedPreferences("anon_ping", Context.MODE_PRIVATE)
        if (!AnonPingPlan.due(sp.getLong("last", 0L), nowMs)) return
        val base = WorkerUrl.normalize(WorkerUrl.resolve(p.workerBaseUrl), BuildConfig.DEBUG) ?: return
        val version = AnnouncementPlan.versionParam(BuildConfig.VERSION_NAME)
        val platform = AnonPingPlan.platform(isTelevision(ctx))
        val model = AnonPingPlan.modelLabel(Build.MANUFACTURER, Build.MODEL)
        if (AnonPingPlan.post(http, base, installId.get(), version, platform, model)) sp.edit().putLong("last", nowMs).apply()
    }
}

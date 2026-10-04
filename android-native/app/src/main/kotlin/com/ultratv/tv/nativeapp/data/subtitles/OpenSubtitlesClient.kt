package com.ultratv.tv.nativeapp.data.subtitles

import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SubtitleHit(val id: String, val language: String, val release: String, val downloads: Int)

/**
 * Recherche de sous-titres OpenSubtitles pour les films, UNIQUEMENT via le proxy Worker de l'appareil appairé (la clé
 * OpenSubtitles ne vit jamais dans l'appli). Contrat attendu du Worker (à implémenter côté Worker) :
 *  - `GET {proxy}/api/subtitles/search?query=<titre>&languages=fr,en[&year=2020]` -> `{"results":[{"id","language","release","downloads"}]}`
 *  - `GET {proxy}/api/subtitles/download?id=<id>` -> le fichier SRT (UTF-8)
 * Authentification : `Authorization: Bearer <jeton d'appareil>`. Sans proxy appairé, [isAvailable] est faux : l'option est désactivée.
 */
@Singleton
class OpenSubtitlesClient @Inject constructor(
    okHttp: OkHttpClient,
    private val prefs: UserPreferencesStore,
    private val token: DeviceTokenStore,
) {
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /** Base du Worker appairé, ou null si aucun proxy n'est configuré / appairé. */
    suspend fun proxyBase(): String? {
        if (!token.isPaired) return null
        // Adresse vide = Worker par défaut (comme la synchro cloud) ; sinon « appairez l'appareil » s'affichait à tort.
        val raw = prefs.flow.first().workerBaseUrl.ifBlank { com.ultratv.tv.nativeapp.BuildConfig.WORKER_URL }.trim().trimEnd('/')
        return raw.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
    }

    /** Le Worker a répondu 503 « subtitles_not_configured » : l'option reste désactivée jusqu'au prochain lancement. */
    @Volatile var serviceMissing: Boolean = false
        private set

    suspend fun isAvailable(): Boolean = proxyBase() != null && !serviceMissing

    suspend fun search(title: String, languages: List<String>, year: Int? = null): List<SubtitleHit> = withContext(Dispatchers.IO) {
        val base = proxyBase() ?: return@withContext emptyList()
        val url = (base + "/api/subtitles/search").toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("query", title)
            ?.apply { if (languages.isNotEmpty()) addQueryParameter("languages", languages.joinToString(",")); year?.let { addQueryParameter("year", it.toString()) } }
            ?.build() ?: return@withContext emptyList()
        http.newCall(authed(url.toString())).execute().use { r ->
            if (r.code == 503) { serviceMissing = true; return@withContext emptyList() }
            if (!r.isSuccessful) return@withContext emptyList()
            parseResults(r.body?.string().orEmpty())
        }
    }

    /** Télécharge un sous-titre dans [dir] ; renvoie le fichier, ou null en cas d'échec. */
    suspend fun download(hit: SubtitleHit, dir: File): File? = withContext(Dispatchers.IO) {
        val base = proxyBase() ?: return@withContext null
        val url = (base + "/api/subtitles/download").toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("id", hit.id)?.build() ?: return@withContext null
        http.newCall(authed(url.toString())).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val body = r.body ?: return@withContext null
            if (body.contentLength() > MAX_BYTES) return@withContext null
            dir.mkdirs()
            File(dir, "sub-${hit.id.filter { it.isLetterOrDigit() }.take(24)}.srt").also { f -> f.writeBytes(body.bytes().take(MAX_BYTES.toInt()).toByteArray()) }
        }
    }

    private fun authed(url: String) = Request.Builder().url(url).header("Authorization", "Bearer ${token.token().orEmpty()}").build()

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
        fun parseResults(json: String): List<SubtitleHit> = runCatching {
            val a = JSONObject(json).optJSONArray("results") ?: return emptyList()
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SubtitleHit(id, o.optString("language"), o.optString("release"), o.optInt("downloads", 0))
            }.sortedByDescending { it.downloads }
        }.getOrDefault(emptyList())
    }
}

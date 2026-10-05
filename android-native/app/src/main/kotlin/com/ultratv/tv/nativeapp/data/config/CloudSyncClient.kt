package com.ultratv.tv.nativeapp.data.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Réponse de GET /api/config : `notModified` (304) ou le corps JSON et son ETag. */
data class ConfigFetch(val notModified: Boolean, val body: String, val etag: String?)

/**
 * Client HTTP de la synchro multi-appareils (lecture conditionnelle, envoi, retrait, renommage).
 * Aucune redirection suivie (un 30x vers http:// ferait fuiter le jeton) ; aucune URL de flux n'est journalisée.
 */
@Singleton
class CloudSyncClient @Inject constructor(okHttp: OkHttpClient) {
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = "application/json".toMediaType()

    private fun check(code: Int, retryAfter: String?) {
        if (code == 401) throw TokenRejectedException()
        if (code == 429) throw RateLimitedException(retryAfter?.toIntOrNull() ?: 30)
        if (code in 300..399) throw CloudSyncException("Unexpected redirect (HTTP $code) from the Worker")
    }

    suspend fun fetch(base: String, token: String, etag: String?): ConfigFetch = withContext(Dispatchers.IO) {
        val b = Request.Builder().url("$base/api/config").header("Authorization", "Bearer $token")
        if (etag != null) b.header("If-None-Match", etag)
        http.newCall(b.get().build()).execute().use { r ->
            if (r.code == 304) return@use ConfigFetch(true, "", etag)
            check(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} — config unreachable")
            ConfigFetch(false, r.body?.string().orEmpty(), r.header("ETag"))
        }
    }

    /** Crée ou met à jour une source ; renvoie le corps JSON (`provider.id` = identifiant cloud). */
    suspend fun put(base: String, token: String, body: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/api/device/providers").header("Authorization", "Bearer $token").post(body.toRequestBody(json)).build()
        http.newCall(req).execute().use { r ->
            check(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while sharing the source")
            r.body?.string().orEmpty()
        }
    }

    /**
     * Publie les réglages d'affichage d'une source. Renvoie `null` si acceptés, ou les réglages GAGNANTS du cloud
     * (HTTP 409 : une version plus récente existe déjà). 404 = source plus visible : ignoré.
     */
    suspend fun putPrefs(base: String, token: String, cloudId: String, body: String): CloudPrefs? = withContext(Dispatchers.IO) {
        require(Regex("[0-9a-f]{8}").matches(cloudId)) { "bad id" }
        val req = Request.Builder().url("$base/api/device/providers/$cloudId/prefs").header("Authorization", "Bearer $token").put(body.toRequestBody(json)).build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) return@use null
            if (r.code == 409) return@use DisplayPrefs.parse(runCatching { org.json.JSONObject(r.body?.string().orEmpty()).optJSONObject("prefs") }.getOrNull())
            check(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while sharing display settings")
            null
        }
    }

    /** Échange l'état partagé d'une source (favoris, reprises, derniers vus) ; renvoie l'état fusionné, null si 404. */
    suspend fun syncState(base: String, token: String, cloudId: String, body: String): String? = withContext(Dispatchers.IO) {
        require(Regex("[0-9a-f]{8}").matches(cloudId)) { "bad id" }
        val req = Request.Builder().url("$base/api/device/providers/$cloudId/state").header("Authorization", "Bearer $token").post(body.toRequestBody(json)).build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) return@use null
            check(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while syncing favorites and progress")
            r.body?.string()
        }
    }

    /** Retire cet appareil de la source (`everywhere` = la supprime du compte). 404 = déjà retirée. */
    suspend fun delete(base: String, token: String, cloudId: String, everywhere: Boolean = false) = withContext(Dispatchers.IO) {
        require(Regex("[0-9a-f]{8}").matches(cloudId)) { "bad id" }
        val req = Request.Builder().url("$base/api/device/providers/$cloudId" + if (everywhere) "?all=1" else "")
            .header("Authorization", "Bearer $token").delete().build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) return@use
            check(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while removing the source")
        }
    }

    suspend fun renameSelf(base: String, token: String, name: String) = withContext(Dispatchers.IO) {
        val body = org.json.JSONObject().put("name", name).toString().toRequestBody(json)
        val req = Request.Builder().url("$base/api/device").header("Authorization", "Bearer $token").patch(body).build()
        http.newCall(req).execute().use { r ->
            check(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while renaming the device")
        }
    }
}

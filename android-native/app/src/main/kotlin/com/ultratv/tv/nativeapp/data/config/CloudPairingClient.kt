package com.ultratv.tv.nativeapp.data.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Le Worker a refusé le jeton (révoqué, inconnu) : il faut ré-appairer. */
class TokenRejectedException : RuntimeException("Device token rejected — pair this device again")

/** Trop de requêtes : réessayer après [retryAfterSec]. */
class RateLimitedException(val retryAfterSec: Int) : RuntimeException("Rate limited, retry in ${retryAfterSec}s")

class CloudSyncException(message: String) : RuntimeException(message)

/** URL de base du Worker : HTTPS obligatoire (HTTP seulement dans les builds debug). */
object WorkerUrl {
    fun normalize(raw: String, allowCleartext: Boolean): String? {
        val s = raw.trim().trimEnd('/')
        val u = runCatching { URI(s) }.getOrNull() ?: return null
        val scheme = u.scheme?.lowercase() ?: return null
        if (scheme != "https" && !(allowCleartext && scheme == "http")) return null
        if (u.host.isNullOrBlank() || u.userInfo != null || u.rawQuery != null || u.rawFragment != null) return null
        return s
    }
}

data class PairingSession(val code: String, val pollSecret: String, val expiresInSec: Int, val intervalSec: Int)

sealed interface PollResult {
    data object Pending : PollResult
    data object Gone : PollResult // code expiré, inconnu ou déjà consommé
    data class Paired(val token: String, val deviceId: String) : PollResult
}

/**
 * Client HTTP du Worker (appairage + lecture de configuration par jeton).
 * Pas de redirection suivie : un Worker n'en émet pas, et suivre un 30x vers
 * http:// ferait fuiter le jeton.
 */
@Singleton
class CloudPairingClient @Inject constructor(okHttp: OkHttpClient) {

    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = "application/json".toMediaType()

    suspend fun start(base: String, label: String): PairingSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("label", label).toString().toRequestBody(json)
        http.newCall(Request.Builder().url("$base/api/pair/start").post(body).build()).execute().use { r ->
            checkCommon(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while requesting a pairing code")
            val o = JSONObject(r.body?.string().orEmpty())
            PairingSession(o.getString("code"), o.getString("pollSecret"), o.optInt("expiresIn", 600), o.optInt("interval", 3))
        }
    }

    suspend fun poll(base: String, s: PairingSession): PollResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", s.code).put("pollSecret", s.pollSecret).toString().toRequestBody(json)
        http.newCall(Request.Builder().url("$base/api/pair/poll").post(body).build()).execute().use { r ->
            checkCommon(r.code, r.header("Retry-After"))
            when (r.code) {
                202 -> PollResult.Pending
                404, 410 -> PollResult.Gone
                200 -> JSONObject(r.body?.string().orEmpty()).let { PollResult.Paired(it.getString("token"), it.getString("deviceId")) }
                else -> throw CloudSyncException("HTTP ${r.code} while waiting for pairing")
            }
        }
    }

    /** Corps JSON brut de /api/config (liste des fournisseurs). */
    suspend fun fetchConfig(base: String, token: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/api/config").header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401) throw TokenRejectedException()
            checkCommon(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} — config unreachable")
            r.body?.string().orEmpty()
        }
    }

    /** Échange le jeton contre un nouveau (l'ancien devient invalide). Renvoie (jeton, deviceId). */
    suspend fun rotate(base: String, token: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/api/device/rotate").header("Authorization", "Bearer $token")
            .post("".toRequestBody(null)).build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401) throw TokenRejectedException()
            checkCommon(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while rotating the token")
            JSONObject(r.body?.string().orEmpty()).let { it.getString("token") to it.getString("deviceId") }
        }
    }

    private fun checkCommon(code: Int, retryAfter: String?) {
        if (code == 429) throw RateLimitedException(retryAfter?.toIntOrNull() ?: 30)
        if (code in 300..399) throw CloudSyncException("Unexpected redirect (HTTP $code) from the Worker")
    }
}

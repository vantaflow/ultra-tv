package com.ultratv.tv.nativeapp.data.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source envoyée depuis un téléphone ou un ordinateur (page /envoyer du Worker). Contient des identifiants :
 * [toString] ne les montre jamais (aucun journal, aucune trace de pile ne doit les contenir).
 */
data class WebSource(val kind: String, val name: String, val url: String, val username: String, val password: String, val mac: String) {
    override fun toString() = "WebSource(kind=$kind)"
}

/** Règles pures de l'envoi depuis le web (testées sans Android). */
object WebSendLogic {
    /** Adresse encodée dans le QR : `<worker>/envoyer?c=ABC123`. */
    fun sendUrl(base: String, code: String): String =
        base.trimEnd('/') + "/envoyer?c=" + code.uppercase().filter { it.isLetterOrDigit() }

    /** Adresse courte à saisir à la main : `hôte/envoyer`. */
    fun shortAddress(base: String): String =
        base.trim().substringAfter("://").substringBefore('/').let { if (it.isEmpty()) "" else "$it/envoyer" }

    /** « K7Q2M9 » → « K7Q-2M9 » (lisible à distance) ; un code déjà groupé ou trop court est conservé. */
    fun groupCode(code: String): String =
        if (code.contains('-') || code.length < 6) code else code.substring(0, code.length / 2) + "-" + code.substring(code.length / 2)

    private val MAC = Regex("^([0-9A-Fa-f]{2}[:-]?){5}[0-9A-Fa-f]{2}$")

    private fun httpUrl(s: String): Boolean {
        val t = s.trim()
        if (!(t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true))) return false
        return !runCatching { java.net.URI(t).host }.getOrNull().isNullOrBlank()
    }

    /**
     * Corps `provider` de la réponse de /api/envoi/poll → source, ou null si invalide (le Worker a déjà validé, on revérifie :
     * on n'ajoute jamais une source douteuse). La MAC est ramenée à `AA:BB:CC:DD:EE:FF`.
     */
    fun parseSource(o: JSONObject?): WebSource? {
        o ?: return null
        val kind = o.optString("kind").uppercase()
        val url = o.optString("url").trim()
        // GUIDE : adresse d'un guide XMLTV (guide complémentaire), revérifiée ici comme à la saisie sur la TV.
        if (kind == "GUIDE") {
            if (com.ultratv.tv.nativeapp.data.xmltv.GuideAddress.validate(url) !is com.ultratv.tv.nativeapp.data.xmltv.GuideAddress.Validation.Ok) return null
            return WebSource(kind, "", url, "", "", "")
        }
        if (kind !in setOf("XTREAM", "M3U", "STALKER") || !httpUrl(url)) return null
        val user = o.optString("username"); val pass = o.optString("password")
        if (kind == "XTREAM" && (user.isBlank() || pass.isBlank())) return null
        var mac = ""
        if (kind == "STALKER") {
            val raw = o.optString("mac")
            if (!MAC.matches(raw)) return null
            mac = raw.filter { it != ':' && it != '-' }.uppercase().chunked(2).joinToString(":")
        }
        return WebSource(kind, o.optString("name").trim().take(64), url, user, pass, mac)
    }
}

data class WebSendSession(val code: String, val secret: String, val expiresInSec: Int, val intervalSec: Int)

sealed interface WebPollResult {
    data object Pending : WebPollResult
    data object Gone : WebPollResult // code expiré, inconnu ou déjà remis
    data class Received(val source: WebSource) : WebPollResult
}

/** Client HTTP de l'envoi depuis le web : création du code, puis interrogation avec le secret. Aucune redirection suivie. */
@Singleton
class WebSendClient @Inject constructor(okHttp: OkHttpClient) {
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = "application/json".toMediaType()

    /** [install] : identifiant d'installation (statistiques anonymes), null si la télémétrie est coupée. */
    suspend fun start(base: String, install: String?): WebSendSession = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { if (install != null) put("install", install) }.toString().toRequestBody(json)
        http.newCall(Request.Builder().url("$base/api/envoi/start").post(body).build()).execute().use { r ->
            check(r.code, r.header("Retry-After"))
            if (!r.isSuccessful) throw CloudSyncException("HTTP ${r.code} while requesting a code")
            val o = JSONObject(r.body?.string().orEmpty())
            WebSendSession(o.getString("code"), o.getString("secret"), o.optInt("expiresIn", 900), o.optInt("interval", 3))
        }
    }

    suspend fun poll(base: String, s: WebSendSession): WebPollResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", s.code).put("secret", s.secret).toString().toRequestBody(json)
        http.newCall(Request.Builder().url("$base/api/envoi/poll").post(body).build()).execute().use { r ->
            check(r.code, r.header("Retry-After"))
            when (r.code) {
                202 -> WebPollResult.Pending
                404, 410 -> WebPollResult.Gone
                200 -> WebSendLogic.parseSource(JSONObject(r.body?.string().orEmpty()).optJSONObject("provider"))
                    ?.let { WebPollResult.Received(it) } ?: WebPollResult.Gone
                else -> throw CloudSyncException("HTTP ${r.code} while waiting for the phone")
            }
        }
    }

    private fun check(code: Int, retryAfter: String?) {
        if (code == 429) throw RateLimitedException(retryAfter?.toIntOrNull() ?: 30)
        if (code in 300..399) throw CloudSyncException("Unexpected redirect (HTTP $code) from the Worker")
    }
}

sealed interface WebSendEvent {
    data class CodeReady(val code: String, val expiresInSec: Int) : WebSendEvent
    data class Received(val source: WebSource) : WebSendEvent
    data object Expired : WebSendEvent
    data class Failed(val message: String) : WebSendEvent
}

/**
 * Déroulé de l'envoi depuis le web : demande un code, l'affiche, interroge le Worker toutes les ~3 s tant que le flux
 * est collecté (annuler la collecte arrête l'interrogation) et se termine à la réception de la source, à l'expiration
 * du code ou sur une erreur. Les identifiants reçus ne passent que dans [WebSendEvent.Received].
 */
@Singleton
class WebSend @Inject constructor(private val client: WebSendClient) {
    internal var sleeper: suspend (Long) -> Unit = { delay(it) }

    fun run(base: String, install: String?): Flow<WebSendEvent> = flow {
        val session = try {
            client.start(base, install)
        } catch (e: RateLimitedException) {
            emit(WebSendEvent.Failed("Too many attempts — retry in ${e.retryAfterSec}s")); return@flow
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(WebSendEvent.Failed(e.message ?: "Cannot reach the Worker")); return@flow
        }
        emit(WebSendEvent.CodeReady(session.code, session.expiresInSec))
        var waitedMs = 0L
        val limitMs = session.expiresInSec * 1000L
        var intervalMs = session.intervalSec.coerceIn(1, 30) * 1000L
        while (waitedMs <= limitMs) {
            sleeper(intervalMs); waitedMs += intervalMs
            try {
                when (val r = client.poll(base, session)) {
                    WebPollResult.Pending -> Unit
                    WebPollResult.Gone -> { emit(WebSendEvent.Expired); return@flow }
                    is WebPollResult.Received -> { emit(WebSendEvent.Received(r.source)); return@flow }
                }
            } catch (e: RateLimitedException) {
                intervalMs = e.retryAfterSec.coerceIn(1, 60) * 1000L // on ralentit au lieu d'insister
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Coupure passagère du réseau : on réessaie jusqu'à l'expiration du code plutôt que d'abandonner.
                if (e is CloudSyncException) { emit(WebSendEvent.Failed(e.message ?: "Network error")); return@flow }
            }
        }
        emit(WebSendEvent.Expired)
    }
}

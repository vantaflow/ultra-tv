package com.ultratv.tv.nativeapp.data.xmltv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Adresse de guide saisie par l'utilisateur : règles de forme (pures, sans réseau). */
object GuideAddress {
    enum class Problem { EMPTY, SCHEME, HOST, EXTENSION }

    sealed interface Validation {
        data class Ok(val url: String) : Validation
        data class Invalid(val problem: Problem) : Validation
    }

    /**
     * http:// ou https:// seulement, hôte non vide, chemin finissant par `.xml` ou `.xml.gz` (une query string ou un fragment
     * après le chemin est toléré, ex. `guide.xml.gz?token=…`).
     */
    fun validate(raw: String): Validation {
        val s = raw.trim()
        if (s.isEmpty()) return Validation.Invalid(Problem.EMPTY)
        if (!(s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true))) return Validation.Invalid(Problem.SCHEME)
        val uri = runCatching { URI(s) }.getOrNull() ?: return Validation.Invalid(Problem.HOST)
        if (uri.host.isNullOrBlank()) return Validation.Invalid(Problem.HOST)
        val path = (uri.path ?: "").lowercase()
        if (!(path.endsWith(".xml") || path.endsWith(".xml.gz"))) return Validation.Invalid(Problem.EXTENSION)
        return Validation.Ok(s)
    }

    /** Hôte seul, pour l'affichage : jamais l'adresse complète (elle peut contenir un identifiant ou un jeton). */
    fun hostOf(url: String): String = runCatching { URI(url.trim()).host }.getOrNull().orEmpty()
}

/** Comment l'adresse perso d'une source se combine avec le guide du fournisseur. */
enum class GuideMode {
    /** Seule l'adresse perso est utilisée. */
    REPLACE,

    /** Guide du fournisseur d'abord, puis l'adresse perso pour les chaînes restées sans programme. */
    COMPLETE;

    companion object {
        fun parse(s: String?): GuideMode = if (s.equals("REPLACE", true)) REPLACE else COMPLETE
    }
}

data class CustomGuide(val url: String, val mode: GuideMode)

/**
 * Ce que la synchro du guide d'une source doit faire. [replaceUrl] : seul guide à lire ; [completeUrl] : guide lu en plus,
 * pour les chaînes sans programme ; [hasFeed] : la source a au moins un guide (sinon la synchro du guide est sautée).
 */
data class GuidePlan(val replaceUrl: String?, val completeUrl: String?, val hasFeed: Boolean) {
    companion object {
        fun of(kind: String, providerFeeds: List<String>, custom: CustomGuide?): GuidePlan {
            val c = custom?.takeIf { GuideAddress.validate(it.url) is GuideAddress.Validation.Ok }
            val own = when (kind) {
                "XTREAM" -> true
                "M3U", "M3U_LOCAL" -> providerFeeds.isNotEmpty()
                else -> false   // Stalker : aucun guide XMLTV propre au portail
            }
            return when {
                c == null -> GuidePlan(null, null, own)
                c.mode == GuideMode.REPLACE -> GuidePlan(c.url.trim(), null, true)
                else -> GuidePlan(null, c.url.trim(), true)
            }
        }
    }
}

/** Verdict de la vérification d'une adresse de guide. */
sealed interface GuideCheck {
    data object Ok : GuideCheck
    data class Invalid(val problem: GuideAddress.Problem) : GuideCheck
    data object Unreachable : GuideCheck
    data class Http(val code: Int) : GuideCheck
    data object NotXmltv : GuideCheck
}

/**
 * Vérifie une adresse AVANT de l'enregistrer : forme, puis requête réseau lisant les premiers Ko (gzip détecté par ses
 * octets magiques) qui doivent contenir une balise `<tv`. Rien n'est journalisé (l'adresse peut contenir un jeton).
 */
@Singleton
class GuideChecker @Inject constructor(okBase: OkHttpClient) {
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun check(raw: String): GuideCheck = withContext(Dispatchers.IO) {
        val url = when (val v = GuideAddress.validate(raw)) {
            is GuideAddress.Validation.Invalid -> return@withContext GuideCheck.Invalid(v.problem)
            is GuideAddress.Validation.Ok -> v.url
        }
        val call = try {
            ok.newCall(Request.Builder().url(url).header("User-Agent", "UltraTV").build())
        } catch (e: IllegalArgumentException) {
            return@withContext GuideCheck.Invalid(GuideAddress.Problem.HOST)
        }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) return@withContext GuideCheck.Http(resp.code)
                val body = resp.body ?: return@withContext GuideCheck.NotXmltv
                val head = try {
                    val input = maybeGunzip(BufferedInputStream(body.byteStream(), 8 * 1024))
                    val buf = ByteArray(HEAD_BYTES)
                    var n = 0
                    while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                    String(buf, 0, n, Charsets.ISO_8859_1)
                } catch (e: java.util.zip.ZipException) {
                    return@withContext GuideCheck.NotXmltv   // gzip annoncé par ses octets magiques mais illisible
                }
                if (TV_TAG.containsMatchIn(head)) GuideCheck.Ok else GuideCheck.NotXmltv
            }
        } catch (e: IOException) {
            GuideCheck.Unreachable
        } finally {
            call.cancel()   // on ne lit que les premiers Ko : pas de téléchargement de 150 Mo pour rien
        }
    }

    private companion object {
        const val HEAD_BYTES = 16 * 1024
        val TV_TAG = Regex("<tv[\\s>]", RegexOption.IGNORE_CASE)
    }
}

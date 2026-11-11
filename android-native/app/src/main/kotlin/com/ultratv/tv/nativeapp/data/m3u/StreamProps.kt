package com.ultratv.tv.nativeapp.data.m3u

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/** Système de protection demandé par une playlist (`#KODIPROP:inputstream.adaptive.license_type=`). */
@Serializable
enum class DrmScheme {
    WIDEVINE,
    CLEARKEY,

    /** Autre système (PlayReady, FairPlay…) : reconnu comme protégé, jamais lu. */
    UNSUPPORTED,
}

/** Une clé ClearKey : [kid] et [key] en hexadécimal minuscule, 32 caractères (16 octets) chacun. */
@Serializable
data class ClearKeyPair(val kid: String, val key: String)

/**
 * Propriétés de lecture d'une chaîne protégée, lues dans la playlist M3U (lignes `#KODIPROP` et `#EXTVLCOPT`).
 *
 * SECRETS DE LECTURE : l'URL de licence, ses en-têtes et les clés ClearKey ne doivent JAMAIS être journalisés ni
 * envoyés hors de l'appareil. Elles restent dans la base locale (colonne `channel.props`), comme l'URL du flux ; la
 * synchro cloud ne transmet que l'historique et les favoris, et n'en transporte aucune. [toString] les masque.
 */
@Serializable
data class StreamProps(
    val scheme: DrmScheme? = null,
    val licenseUrl: String? = null,
    val licenseHeaders: Map<String, String> = emptyMap(),
    val clearKeys: List<ClearKeyPair> = emptyList(),
    /** `mpd` (DASH) ou `hls`, tel que déclaré par `manifest_type` ; null = d'après l'extension. */
    val manifestType: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
) {
    /** Vrai si la playlist déclare un système de protection : ces flux ne passent jamais par VLC ni par le bureau. */
    val isProtected: Boolean get() = scheme != null

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    override fun toString(): String = "StreamProps(scheme=$scheme, manifestType=$manifestType, <secrets masqués>)"

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /** Relit la colonne `channel.props` ; null si vide ou illisible (jamais d'exception : une valeur abîmée = flux ordinaire). */
        fun fromJson(s: String?): StreamProps? {
            if (s.isNullOrBlank()) return null
            return runCatching { JSON.decodeFromString(serializer(), s) }.getOrNull()
        }
    }
}

/**
 * Accumule les lignes de propriétés qui précèdent une URL de flux. `build()` rend null quand la chaîne n'est pas
 * protégée (les options VLC seules, user-agent / referrer, ne sont conservées que pour une chaîne protégée : elles ne
 * servent qu'à sa licence et à son manifeste, et le reste des flux garde son comportement actuel).
 */
class StreamPropsBuilder {
    private var licenseType: String? = null
    private var licenseKey: String? = null
    private var manifestType: String? = null
    private var userAgent: String? = null
    private var referrer: String? = null

    /** `#KODIPROP:clé=valeur` ; toute autre ligne ou clé inconnue est ignorée. */
    fun acceptKodiProp(line: String) {
        val body = line.substringAfter(':', "").trim()
        val eq = body.indexOf('=')
        if (eq <= 0) return
        val key = body.substring(0, eq).trim().lowercase()
        val value = body.substring(eq + 1).trim()
        when (key) {
            "inputstream.adaptive.license_type" -> licenseType = value.ifBlank { null }
            "inputstream.adaptive.license_key" -> licenseKey = value.ifBlank { null }
            "inputstream.adaptive.manifest_type" -> manifestType = value.lowercase().ifBlank { null }
        }
    }

    /** `#EXTVLCOPT:http-user-agent=…` / `http-referrer=…` (et l'ancienne graphie `http-referer`). */
    fun acceptVlcOpt(line: String) {
        val body = line.substringAfter(':', "").trim()
        val eq = body.indexOf('=')
        if (eq <= 0) return
        val value = body.substring(eq + 1).trim().ifBlank { null }
        when (body.substring(0, eq).trim().lowercase()) {
            "http-user-agent" -> userAgent = value
            "http-referrer", "http-referer" -> referrer = value
        }
    }

    fun build(): StreamProps? {
        val scheme = DrmParsing.scheme(licenseType) ?: return null
        val manifest = manifestType?.takeIf { it == "mpd" || it == "hls" }
        val base = StreamProps(scheme = scheme, manifestType = manifest, userAgent = userAgent, referrer = referrer)
        val key = licenseKey ?: return base
        return when (scheme) {
            DrmScheme.WIDEVINE -> {
                val (url, headers) = DrmParsing.licenseServer(key)
                base.copy(licenseUrl = url, licenseHeaders = headers)
            }
            DrmScheme.CLEARKEY -> when {
                DrmParsing.isUrl(key.substringBefore('|')) -> {
                    val (url, headers) = DrmParsing.licenseServer(key)
                    base.copy(licenseUrl = url, licenseHeaders = headers)
                }
                else -> base.copy(clearKeys = DrmParsing.clearKeys(key))
            }
            DrmScheme.UNSUPPORTED -> base
        }
    }
}

/** Analyse des valeurs de propriétés (pure, testable). Valeur invalide → résultat vide, jamais d'exception. */
object DrmParsing {
    private val HEX32 = Regex("^[0-9a-fA-F]{32}$")

    fun scheme(licenseType: String?): DrmScheme? = when (licenseType?.trim()?.lowercase()) {
        null, "" -> null
        "com.widevine.alpha" -> DrmScheme.WIDEVINE
        "org.w3.clearkey", "clearkey" -> DrmScheme.CLEARKEY
        else -> DrmScheme.UNSUPPORTED
    }

    fun isUrl(s: String): Boolean = s.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) }

    /**
     * Format Kodi `url|En-tête=valeur&Autre=valeur|données|réponse`. Seuls l'URL et les en-têtes sont repris : le corps
     * brut du défi (`R{SSM}`) est celui que Media3 envoie de toute façon. Valeurs encodées en pourcentage (le « + » reste un « + »).
     */
    fun licenseServer(raw: String): Pair<String?, Map<String, String>> {
        val parts = raw.split('|')
        val url = parts[0].trim().takeIf { isUrl(it) }
        val headers = LinkedHashMap<String, String>()
        parts.getOrNull(1)?.split('&')?.forEach { h ->
            val eq = h.indexOf('=')
            if (eq > 0) {
                val name = percentDecode(h.substring(0, eq)).trim()
                if (name.isNotEmpty()) headers[name] = percentDecode(h.substring(eq + 1)).trim()
            }
        }
        return url to headers
    }

    /** `kid:key` hexadécimaux séparés par des virgules, ou JSON `{"kid":"key"}` / JWK `{"keys":[…]}`. Vide si invalide. */
    fun clearKeys(raw: String): List<ClearKeyPair> {
        val s = raw.trim()
        return if (s.startsWith("{")) fromJson(s) else fromPairs(s)
    }

    private fun fromPairs(s: String): List<ClearKeyPair> {
        val out = ArrayList<ClearKeyPair>()
        for (p in s.split(',')) {
            val item = p.trim()
            if (item.isEmpty()) continue
            val c = item.indexOf(':')
            if (c <= 0) return emptyList()
            val kid = item.substring(0, c).trim().replace("-", "")
            val key = item.substring(c + 1).trim()
            if (!HEX32.matches(kid) || !HEX32.matches(key)) return emptyList()
            out += ClearKeyPair(kid.lowercase(), key.lowercase())
        }
        return out
    }

    private fun fromJson(s: String): List<ClearKeyPair> {
        val root = runCatching { Json.parseToJsonElement(s) }.getOrNull() as? JsonObject ?: return emptyList()
        val out = ArrayList<ClearKeyPair>()
        val keys = root["keys"]
        if (keys is JsonArray) {
            for (e in keys) {
                val o = e as? JsonObject ?: return emptyList()
                val kid = toHex16((o["kid"] as? JsonPrimitive)?.contentOrNull) ?: return emptyList()
                val k = toHex16((o["k"] as? JsonPrimitive)?.contentOrNull) ?: return emptyList()
                out += ClearKeyPair(kid, k)
            }
        } else {
            for ((kidRaw, v) in root) {
                val kid = toHex16(kidRaw) ?: return emptyList()
                val k = toHex16((v as? JsonPrimitive)?.contentOrNull) ?: return emptyList()
                out += ClearKeyPair(kid, k)
            }
        }
        return out
    }

    /** 16 octets en hexadécimal (32 caractères) ou en base64url (22 caractères) → hexadécimal minuscule ; null sinon. */
    fun toHex16(v: String?): String? {
        val s = v?.trim()?.replace("-", "")?.takeIf { it.isNotEmpty() } ?: return null
        if (HEX32.matches(s)) return s.lowercase()
        val bytes = runCatching { Base64.getUrlDecoder().decode(s.trimEnd('=')) }.getOrNull() ?: return null
        return if (bytes.size == 16) bytes.joinToString("") { "%02x".format(it) } else null
    }

    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val b = if (hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) hex.toInt(16) else null
                if (b != null) { out.write(b); i += 3; continue }
            }
            out.write(ch.toString().toByteArray(Charsets.UTF_8)); i++
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

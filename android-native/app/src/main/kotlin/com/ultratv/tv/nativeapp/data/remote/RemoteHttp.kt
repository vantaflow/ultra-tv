package com.ultratv.tv.nativeapp.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.URLDecoder
import java.security.MessageDigest

class HttpRequest(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String)

class HttpResponse(val status: Int, val contentType: String, val body: ByteArray, val extraHeaders: Map<String, String> = emptyMap()) {
    val reason: String get() = when (status) {
        200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
        413 -> "Payload Too Large"; 503 -> "Service Unavailable"; else -> "Error"
    }
    fun toBytes(): ByteArray {
        val head = StringBuilder("HTTP/1.1 $status $reason\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
        head.append("Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\n")
        extraHeaders.forEach { (k, v) -> head.append("$k: $v\r\n") }
        head.append("\r\n")
        return head.toString().toByteArray(Charsets.ISO_8859_1) + body
    }
}

/** Lecture d'une requête HTTP/1.1 minimale (pas de keep-alive, pas de chunked) avec des limites strictes. */
object HttpParser {
    const val MAX_HEAD = 8 * 1024
    const val MAX_BODY = 4 * 1024

    class Bad(val status: Int) : Exception()

    fun read(input: InputStream): HttpRequest {
        val head = readHead(input)
        val lines = head.split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size < 3) throw Bad(400)
        val headers = HashMap<String, String>()
        for (l in lines.drop(1)) { val i = l.indexOf(':'); if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len < 0 || len > MAX_BODY) throw Bad(413)
        val body = if (len == 0) "" else {
            val buf = ByteArray(len); var n = 0
            while (n < len) { val r = input.read(buf, n, len - n); if (r < 0) throw Bad(400); n += r }
            String(buf, Charsets.UTF_8)
        }
        val target = parts[1]
        return HttpRequest(parts[0].uppercase(), target.substringBefore('?'), parseQuery(target.substringAfter('?', "")), headers, body)
    }

    private fun readHead(input: InputStream): String {
        val sb = java.io.ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw Bad(400)
            sb.write(b)
            if (sb.size() > MAX_HEAD) throw Bad(413)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
        return String(sb.toByteArray(), Charsets.ISO_8859_1).trimEnd('\r', '\n')
    }

    fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (kv in raw.split('&')) {
            if (kv.isEmpty()) continue
            val k = decode(kv.substringBefore('='))
            if (k !in out) out[k] = decode(kv.substringAfter('=', ""))
        }
        return out
    }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault("")
}

/**
 * Routage et contrôle du jeton, sans réseau (testable). RIEN n'est servi sans jeton valide, pas même la page ni un 404 :
 * une requête sans jeton reçoit toujours 401, qu'elle vise une route connue ou non.
 * Le jeton est lu dans `?k=` ou dans l'en-tête `X-Key` (la page l'envoie en en-tête, donc hors des journaux d'URL).
 */
class RemoteRouter(private val token: () -> String, private val api: RemoteApi, private val lang: suspend () -> String) {

    suspend fun handle(req: HttpRequest): HttpResponse {
        val sent = req.headers["x-key"] ?: req.query["k"]
        if (!tokenOk(sent)) return json(401, JSONObject().put("error", "unauthorized"))
        return when (req.path) {
            "/r" -> if (req.method == "GET") page() else notAllowed()
            "/api/search" -> if (req.method == "GET") search(req) else notAllowed()
            "/api/now" -> if (req.method == "GET") now() else notAllowed()
            "/api/open" -> if (req.method == "POST") open(req) else notAllowed()
            "/api/control" -> if (req.method == "POST") control(req) else notAllowed()
            else -> json(404, JSONObject().put("error", "not_found"))
        }
    }

    private fun tokenOk(sent: String?): Boolean {
        if (sent.isNullOrEmpty()) return false
        val real = token()
        // Comparaison à temps constant.
        return MessageDigest.isEqual(sent.toByteArray(Charsets.UTF_8), real.toByteArray(Charsets.UTF_8))
    }

    private suspend fun page() = HttpResponse(
        200, "text/html; charset=utf-8", RemotePage.html(lang()).toByteArray(Charsets.UTF_8),
        mapOf("Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"),
    )

    private suspend fun search(req: HttpRequest): HttpResponse {
        val q = req.query["q"].orEmpty().trim().take(80)
        val kind = req.query["t"] ?: RemoteKinds.MOVIE
        if (kind !in RemoteKinds.all) return json(400, JSONObject().put("error", "bad_kind"))
        val hits = if (q.length < 2) emptyList() else runCatching { api.search(q, kind) }.getOrDefault(emptyList())
        return json(200, searchJson(hits))
    }

    private fun now(): HttpResponse {
        val n = api.now()
        return json(200, JSONObject()
            .put("title", n.title ?: JSONObject.NULL).put("kind", n.kind ?: JSONObject.NULL)
            .put("playing", n.playing ?: JSONObject.NULL).put("player", n.playerShown))
    }

    private suspend fun open(req: HttpRequest): HttpResponse {
        val o = runCatching { JSONObject(req.body) }.getOrNull() ?: return json(400, JSONObject().put("error", "bad_json"))
        val kind = o.optString("kind"); val id = o.optLong("id", -1)
        if (kind !in RemoteKinds.all || id < 0) return json(400, JSONObject().put("error", "bad_target"))
        val ok = runCatching { api.open(kind, id) }.getOrDefault(false)
        return json(if (ok) 200 else 404, JSONObject().put("ok", ok))
    }

    private fun control(req: HttpRequest): HttpResponse {
        val o = runCatching { JSONObject(req.body) }.getOrNull() ?: return json(400, JSONObject().put("error", "bad_json"))
        val action = RemoteCommands.parse(o.optString("cmd")) ?: return json(400, JSONObject().put("error", "bad_command"))
        api.control(action)
        return json(200, JSONObject().put("ok", true))
    }

    private fun notAllowed() = json(405, JSONObject().put("error", "method_not_allowed"))

    companion object {
        fun json(status: Int, o: JSONObject) = HttpResponse(status, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8))

        /** Réponse de `/api/search` : uniquement kind, id local, titre, année. */
        fun searchJson(hits: List<RemoteHit>): JSONObject = JSONObject().put("results", JSONArray().also { arr ->
            hits.forEach { h -> arr.put(JSONObject().put("kind", h.kind).put("id", h.id).put("title", h.title).also { if (h.year != null && h.year > 0) it.put("year", h.year) }) }
        })
    }
}

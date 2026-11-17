package com.ultratv.tv.nativeapp.data.nas

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Fichier ouvert en lecture aléatoire (SMB en production, mémoire en test). [read] rend le nombre d'octets lus, -1 en fin de fichier. */
interface NasFile : Closeable {
    val size: Long
    fun read(offset: Long, buf: ByteArray, off: Int, len: Int): Int
}

/** Partie pure du relais HTTP : en-tête Range, types de contenu, ligne de requête. */
object NasHttp {
    /** Plage demandée : [start]..[end] inclus. */
    data class Range(val start: Long, val end: Long) { val length: Long get() = end - start + 1 }

    sealed interface RangeResult {
        /** Pas d'en-tête Range (ou illisible) : tout le fichier. */
        data object Full : RangeResult
        data class Partial(val range: Range) : RangeResult
        /** Plage hors du fichier : 416. */
        data object Unsatisfiable : RangeResult
    }

    /** `bytes=a-b`, `bytes=a-`, `bytes=-n` ; plusieurs plages : la première seulement. Un en-tête incompréhensible vaut « tout le fichier ». */
    fun parseRange(header: String?, size: Long): RangeResult {
        val h = header?.trim() ?: return RangeResult.Full
        if (!h.startsWith("bytes=", ignoreCase = true)) return RangeResult.Full
        val spec = h.substring(6).substringBefore(',').trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return RangeResult.Full
        val a = spec.substring(0, dash).trim()
        val b = spec.substring(dash + 1).trim()
        if (a.isEmpty() && b.isEmpty()) return RangeResult.Full
        if (size <= 0) return RangeResult.Unsatisfiable
        if (a.isEmpty()) {
            // Les n derniers octets.
            val n = b.toLongOrNull() ?: return RangeResult.Full
            if (n <= 0) return RangeResult.Unsatisfiable
            return RangeResult.Partial(Range((size - n).coerceAtLeast(0), size - 1))
        }
        val start = a.toLongOrNull() ?: return RangeResult.Full
        val end = if (b.isEmpty()) size - 1 else (b.toLongOrNull() ?: return RangeResult.Full).coerceAtMost(size - 1)
        if (start >= size || start < 0 || end < start) return RangeResult.Unsatisfiable
        return RangeResult.Partial(Range(start, end))
    }

    fun contentType(ext: String): String = when (ext.lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "wmv" -> "video/x-ms-wmv"
        "webm" -> "video/webm"
        "ts", "m2ts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        else -> "application/octet-stream"
    }

    /** Ligne de requête `GET /chemin HTTP/1.1` → (méthode, chemin sans paramètres) ; null si malformée. */
    fun parseRequestLine(line: String): Pair<String, String>? {
        val p = line.trim().split(' ').filter { it.isNotEmpty() }
        if (p.size < 3 || !p[2].startsWith("HTTP/")) return null
        return p[0].uppercase() to p[1].substringBefore('?').substringBefore('#')
    }
}

/**
 * Petit serveur HTTP sur 127.0.0.1 qui relaie un fichier du NAS au lecteur (ExoPlayer comme VLC) : lecture par plages (`Range`),
 * donc recherche instantanée dans le film. Choisi plutôt qu'un `DataSource` maison parce qu'il sert les DEUX moteurs de lecture
 * de l'appli, sans code propre à chacun.
 *
 * Sécurité : écoute en boucle locale seulement ; chaque adresse porte un jeton aléatoire propre au processus (une autre appli
 * du téléphone ne peut pas deviner l'adresse) ; aucun identifiant n'y figure. Les chemins sont validés (jamais de « .. »).
 */
class NasRelay(private val open: (providerId: Long, path: List<String>) -> NasFile?) {
    private val token: String = ByteArray(18).also { SecureRandom().nextBytes(it) }.let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "nas-relay").also { it.isDaemon = true } }
    @Volatile private var server: ServerSocket? = null

    /** Port d'écoute, une fois démarré. */
    val port: Int get() = server?.localPort ?: 0

    @Synchronized
    fun start(): Int {
        server?.let { return it.localPort }
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server = s
        pool.execute {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: IOException) { break }
                pool.execute { serve(c) }
            }
        }
        return s.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    /** Adresse de lecture d'un fichier ; l'extension aide le lecteur à choisir son extracteur. */
    fun urlFor(providerId: Long, path: List<String>): String {
        val p = start()
        val ext = NasNames.extensionOf(path.last()).takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() } && it.length <= 5 }
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(path.joinToString("/").toByteArray(Charsets.UTF_8))
        return "http://127.0.0.1:$p/$token/$providerId/$b64" + (ext?.let { ".$it" } ?: "")
    }

    private fun parsePath(p: String): Pair<Long, List<String>>? {
        val segs = p.split('/')
        if (segs.size != 4 || segs[0].isNotEmpty() || segs[1] != token) return null
        val pid = segs[2].toLongOrNull() ?: return null
        val b64 = segs[3].substringBeforeLast('.', segs[3]).let { if (it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }) it else return null }
        val text = runCatching { String(java.util.Base64.getUrlDecoder().decode(b64), Charsets.UTF_8) }.getOrNull() ?: return null
        val path = text.split('/').filter { it.isNotEmpty() }
        if (path.isEmpty() || path.any { it == ".." || it == "." }) return null
        return pid to path
    }

    private fun serve(sock: Socket) {
        sock.use { s ->
            try {
                s.soTimeout = 30_000
                s.tcpNoDelay = true
                val input = BufferedInputStream(s.getInputStream())
                val out = s.getOutputStream()
                val reqLine = readLine(input) ?: return
                val headers = HashMap<String, String>()
                while (true) {
                    val l = readLine(input) ?: break
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }
                val (method, path) = NasHttp.parseRequestLine(reqLine) ?: return respond(out, 400)
                if (method != "GET" && method != "HEAD") return respond(out, 405)
                val (pid, rel) = parsePath(path) ?: return respond(out, 404)
                val file = try { open(pid, rel) } catch (_: Exception) { null } ?: return respond(out, 404)
                file.use { f -> sendFile(out, f, method == "HEAD", headers["range"], NasNames.extensionOf(rel.last())) }
            } catch (_: IOException) {
                // Le lecteur a fermé la connexion (changement de position, arrêt) : normal.
            } catch (_: RuntimeException) {
                // Erreur SMB en cours de lecture : la connexion se coupe, le lecteur redemandera la plage.
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
        return null
    }

    private fun respond(out: OutputStream, code: Int, extra: String = "") {
        val text = when (code) { 200 -> "OK"; 206 -> "Partial Content"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 416 -> "Range Not Satisfiable"; else -> "Error" }
        out.write("HTTP/1.1 $code $text\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun sendFile(out: OutputStream, f: NasFile, headOnly: Boolean, rangeHeader: String?, ext: String) {
        val size = f.size
        val (code, range) = when (val r = NasHttp.parseRange(rangeHeader, size)) {
            NasHttp.RangeResult.Full -> 200 to NasHttp.Range(0, size - 1)
            is NasHttp.RangeResult.Partial -> 206 to r.range
            NasHttp.RangeResult.Unsatisfiable -> return respond(out, 416, "Content-Range: bytes */$size\r\n")
        }
        val head = StringBuilder("HTTP/1.1 $code ${if (code == 206) "Partial Content" else "OK"}\r\n")
        head.append("Content-Type: ${NasHttp.contentType(ext)}\r\nAccept-Ranges: bytes\r\n")
        if (code == 206) head.append("Content-Range: bytes ${range.start}-${range.end}/$size\r\n")
        head.append("Content-Length: ${if (size == 0L) 0 else range.length}\r\nConnection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (headOnly || size == 0L) { out.flush(); return }
        val buf = ByteArray(256 * 1024)
        var pos = range.start
        var left = range.length
        while (left > 0) {
            val n = f.read(pos, buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) break
            out.write(buf, 0, n)
            pos += n
            left -= n
        }
        out.flush()
    }
}

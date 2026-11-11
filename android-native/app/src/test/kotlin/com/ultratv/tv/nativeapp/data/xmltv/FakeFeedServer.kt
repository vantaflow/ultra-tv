package com.ultratv.tv.nativeapp.data.xmltv

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Faux serveur de guides XMLTV EN MÉMOIRE (aucun socket, aucun réseau) : un intercepteur OkHttp qui sert des routes
 * (suffixe de chemin) avec des comportements de serveur IPTV réels : débit limité, connexion qui « cale » (ouverte,
 * sans aucune donnée), codes d'erreur sur les N premières requêtes. Aucune donnée réelle (hôte `.invalid`).
 */
class FakeFeedServer {
    class Route(
        /** Ouvre le contenu (un flux neuf par requête). */
        val open: () -> InputStream,
        /** Taille annoncée (Content-Length) ; -1 = inconnue. */
        val length: Long = -1,
        /** Débit en octets/s (0 = instantané). */
        var bytesPerSecond: Long = 0,
        /** Après ce nombre d'octets, la connexion reste ouverte sans rien envoyer (-1 = jamais). */
        var stallAfterBytes: Long = -1,
        /** Les [failFirst] premières requêtes reçoivent [failCode]. */
        var failFirst: Int = 0,
        var failCode: Int = 503,
        /** Coupe la connexion (EOF prématuré) après ce nombre d'octets, pour les [truncateFirst] premières requêtes. */
        var truncateAfterBytes: Long = -1,
        var truncateFirst: Int = 0,
    ) { val hits = java.util.concurrent.atomic.AtomicInteger() }

    val routes = LinkedHashMap<String, Route>()
    val requests = CopyOnWriteArrayList<String>()
    val servedBytes = AtomicLong()
    @Volatile private var closedAll = false
    private val open = CopyOnWriteArrayList<Stream>()

    fun add(suffix: String, bytes: ByteArray, bytesPerSecond: Long = 0, stallAfterBytes: Long = -1): Route =
        Route({ ByteArrayInputStream(bytes) }, bytes.size.toLong(), bytesPerSecond, stallAfterBytes).also { routes[suffix] = it }

    fun add(suffix: String, file: File, bytesPerSecond: Long = 0, stallAfterBytes: Long = -1): Route =
        Route({ file.inputStream() }, file.length(), bytesPerSecond, stallAfterBytes).also { routes[suffix] = it }

    /** Débloque tous les flux calés (fin de test). */
    fun closeAll() { closedAll = true; open.forEach { runCatching { it.close() } } }

    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val path = req.url.encodedPath
        requests += req.url.toString()
        val route = routes.entries.firstOrNull { path.endsWith(it.key) }?.value
        fun status(code: Int) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(ResponseBody.create("text/plain".toMediaType(), "")).build()
        if (route == null) return@Interceptor status(404)
        val n = route.hits.incrementAndGet()
        if (n <= route.failFirst) return@Interceptor status(route.failCode)
        val truncate = if (n <= route.truncateFirst) route.truncateAfterBytes else -1
        val s = Stream(route.open(), route.bytesPerSecond, if (truncate >= 0) -1 else route.stallAfterBytes, truncate).also { open += it }
        val buffered = s.source().buffer()
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("ok")
            .body(object : ResponseBody() {
                override fun contentType() = "application/xml".toMediaType()
                override fun contentLength() = route.length
                override fun source() = buffered
            }).build()
    }

    private inner class Stream(input: InputStream, private val bps: Long, private val stallAfter: Long, private val truncateAfter: Long) : FilterInputStream(input) {
        private var sent = 0L
        private val t0 = System.nanoTime()
        @Volatile private var closed = false

        override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed || closedAll) throw IOException("closed")
            if (truncateAfter >= 0 && sent >= truncateAfter) return -1
            if (stallAfter >= 0 && sent >= stallAfter) {
                // Connexion ouverte, aucune donnée : on attend la fermeture par le client.
                while (!closed && !closedAll) Thread.sleep(20)
                throw IOException("closed")
            }
            var max = minOf(len, 64 * 1024)
            if (stallAfter >= 0) max = minOf(max.toLong(), stallAfter - sent).toInt()
            if (truncateAfter >= 0) max = minOf(max.toLong(), truncateAfter - sent).toInt()
            val n = super.read(b, off, max)
            if (n > 0) {
                sent += n; servedBytes.addAndGet(n.toLong())
                if (bps > 0) {
                    val due = sent * 1_000_000_000L / bps - (System.nanoTime() - t0)
                    if (due > 1_000_000) Thread.sleep(due / 1_000_000, (due % 1_000_000).toInt())
                }
            }
            return n
        }

        override fun close() { closed = true; runCatching { super.close() } }
    }
}

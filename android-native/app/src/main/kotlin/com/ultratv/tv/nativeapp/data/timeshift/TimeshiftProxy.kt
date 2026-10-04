package com.ultratv.tv.nativeapp.data.timeshift

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Relais local qui DUPLIQUE le flux direct dans un [CircularFileBuffer] et le ressert au lecteur depuis la position
 * voulue. Le fournisseur n'autorise qu'une connexion : c'est ce relais qui la tient, le lecteur ne parle qu'à 127.0.0.1.
 * Pause, ±30 s et retour au direct deviennent de simples positions dans le tampon, identiques pour Media3 et LibVLC.
 *
 * Réservé aux flux continus MPEG-TS (le HLS a sa propre fenêtre de direct). L'URL amont n'est jamais journalisée.
 */
class TimeshiftProxy(
    private val client: OkHttpClient,
    private val upstreamUrl: String,
    private val userAgent: String,
    bufferFile: File,
    capacity: Long,
    assumedBytesPerSec: Long,
) : AutoCloseable {
    val buffer = CircularFileBuffer(bufferFile, capacity)
    private val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
    /** Jeton aléatoire dans le chemin : une autre appli de l'appareil ne peut pas deviner l'adresse. */
    private val token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val stopped = AtomicBoolean(false)
    private val bps = AtomicLong(assumedBytesPerSec)
    /** Rend chaque URL distincte : le lecteur relance bien la lecture même pour une position identique. */
    private val nonce = AtomicLong()
    @Volatile var upstreamFailed = false; private set
    @Volatile private var client0: Socket? = null
    @Volatile private var servedPos = 0L
    @Volatile private var call: okhttp3.Call? = null

    /** Débit mesuré du flux (octets/s), repli sur l'hypothèse tant qu'il n'y a rien. */
    val bytesPerSec: Long get() = bps.get()
    /** Position absolue envoyée au lecteur (légèrement en avance sur ce qu'il affiche : sa propre mémoire tampon). */
    val playPos: Long get() = servedPos

    /** URL locale ; `from` = position absolue de départ, null = le direct. */
    fun localUrl(from: Long? = null): String =
        "http://127.0.0.1:${server.localPort}/ts/$token?o=${from ?: buffer.end}&n=${nonce.incrementAndGet()}"

    fun start() {
        Thread({ pump() }, "timeshift-pump").apply { isDaemon = true }.start()
        Thread({ acceptLoop() }, "timeshift-accept").apply { isDaemon = true }.start()
    }

    private fun pump() {
        try {
            val req = Request.Builder().url(upstreamUrl).header("User-Agent", userAgent).build()
            val c = client.newCall(req).also { call = it }
            c.execute().use { resp ->
                if (!resp.isSuccessful) { upstreamFailed = true; return }
                val src = resp.body?.byteStream() ?: run { upstreamFailed = true; return }
                val chunk = ByteArray(64 * 1024)
                var aligned = false
                val pending = java.io.ByteArrayOutputStream()
                var t0 = System.nanoTime(); var bytesSinceT0 = 0L
                while (!stopped.get()) {
                    val n = src.read(chunk)
                    if (n < 0) break
                    if (!aligned) {
                        pending.write(chunk, 0, n)
                        val all = pending.toByteArray()
                        val s = TimeshiftMath.firstSync(all, all.size)
                        if (s >= 0) { buffer.write(all, s, all.size - s); aligned = true }
                        else if (all.size > 512 * 1024) { upstreamFailed = true; return }     // pas du MPEG-TS
                    } else buffer.write(chunk, 0, n)
                    bytesSinceT0 += n
                    val dt = System.nanoTime() - t0
                    if (dt >= 2_000_000_000L) {
                        val measured = bytesSinceT0 * 1_000_000_000L / dt
                        if (measured > 0) bps.set(((bps.get() * 0.5) + (measured * 0.5)).toLong().coerceAtLeast(1))
                        t0 = System.nanoTime(); bytesSinceT0 = 0
                    }
                }
                if (!stopped.get()) upstreamFailed = true      // flux coupé par le fournisseur
            }
        } catch (_: Exception) {
            if (!stopped.get()) upstreamFailed = true
        }
    }

    private fun acceptLoop() {
        while (!stopped.get()) {
            val s = try { server.accept() } catch (_: Exception) { return }
            // Un seul client à la fois : un nouveau lecteur (saut) remplace l'ancien.
            client0?.let { runCatching { it.close() } }
            client0 = s
            Thread({ serve(s) }, "timeshift-serve").apply { isDaemon = true }.start()
        }
    }

    private fun serve(s: Socket) {
        try {
            val input = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = input.readLine() ?: return
            while (true) { val l = input.readLine() ?: break; if (l.isEmpty()) break }
            val target = requestLine.split(' ').getOrNull(1) ?: return
            if (!target.startsWith("/ts/$token")) { s.getOutputStream().write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray()); return }
            val from = target.substringAfter("?o=", "").substringBefore('&').toLongOrNull()
            val out = s.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: video/MP2T\r\nConnection: close\r\n\r\n".toByteArray())
            var pos = TimeshiftMath.align(from ?: buffer.end)
            servedPos = pos
            val buf = ByteArray(64 * 1024)
            while (!stopped.get() && s === client0) {
                if (pos < buffer.start) pos = TimeshiftMath.align(buffer.start) + TimeshiftMath.TS_PACKET   // écrasé : on se recale sur le plus ancien
                val n = buffer.read(pos, buf, 0, buf.size)
                if (n < 0) { if (buffer.isClosed) return; continue }
                if (n == 0) {
                    if (upstreamFailed && pos >= buffer.end) return      // plus rien à venir : EOF pour le lecteur
                    buffer.awaitData(pos, 500); continue
                }
                out.write(buf, 0, n)
                pos += n; servedPos = pos
            }
        } catch (_: Exception) {
        } finally { runCatching { s.close() } }
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { call?.cancel() }
        runCatching { server.close() }
        client0?.let { runCatching { it.close() } }
        buffer.close()                // supprime le fichier
    }
}

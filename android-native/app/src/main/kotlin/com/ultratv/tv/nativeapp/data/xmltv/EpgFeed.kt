package com.ultratv.tv.nativeapp.data.xmltv

import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import com.ultratv.tv.nativeapp.data.net.NetErrors
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Limites du téléchargement d'un guide. Un guide de 150 Mo sur une box faible peut être lent, mais jamais infini.
 *  - [connectMs] : établissement de la connexion ;
 *  - [stallMs] : durée maximale d'UNE lecture sans aucune donnée (connexion ouverte qui « cale ») ;
 *  - [totalMs] : durée totale d'une tentative (téléchargement + analyse + écriture) ;
 *  - [minBytesPerSecond] : débit minimal (mesuré sur le temps réellement passé à attendre le réseau, après [slowGraceMs]) ;
 *  - [maxAttempts] / [backoffMs] : tentatives (au total) et attente avant chacune des suivantes.
 */
data class EpgLimits(
    val connectMs: Long = 10_000,
    val stallMs: Long = 30_000,
    val totalMs: Long = 20 * 60_000L,
    val minBytesPerSecond: Long = 2_048,
    val slowGraceMs: Long = 60_000,
    val maxAttempts: Int = 3,
    val backoffMs: List<Long> = listOf(2_000, 6_000),
) {
    fun backoff(attempt: Int): Long = backoffMs.getOrElse(attempt - 1) { backoffMs.lastOrNull() ?: 0 }
}

/** Délai dépassé : [kind] dit lequel. Hérite de [SocketTimeoutException] : classé « TIMEOUT » par [NetErrors]. */
class EpgTimeoutException(val kind: Kind, message: String) : SocketTimeoutException(message) {
    enum class Kind { STALL, TOTAL, TOO_SLOW }
}

/** Progression du téléchargement : octets lus (compressés si gzip), taille annoncée (-1 si inconnue), programmes écrits. */
data class EpgProgress(val bytesRead: Long, val totalBytes: Long, val programmes: Int) {
    /** 0..100, ou null quand la taille n'est pas connue. */
    val percent: Int? get() = if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt().coerceIn(0, 100) else null
}

/** État du guide, publié sur le SyncStatusBus : lu par le guide (chargement, « Guide indisponible ») et la première synchro. */
sealed interface EpgState {
    data object Idle : EpgState
    data class Loading(val providerId: Long, val progress: EpgProgress) : EpgState
    data class Failed(val providerId: Long, val kind: SyncErrorKind, val reason: EpgFailReason) : EpgState
}

/** Raison courte affichée à l'utilisateur ; le détail technique part dans les journaux nettoyés. */
enum class EpgFailReason { TIMEOUT, NETWORK, SERVER, AUTH, NOT_FOUND, BAD_FORMAT, EMPTY, OTHER }

internal object EpgErrors {
    fun reason(t: Throwable): EpgFailReason {
        var cur: Throwable? = t; var depth = 0
        while (cur != null && depth++ < 8) {
            if (cur is org.xmlpull.v1.XmlPullParserException) return EpgFailReason.BAD_FORMAT
            if (cur is EmptyGuideException) return EpgFailReason.EMPTY
            cur = cur.cause
        }
        return when (NetErrors.classify(t)) {
            SyncErrorKind.TIMEOUT -> EpgFailReason.TIMEOUT
            SyncErrorKind.HOST_NOT_FOUND, SyncErrorKind.UNREACHABLE, SyncErrorKind.TLS -> EpgFailReason.NETWORK
            SyncErrorKind.SERVER_ERROR -> EpgFailReason.SERVER
            SyncErrorKind.UNAUTHORIZED, SyncErrorKind.PROVIDER_BLOCKED -> EpgFailReason.AUTH
            SyncErrorKind.NOT_FOUND -> EpgFailReason.NOT_FOUND
            SyncErrorKind.OTHER -> EpgFailReason.OTHER
        }
    }

    /** Une nouvelle tentative a-t-elle une chance de réussir ? Réseau, délai, 5xx, 408, 429 : oui. 4xx, XML invalide : non. */
    fun retryable(t: Throwable): Boolean {
        var cur: Throwable? = t; var depth = 0
        while (cur != null && depth++ < 8) {
            when (cur) {
                is HttpStatusException -> return cur.code in 500..599 || cur.code == 408 || cur.code == 429
                is org.xmlpull.v1.XmlPullParserException -> return false
                is EmptyGuideException -> return false
                is java.net.UnknownHostException -> return true
            }
            cur = cur.cause
        }
        return t is IOException
    }

    /**
     * Texte de journal : type de l'erreur et code HTTP éventuel, JAMAIS de message brut (OkHttp y met parfois l'adresse
     * complète, identifiants compris). RemoteLog le nettoie en plus avec LogSanitizer.
     */
    fun logLine(t: Throwable, attempts: Int): String {
        val http = generateSequence(t) { it.cause }.take(8).filterIsInstance<HttpStatusException>().firstOrNull()?.code
        val to = generateSequence(t) { it.cause }.take(8).filterIsInstance<EpgTimeoutException>().firstOrNull()?.kind
        return "guide: échec (${reason(t)}${if (http != null) ", HTTP $http" else ""}${if (to != null) ", délai $to" else ""}, ${t.javaClass.simpleName}) après $attempts tentative(s)"
    }
}

/** Le flux était lisible mais ne contenait aucun programme exploitable pour les chaînes présentes. */
class EmptyGuideException : IOException("guide vide")

/**
 * Flux de lecture surveillé : compte les octets, et un chien de garde ferme la connexion ([onAbort]) si UNE lecture reste
 * sans donnée plus de [EpgLimits.stallMs], si le débit utile tombe sous [EpgLimits.minBytesPerSecond], ou si la tentative
 * dépasse [EpgLimits.totalMs]. La lecture bloquée échoue alors avec [EpgTimeoutException]. Le temps passé par l'appelant à
 * écrire en base entre deux lectures ne compte PAS comme un calage.
 */
internal class GuardedInput(
    private val src: InputStream,
    private val limits: EpgLimits,
    private val onAbort: () -> Unit,
    private val onBytes: (Long) -> Unit = {},
    /** Taille annoncée : un flux qui s'arrête avant est tronqué (erreur, donc nouvelle tentative), pas fini. */
    private val expectedBytes: Long = -1,
    private val nanoTime: () -> Long = System::nanoTime,
) : InputStream() {
    @Volatile private var abort: EpgTimeoutException? = null
    @Volatile private var readingSince = 0L
    @Volatile private var netNanos = 0L
    @Volatile var bytes = 0L; private set
    private val startedAt = nanoTime()
    private var watch: ScheduledFuture<*>? = null

    init {
        val period = (minOf(limits.stallMs, limits.totalMs) / 4).coerceIn(20L, 1_000L)
        watch = scheduler.scheduleWithFixedDelay({ check() }, period, period, TimeUnit.MILLISECONDS)
    }

    private fun ms(nanos: Long) = nanos / 1_000_000

    private fun check() {
        if (abort != null) return
        val now = nanoTime()
        val rs = readingSince
        val inRead = if (rs != 0L) now - rs else 0L
        val e = when {
            ms(now - startedAt) > limits.totalMs -> EpgTimeoutException(EpgTimeoutException.Kind.TOTAL, "durée totale dépassée")
            rs != 0L && ms(inRead) > limits.stallMs -> EpgTimeoutException(EpgTimeoutException.Kind.STALL, "aucune donnée reçue")
            ms(netNanos + inRead) > limits.slowGraceMs && bytes * 1000 / maxOf(1L, ms(netNanos + inRead)) < limits.minBytesPerSecond ->
                EpgTimeoutException(EpgTimeoutException.Kind.TOO_SLOW, "débit trop faible")
            else -> null
        } ?: return
        abort = e
        runCatching { onAbort() }
    }

    private inline fun <T> guarded(block: () -> T): T {
        abort?.let { throw it }
        readingSince = nanoTime()
        try {
            return block()
        } catch (e: IOException) {
            throw abort ?: e
        } finally {
            val t = nanoTime(); netNanos += t - readingSince; readingSince = 0
            abort?.let { throw it }
        }
    }

    private fun eof(n: Int): Int {
        if (n < 0 && expectedBytes > 0 && bytes < expectedBytes) throw java.io.EOFException("flux tronqué ($bytes / $expectedBytes octets)")
        return n
    }

    override fun read(): Int = eof(guarded { src.read() }.also { if (it >= 0) { bytes++; onBytes(bytes) } })

    override fun read(b: ByteArray, off: Int, len: Int): Int = eof(guarded { src.read(b, off, len) }.also { if (it > 0) { bytes += it; onBytes(bytes) } })

    override fun available(): Int = runCatching { src.available() }.getOrDefault(0)

    override fun close() {
        watch?.cancel(false); watch = null
        runCatching { src.close() }
    }

    companion object {
        private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "epg-watchdog").apply { isDaemon = true }
        }
    }
}

/** Détecte le gzip par ses deux octets magiques (le serveur ne l'annonce pas toujours) et décompresse EN FLUX. */
internal fun maybeGunzip(raw: InputStream): InputStream {
    val b = if (raw.markSupported()) raw as BufferedInputStream else BufferedInputStream(raw, 64 * 1024)
    b.mark(2)
    val gz = b.read() == 0x1f && b.read() == 0x8b
    b.reset()
    return if (gz) BufferedInputStream(GZIPInputStream(b, 64 * 1024), 64 * 1024) else b
}

/** `url-tvg` / `x-tvg-url` d'un en-tête `#EXTM3U` : une ou plusieurs adresses séparées par des virgules ; http(s) seulement. */
fun parseTvgUrls(header: String): List<String> {
    val attr = Regex("""(?:^|\s)(url-tvg|x-tvg-url|tvg-url)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""", RegexOption.IGNORE_CASE)
    return attr.findAll(header).flatMap { m -> (m.groups[2] ?: m.groups[3] ?: m.groups[4])?.value.orEmpty().split(',').asSequence() }
        .map { it.trim() }.filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
        .distinct().take(5).toList()
}

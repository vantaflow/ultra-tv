package com.ultratv.tv.nativeapp.data.timeshift

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Tampon disque CIRCULAIRE d'un flux continu. Les positions sont ABSOLUES (nombre d'octets écrits depuis le début) :
 * la fenêtre lisible est [[start], [end]) avec `end - start <= capacity`. Au-delà, les plus anciens octets sont écrasés.
 * Un écrivain (le flux entrant), un lecteur à la fois (le client local) ; les lecteurs peuvent attendre de nouvelles données.
 */
class CircularFileBuffer(val file: File, val capacity: Long) : AutoCloseable {
    init { require(capacity > 0) }

    private val raf = RandomAccessFile(file, "rw")
    private val lock = ReentrantLock()
    private val more = lock.newCondition()
    private var written = 0L
    private var closed = false

    /** Position absolue du premier octet encore lisible. */
    val start: Long get() = lock.withLock { maxOf(0L, written - capacity) }
    /** Position absolue après le dernier octet écrit (le « direct »). */
    val end: Long get() = lock.withLock { written }
    val isClosed: Boolean get() = lock.withLock { closed }

    fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        lock.withLock {
            if (closed) return
            var o = off; var left = len
            while (left > 0) {
                val pos = written % capacity
                val n = minOf(left.toLong(), capacity - pos).toInt()
                raf.seek(pos); raf.write(b, o, n)
                written += n; o += n; left -= n
            }
            more.signalAll()
        }
    }

    /**
     * Lit jusqu'à [len] octets à la position absolue [pos]. Renvoie le nombre d'octets lus, 0 si [pos] == end (rien de
     * neuf), -1 si [pos] a déjà été écrasé (< start) ou si le tampon est fermé.
     */
    fun read(pos: Long, dst: ByteArray, off: Int, len: Int): Int = lock.withLock {
        if (closed || pos < maxOf(0L, written - capacity)) return -1
        val avail = (written - pos).coerceAtLeast(0)
        if (avail == 0L) return 0
        var total = 0; var p = pos; var o = off
        var left = minOf(len.toLong(), avail).toInt()
        while (left > 0) {
            val fp = p % capacity
            val n = minOf(left.toLong(), capacity - fp).toInt()
            raf.seek(fp); raf.readFully(dst, o, n)
            p += n; o += n; left -= n; total += n
        }
        total
    }

    /** Attend qu'il y ait des octets à lire à [pos] (ou la fermeture). Vrai si des données sont disponibles. */
    fun awaitData(pos: Long, timeoutMs: Long): Boolean = lock.withLock {
        var nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!closed && written <= pos && nanos > 0) nanos = more.awaitNanos(nanos)
        !closed && written > pos
    }

    /** Ferme et SUPPRIME le fichier du tampon. */
    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            more.signalAll()
            runCatching { raf.close() }
            runCatching { file.delete() }
        }
    }
}

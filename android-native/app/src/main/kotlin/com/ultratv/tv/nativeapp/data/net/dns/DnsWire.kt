package com.ultratv.tv.nativeapp.data.net.dns

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress

/**
 * Format binaire DNS (RFC 1035), réduit au strict nécessaire : une question A ou AAAA, et la lecture des
 * enregistrements A / AAAA d'une réponse (les CNAME intermédiaires sont ignorés, un résolveur récursif
 * renvoie toujours les adresses finales). Toute incohérence lève une IOException : jamais de lecture hors limites.
 */
object DnsWire {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val CLASS_IN = 1
    const val RCODE_NXDOMAIN = 3

    class Answer(val id: Int, val rcode: Int, val truncated: Boolean, val addresses: List<InetAddress>, val minTtlSeconds: Long?)

    /** Requête récursive (RD) pour [host] ; [host] doit être en ASCII (OkHttp fournit déjà le punycode). */
    fun query(id: Int, host: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream(32 + host.length)
        out.u16(id); out.u16(0x0100); out.u16(1); out.u16(0); out.u16(0); out.u16(0)
        writeName(out, host)
        out.u16(type); out.u16(CLASS_IN)
        return out.toByteArray()
    }

    fun writeName(out: ByteArrayOutputStream, host: String) {
        val name = host.trimEnd('.')
        if (name.isEmpty() || name.length > 253) throw IOException("nom DNS invalide")
        for (label in name.split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            if (b.isEmpty() || b.size > 63 || label.any { it.code > 0x7E || it.code <= 0x20 }) throw IOException("étiquette DNS invalide")
            out.write(b.size); out.write(b)
        }
        out.write(0)
    }

    fun parse(buf: ByteArray, len: Int = buf.size, host: String? = null): Answer {
        val r = Reader(buf, len)
        val id = r.u16()
        val flags = r.u16()
        if (flags and 0x8000 == 0) throw IOException("pas une réponse DNS")
        val qd = r.u16(); val an = r.u16(); r.u16(); r.u16()
        repeat(qd) { r.skipName(); r.skip(4) }
        val addrs = ArrayList<InetAddress>(an)
        var minTtl: Long? = null
        repeat(an) {
            r.skipName()
            val type = r.u16(); r.u16()
            val ttl = r.u32()
            val rdLen = r.u16()
            val start = r.pos
            r.skip(rdLen)
            val size = when (type) { TYPE_A -> 4; TYPE_AAAA -> 16; else -> -1 }
            if (size > 0 && rdLen == size) {
                addrs += InetAddress.getByAddress(host, buf.copyOfRange(start, start + size))
                minTtl = minOf(minTtl ?: ttl, ttl)
            }
        }
        return Answer(id, flags and 0xF, flags and 0x0200 != 0, addrs, minTtl)
    }

    private fun ByteArrayOutputStream.u16(v: Int) { write((v shr 8) and 0xFF); write(v and 0xFF) }

    private class Reader(val b: ByteArray, val len: Int) {
        var pos = 0
        private fun need(n: Int) { if (n < 0 || pos + n > len) throw IOException("réponse DNS tronquée") }
        fun u8(): Int { need(1); return b[pos++].toInt() and 0xFF }
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Long = (u16().toLong() shl 16) or u16().toLong()
        fun skip(n: Int) { need(n); pos += n }
        /** Nom éventuellement compressé : un pointeur (2 octets) termine le nom dans le flux courant. */
        fun skipName() {
            var guard = 0
            while (true) {
                if (++guard > 128) throw IOException("nom DNS en boucle")
                val l = u8()
                when {
                    l == 0 -> return
                    l and 0xC0 == 0xC0 -> { u8(); return }
                    l and 0xC0 != 0 -> throw IOException("étiquette DNS inconnue")
                    else -> skip(l)
                }
            }
        }
    }
}

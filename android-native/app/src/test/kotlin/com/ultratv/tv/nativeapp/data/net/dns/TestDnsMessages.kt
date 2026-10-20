package com.ultratv.tv.nativeapp.data.net.dns

import java.io.ByteArrayOutputStream
import java.net.InetAddress

/** Fabrique de réponses DNS pour les tests (nom de la réponse compressé vers la question, 0xC00C). */
object TestDnsMessages {
    fun id(query: ByteArray): Int = ((query[0].toInt() and 0xFF) shl 8) or (query[1].toInt() and 0xFF)
    fun qtype(query: ByteArray): Int = ((query[query.size - 4].toInt() and 0xFF) shl 8) or (query[query.size - 3].toInt() and 0xFF)

    fun response(query: ByteArray, addrs: List<String>, ttl: Long = 300, rcode: Int = 0, truncated: Boolean = false, idOverride: Int? = null): ByteArray {
        val type = qtype(query)
        val records = addrs.map { InetAddress.getByName(it).address }.filter { (type == DnsWire.TYPE_A && it.size == 4) || (type == DnsWire.TYPE_AAAA && it.size == 16) }
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }
        u16(idOverride ?: id(query))
        u16(0x8180 or rcode or (if (truncated) 0x0200 else 0))
        u16(1); u16(if (truncated) 0 else records.size); u16(0); u16(0)
        out.write(query, 12, query.size - 12)
        if (!truncated) for (r in records) {
            u16(0xC00C); u16(type); u16(1)
            u16(((ttl shr 16) and 0xFFFF).toInt()); u16((ttl and 0xFFFF).toInt())
            u16(r.size); out.write(r)
        }
        return out.toByteArray()
    }
}

package com.ultratv.tv.nativeapp.data.sync

import java.io.ByteArrayOutputStream

/**
 * Petit flux direct MPEG-TS SYNTHÉTIQUE, généré à la volée (aucun fichier, aucun contenu protégé) : une PMT annonçant
 * de la vidéo H.264, de l'audio AAC, des sous-titres DVB et du télétexte, et des images H.264 dont les SEI portent des
 * sous-titres CEA-608 (norme ATSC A/53, « GA94 »). Les images elles-mêmes sont factices (pas décodables) : le flux sert
 * à vérifier ce que l'EXTRACTEUR expose, pas le rendu.
 */
object SyntheticLiveTs {
    const val PID_PMT = 0x1000
    const val PID_VIDEO = 0x100
    const val PID_AUDIO = 0x101
    const val PID_DVBSUB = 0x102
    const val PID_TELETEXT = 0x103

    fun bytes(frames: Int = 6, withCea608: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        val cc = IntArray(0x2000)
        fun emit(pid: Int, pusi: Boolean, payload: ByteArray) {
            var off = 0; var first = true
            while (off < payload.size || first) {
                val n = minOf(184, payload.size - off)
                val pkt = ByteArray(188) { 0xFF.toByte() }
                pkt[0] = 0x47
                pkt[1] = ((if (pusi && first) 0x40 else 0) or (pid shr 8)).toByte()
                pkt[2] = pid.toByte()
                val stuffing = 184 - n
                if (stuffing == 0) {
                    pkt[3] = (0x10 or (cc[pid] and 0xF)).toByte()
                    System.arraycopy(payload, off, pkt, 4, n)
                } else {
                    pkt[3] = (0x30 or (cc[pid] and 0xF)).toByte()
                    pkt[4] = (stuffing - 1).toByte()
                    if (stuffing > 1) pkt[5] = 0
                    System.arraycopy(payload, off, pkt, 4 + stuffing, n)
                }
                cc[pid]++
                out.write(pkt)
                off += n; first = false
            }
        }
        fun psi(pid: Int, section: ByteArray) = emit(pid, true, byteArrayOf(0) + section)

        psi(0, section(0x00, 1, byteArrayOf(0, 1, (0xE0 or (PID_PMT shr 8)).toByte(), PID_PMT.toByte())))
        val pmt = ByteArrayOutputStream().apply {
            write(0xE0 or (PID_VIDEO shr 8)); write(PID_VIDEO)            // PCR sur la vidéo
            write(0xF0); write(0)                                          // pas de descripteur de programme
            es(0x1B, PID_VIDEO, byteArrayOf())                             // H.264
            es(0x0F, PID_AUDIO, byteArrayOf())                             // AAC ADTS
            // Sous-titres DVB (descripteur 0x59 : fra, type 0x10, pages 1 / 1).
            es(0x06, PID_DVBSUB, byteArrayOf(0x59, 8, 'f'.code.toByte(), 'r'.code.toByte(), 'a'.code.toByte(), 0x10, 0, 1, 0, 1))
            // Télétexte (descripteur 0x56 : fra, type 2 « sous-titres », magazine 1, page 0x88).
            es(0x06, PID_TELETEXT, byteArrayOf(0x56, 5, 'f'.code.toByte(), 'r'.code.toByte(), 'a'.code.toByte(), 0x11, 0x88.toByte()))
        }.toByteArray()
        psi(PID_PMT, section(0x02, 1, pmt))

        val startPts = 90_000L
        for (i in 0 until frames) {
            val nal = ByteArrayOutputStream()
            nal.write(startCode()); nal.write(byteArrayOf(0x09, 0xF0.toByte()))                    // délimiteur d'unité d'accès
            if (withCea608) { nal.write(startCode()); nal.write(seiCea608(i)) }
            nal.write(startCode()); nal.write(byteArrayOf(0x65, 0x88.toByte(), 0x84.toByte(), 0x21, 0x7F, 0x55, 0x33))   // « image » factice
            emit(PID_VIDEO, true, pes(0xE0, startPts + i * 3_600L, nal.toByteArray()))
        }
        // Dernier délimiteur : ferme la dernière unité d'accès.
        emit(PID_VIDEO, true, pes(0xE0, startPts + frames * 3_600L, startCode() + byteArrayOf(0x09, 0xF0.toByte())))
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.es(type: Int, pid: Int, descriptors: ByteArray) {
        write(type); write(0xE0 or (pid shr 8)); write(pid)
        write(0xF0 or (descriptors.size shr 8)); write(descriptors.size); write(descriptors)
    }

    private fun startCode() = byteArrayOf(0, 0, 0, 1)

    /** Section PSI avec en-tête long et CRC-32 MPEG. */
    private fun section(tableId: Int, idExt: Int, body: ByteArray): ByteArray {
        val len = body.size + 5 + 4
        val s = ByteArrayOutputStream()
        s.write(tableId); s.write(0xB0 or (len shr 8)); s.write(len)
        s.write(idExt shr 8); s.write(idExt); s.write(0xC1); s.write(0); s.write(0)
        s.write(body)
        val head = s.toByteArray()
        val crc = crc32Mpeg(head)
        return head + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
    }

    private fun crc32Mpeg(d: ByteArray): Int {
        var c = -1
        for (b in d) {
            c = c xor ((b.toInt() and 0xFF) shl 24)
            repeat(8) { c = if (c < 0) (c shl 1) xor 0x04C11DB7 else c shl 1 }
        }
        return c
    }

    private fun pes(streamId: Int, pts: Long, payload: ByteArray): ByteArray {
        fun ts(marker: Int, v: Long) = byteArrayOf(
            (marker or (((v shr 30) and 7) shl 1).toInt() or 1).toByte(), ((v shr 22) and 0xFF).toByte(),
            ((((v shr 15) and 0x7F) shl 1) or 1).toByte(), ((v shr 7) and 0xFF).toByte(), (((v and 0x7F) shl 1) or 1).toByte(),
        )
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0, 0, 1, streamId.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5)); o.write(ts(0x20, pts)); o.write(payload)
        return o.toByteArray()
    }

    /** SEI « user_data_registered_itu_t_t35 » ATSC A/53 : deux triplets cc_data CEA-608 (champ 1) — « RCL » doublé. */
    private fun seiCea608(i: Int): ByteArray {
        val ccData = byteArrayOf(
            0x03, 0x42, 0xFF.toByte(),                                  // user_data_type_code, process_cc_data + 2 triplets, em_data
            0xFC.toByte(), 0x94.toByte(), 0x20, 0xFC.toByte(), 0x94.toByte(), 0x20,   // CC1 : RCL (reprise de légende) ×2
            0xFF.toByte(),
        )
        val payload = byteArrayOf(0xB5.toByte(), 0x00, 0x31, 'G'.code.toByte(), 'A'.code.toByte(), '9'.code.toByte(), '4'.code.toByte()) + ccData
        return byteArrayOf(0x06, 0x04, payload.size.toByte()) + payload + byteArrayOf(0x80.toByte())
    }
}

package com.ultratv.tv.nativeapp.data.timeshift

import com.ultratv.tv.nativeapp.adaptive.Tier

/** Dimensionnement du tampon de pause du direct. Pur. */
object TimeshiftPlanner {
    const val MIN_MINUTES = 30
    /** Sous ce plancher, on ne propose pas la pause (le tampon ne couvrirait presque rien). */
    const val MIN_CAPACITY_BYTES = 128L * 1024 * 1024
    /** Part maximale de l'espace libre qu'on s'autorise à occuper. */
    const val FREE_SPACE_SHARE = 0.4

    data class Plan(val capacityBytes: Long, val minutes: Int, val assumedBytesPerSec: Long)

    /** Débit supposé avant mesure : HD ~ 8 Mb/s, 4K ~ 25 Mb/s. */
    fun assumedBytesPerSec(screenHeight: Int): Long = if (screenHeight > 1200) 25_000_000L / 8 else 8_000_000L / 8

    /** Minutes visées : 30 au minimum ; les appareils plus capables (disque et mémoire) en gardent davantage. */
    fun targetMinutes(tier: Tier): Int = when (tier) { Tier.LOW -> MIN_MINUTES; Tier.MID -> 45; Tier.HIGH -> 60 }

    /** null = espace insuffisant (pause du direct indisponible). */
    fun plan(freeBytes: Long, tier: Tier, screenHeight: Int): Plan? {
        val bps = assumedBytesPerSec(screenHeight)
        val budget = (freeBytes * FREE_SPACE_SHARE).toLong()
        if (budget < MIN_CAPACITY_BYTES) return null
        val wanted = bps * 60L * targetMinutes(tier)
        val cap = minOf(wanted, budget)
        return Plan(cap, (cap / bps / 60).toInt(), bps)
    }
}

/** Arithmétique position <-> temps du tampon, alignée sur les paquets MPEG-TS de 188 octets. */
object TimeshiftMath {
    const val TS_PACKET = 188L

    fun align(pos: Long): Long = pos - pos % TS_PACKET

    /** Nouvelle position après un saut de [deltaSec] depuis [pos], bornée à la fenêtre [start, end]. */
    fun jump(pos: Long, deltaSec: Int, bytesPerSec: Long, start: Long, end: Long): Long =
        align((pos + deltaSec * bytesPerSec).coerceIn(start, maxOf(start, end)))

    /** Retard sur le direct, en secondes. */
    fun behindLiveSec(pos: Long, end: Long, bytesPerSec: Long): Int =
        if (bytesPerSec <= 0) 0 else ((end - pos).coerceAtLeast(0) / bytesPerSec).toInt()

    fun windowSec(start: Long, end: Long, bytesPerSec: Long): Int =
        if (bytesPerSec <= 0) 0 else ((end - start).coerceAtLeast(0) / bytesPerSec).toInt()

    /** Premier octet de synchronisation (0x47) confirmé aux 2 paquets suivants ; -1 si absent des données reçues. */
    fun firstSync(b: ByteArray, len: Int): Int {
        var i = 0
        while (i < len) {
            if (b[i] == 0x47.toByte()) {
                val a = i + TS_PACKET.toInt(); val c = i + 2 * TS_PACKET.toInt()
                if (c < len) { if (b[a] == 0x47.toByte() && b[c] == 0x47.toByte()) return i }
                else if (a < len) { if (b[a] == 0x47.toByte()) return i }
                else return -1
            }
            i++
        }
        return -1
    }
}

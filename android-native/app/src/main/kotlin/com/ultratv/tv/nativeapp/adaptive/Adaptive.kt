package com.ultratv.tv.nativeapp.adaptive

import com.ultratv.tv.nativeapp.ui.player.engine.BufferPreset

/** Niveau de l'appareil : conditionne animations, images, tampons, fenêtre EPG, lots de la synchro. */
enum class Tier { LOW, MID, HIGH }

/** Qualité du réseau mesuré (pas seulement annoncé). */
enum class NetQuality { POOR, FAIR, GOOD, EXCELLENT }

enum class NetType { ETHERNET, WIFI, MOBILE, OTHER, NONE }

/** Effets d'interface : complets (HIGH), réduits (MID), désactivés (LOW). */
enum class UiEffects { FULL, REDUCED, NONE }

/** Capacité d'un décodeur matériel pour un codec. */
data class HwCodec(val maxWidth: Int, val maxHeight: Int, val maxFps: Int)

/** Données brutes de l'appareil (source injectable : faux ActivityManager / MediaCodecList en test). */
data class DeviceInfo(
    val ramMb: Int,
    val memoryClassMb: Int,
    val lowRamFlag: Boolean,
    val cores: Int,
    val maxFreqMhz: Int?,
    val is64Bit: Boolean,
    val sdk: Int,
    val h264: HwCodec?,
    val hevc: HwCodec?,
    val vp9: HwCodec?,
    val av1: HwCodec?,
    val screenWidth: Int,
    val screenHeight: Int,
    val hdr: Boolean,
    /** Micro-benchmark (ms) ; plus petit = plus rapide. */
    val benchmarkMs: Long?,
)

data class DeviceProfile(val info: DeviceInfo, val tier: Tier)

object DeviceClassifier {
    /**
     * LOW : drapeau low-RAM, moins de ~1,8 Go (Chromecast HD, box à 1 Go), classe mémoire < 160 Mo, ou machine très lente.
     * Sinon un score (RAM, cœurs, 64 bits, classe mémoire, benchmark) départage MID / HIGH.
     */
    fun tier(i: DeviceInfo): Tier {
        if (i.lowRamFlag || i.ramMb < 1800 || i.memoryClassMb < 160) return Tier.LOW
        if (i.benchmarkMs != null && i.benchmarkMs > 900) return Tier.LOW
        var score = 0
        score += when { i.ramMb >= 3500 -> 2; i.ramMb >= 2300 -> 1; else -> 0 }
        if (i.cores >= 4) score += 1
        if (i.cores >= 8) score += 1
        if (i.is64Bit) score += 1
        if (i.memoryClassMb >= 256) score += 1
        i.benchmarkMs?.let { score += if (it < 120) 1 else if (it > 350) -1 else 0 }
        return if (score >= 5) Tier.HIGH else if (score >= 2) Tier.MID else Tier.LOW
    }
}

/** Mesures réseau instantanées. */
data class NetSample(
    val type: NetType,
    val linkKbps: Int?,
    val metered: Boolean,
    val measuredKbps: Int?,
    val ttfbMs: Int?,
    val rebuffersRecent: Int,
    val errorsRecent: Int,
)

object NetClassifier {
    /** [previous] sert d'hystérésis : monter exige 25 % de marge au-dessus du seuil ; descendre est immédiat. */
    fun quality(s: NetSample, previous: NetQuality?): NetQuality {
        if (s.type == NetType.NONE) return NetQuality.POOR
        val kbps = s.measuredKbps ?: s.linkKbps?.let { (it * 0.5).toInt() } ?: 6_000
        val bounds = intArrayOf(3_000, 8_000, 25_000)   // POOR|FAIR|GOOD|EXCELLENT
        fun level(k: Double): Int = bounds.count { k >= it }
        var lvl = level(kbps.toDouble())
        if (previous != null && lvl > previous.ordinal) lvl = level(kbps / 1.25).coerceAtLeast(previous.ordinal).coerceAtMost(lvl)
        if (s.type == NetType.MOBILE) lvl = lvl.coerceAtMost(NetQuality.GOOD.ordinal)
        if (s.ttfbMs != null && s.ttfbMs > 1500) lvl = lvl.coerceAtMost(NetQuality.FAIR.ordinal)
        if (s.rebuffersRecent >= 3 || s.errorsRecent >= 3) lvl = (lvl - 1).coerceAtLeast(0)
        else if (s.rebuffersRecent >= 1) lvl = lvl.coerceAtMost(NetQuality.GOOD.ordinal)
        return NetQuality.entries[lvl]
    }
}

/** Réglages automatiques dérivés de l'appareil et du réseau. */
data class AutoSettings(
    val lowRam: Boolean,
    val uiEffects: UiEffects,
    val imageRgb565: Boolean,
    val railPrefetch: Int,
    val bufferPreset: BufferPreset,
    val maxVideoHeight: Int,
    val maxVideoBitrateBps: Int?,
    val preferH264: Boolean,
    val epgBackHours: Int,
    val epgForwardHours: Int,
    val insertBatch: Int,
    val syncParallelism: Int,
    val syncHeavyOnlyUnmetered: Boolean,
    val refreshHours: Int,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
)

object AutoTuner {
    const val LOW_TIER_MAX_HEIGHT = 720
    const val LOW_TIER_MAX_BITRATE = 6_000_000

    fun settings(device: DeviceProfile, quality: NetQuality, metered: Boolean, ttfbMs: Int?): AutoSettings {
        val tier = device.tier
        val i = device.info
        val bufferPreset = when (quality) {
            NetQuality.POOR, NetQuality.FAIR -> BufferPreset.STABLE
            NetQuality.GOOD -> if (tier == Tier.LOW) BufferPreset.AUTO else BufferPreset.BALANCED
            NetQuality.EXCELLENT -> if (tier == Tier.LOW) BufferPreset.AUTO else BufferPreset.LOW_LATENCY
        }
        // Résolution max : écran, décodeur, puis réseau.
        val hwH264Height = i.h264?.maxHeight ?: 720
        var maxH = minOf(i.screenHeight.coerceAtLeast(480), hwH264Height.coerceAtLeast(720))
        // Box 1 Go : un flux 4K/FHD fait grimper le RSS jusqu'au lowmemorykiller — on plafonne en HD.
        if (tier == Tier.LOW) maxH = minOf(maxH, LOW_TIER_MAX_HEIGHT)
        if (tier != Tier.HIGH || i.hevc == null) maxH = minOf(maxH, 1080)
        maxH = when (quality) { NetQuality.POOR -> minOf(maxH, 480); NetQuality.FAIR -> minOf(maxH, 720); else -> maxH }
        var maxBitrate = when (quality) { NetQuality.POOR -> 2_000_000; NetQuality.FAIR -> 5_000_000; NetQuality.GOOD -> 12_000_000; NetQuality.EXCELLENT -> null }
        if (tier == Tier.LOW) maxBitrate = minOf(maxBitrate ?: LOW_TIER_MAX_BITRATE, LOW_TIER_MAX_BITRATE)
        return AutoSettings(
            lowRam = tier == Tier.LOW,
            uiEffects = when (tier) { Tier.HIGH -> UiEffects.FULL; Tier.MID -> UiEffects.REDUCED; Tier.LOW -> UiEffects.NONE },
            imageRgb565 = tier == Tier.LOW,
            railPrefetch = when (tier) { Tier.HIGH -> 3; Tier.MID -> 2; Tier.LOW -> 1 },
            bufferPreset = bufferPreset,
            maxVideoHeight = maxH,
            maxVideoBitrateBps = maxBitrate,
            // Pas de HEVC matériel (ou box basse) : on préfère H.264 quand le fournisseur propose les deux.
            preferH264 = tier == Tier.LOW || i.hevc == null,
            epgBackHours = if (tier == Tier.HIGH) 4 else 2,
            epgForwardHours = when (tier) { Tier.LOW -> 24; Tier.MID -> 36; Tier.HIGH -> 72 },
            insertBatch = when (tier) { Tier.LOW -> 500; Tier.MID -> 1_000; Tier.HIGH -> 2_000 },
            syncParallelism = if (tier == Tier.HIGH && quality >= NetQuality.GOOD) 2 else 1,
            syncHeavyOnlyUnmetered = metered,
            refreshHours = when (tier) { Tier.LOW -> 12; Tier.MID -> 8; Tier.HIGH -> 6 },
            connectTimeoutMs = ((ttfbMs ?: 1_500) * 4).coerceIn(5_000, 20_000),
            readTimeoutMs = if (quality <= NetQuality.FAIR) 30_000 else 20_000,
        )
    }
}

/**
 * Adaptation EN COURS de lecture, avec hystérésis : 2 rebufferings en 60 s → un cran plus prudent ;
 * 90 s sans rebuffering ET 90 s depuis le dernier changement → un cran plus ambitieux. Jamais d'oscillation rapide.
 */
class PlaybackAdapter(private val upThreshold: Int = 2, private val windowMs: Long = 60_000, private val calmMs: Long = 90_000, val maxLevel: Int = 3) {
    /** 0 = réglage de base ; plus haut = plus prudent (tampon plus grand, débit max plus bas). */
    var level: Int = 0
        private set
    private val rebuffers = ArrayDeque<Long>()
    private var lastChangeAt = Long.MIN_VALUE / 2

    fun onRebuffer(now: Long): Int {
        rebuffers.addLast(now)
        while (rebuffers.isNotEmpty() && now - rebuffers.first() > windowMs) rebuffers.removeFirst()
        if (rebuffers.size >= upThreshold && level < maxLevel && now - lastChangeAt >= windowMs) { level++; lastChangeAt = now; rebuffers.clear() }
        return level
    }

    fun tick(now: Long): Int {
        while (rebuffers.isNotEmpty() && now - rebuffers.first() > calmMs) rebuffers.removeFirst()
        val calm = rebuffers.isEmpty() && now - lastChangeAt >= calmMs
        if (calm && level > 0) { level--; lastChangeAt = now }
        return level
    }

    companion object {
        /** Niveau → préréglage de tampon plus prudent. */
        fun bump(base: BufferPreset, level: Int): BufferPreset {
            val order = listOf(BufferPreset.LOW_LATENCY, BufferPreset.AUTO, BufferPreset.BALANCED, BufferPreset.STABLE)
            val i = order.indexOf(base).let { if (it < 0) 1 else it }
            return order[(i + level).coerceAtMost(order.lastIndex)]
        }
        /** Niveau → débit max (bps) : on divise par 1,5 par cran. */
        fun scaleBitrate(base: Int?, level: Int, fallback: Int = 12_000_000): Int? =
            if (level == 0) base else ((base ?: fallback) / Math.pow(1.5, level.toDouble())).toInt().coerceAtLeast(800_000)
    }
}

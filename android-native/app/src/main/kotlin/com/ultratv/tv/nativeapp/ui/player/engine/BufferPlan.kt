package com.ultratv.tv.nativeapp.ui.player.engine

/** Préréglages de la mémoire tampon (Réglages › Lecture › Mémoire tampon). */
enum class BufferPreset { AUTO, LOW_LATENCY, BALANCED, STABLE, CUSTOM }

/** Valeurs de l'option « Personnalisé » (secondes, sauf [maxMb]). */
data class CustomBuffer(val minSec: Int = 5, val maxSec: Int = 30, val startSec: Int = 2, val rebufferSec: Int = 3, val maxMb: Int = 32)

/** Pourquoi une valeur demandée a été ramenée à une borne sûre (expliqué à l'écran). */
enum class ClampReason { NONE, MEMORY, ORDER }

/** Paramètres effectifs, communs à Media3 (LoadControl) et à VLC (`network-caching`…). */
data class BufferParams(
    val minMs: Int,
    val maxMs: Int,
    val startMs: Int,
    val rebufferMs: Int,
    val maxBytes: Int,
    /** Décalage visé par rapport au bord du direct HLS. */
    val liveOffsetMs: Long,
    val clamp: ClampReason = ClampReason.NONE,
) {
    /** VLC : un seul `network-caching` — on y met le tampon de démarrage élargi du minimum. */
    val vlcNetworkCachingMs: Int get() = maxOf(startMs, minMs.coerceAtMost(maxMs))
    val vlcLiveCachingMs: Int get() = startMs.coerceAtLeast(300)
    val vlcFileCachingMs: Int get() = maxOf(startMs, minMs)
}

/**
 * Résout un préréglage en paramètres sûrs. Bornes : min ≤ max, démarrage ≤ min, et le tampon ne peut pas
 * dépasser ~25 % du tas de l'application (OOM sur un Chromecast de 2 Go en 1080p). Sur box low-RAM le plafond est 16 Mo.
 */
object BufferPlanner {
    /** Débit pessimiste d'un flux 1080p, en octets par seconde, pour convertir un plafond mémoire en secondes. */
    private const val WORST_BYTES_PER_SEC = 1_500_000

    fun resolve(preset: BufferPreset, custom: CustomBuffer, heapClassMb: Int, lowRam: Boolean): BufferParams {
        val raw = when (preset) {
            BufferPreset.LOW_LATENCY -> Raw(2_000, 12_000, 1_000, 1_500, if (lowRam) 12 else 24, 3_000)
            BufferPreset.BALANCED -> Raw(5_000, 30_000, 2_000, 3_000, if (lowRam) 16 else 48, 6_000)
            BufferPreset.STABLE -> Raw(15_000, 60_000, 4_000, 5_000, if (lowRam) 24 else 96, 12_000)
            BufferPreset.CUSTOM -> Raw(custom.minSec * 1000, custom.maxSec * 1000, custom.startSec * 1000, custom.rebufferSec * 1000, custom.maxMb, 6_000)
            BufferPreset.AUTO ->
                if (lowRam) Raw(3_000, 15_000, 1_000, 2_000, 16, 3_000)
                else Raw(5_000, 30_000, 1_500, 2_500, 48, 6_000)
        }
        var clamp = ClampReason.NONE
        // Ordre : démarrage ≤ min ≤ max.
        var max = raw.max.coerceAtLeast(1_000)
        var min = raw.min.coerceIn(500, max)
        var start = raw.start.coerceIn(200, min)
        var rebuffer = raw.rebuffer.coerceIn(200, max)
        if (raw.min > raw.max || raw.start > raw.min) clamp = ClampReason.ORDER
        // Mémoire : plafond demandé, borné à 25 % du tas (et 16 Mo en low-RAM).
        val heapCapMb = (heapClassMb / 4).coerceAtLeast(8)
        var mb = raw.mb.coerceAtLeast(4)
        val hardCap = if (lowRam) minOf(heapCapMb, 16) else heapCapMb
        if (mb > hardCap) { mb = hardCap; clamp = ClampReason.MEMORY }
        // Le tampon (en durée) ne peut pas non plus excéder ce que le plafond mémoire autorise.
        val maxByMemoryMs = (mb.toLong() * 1_048_576L * 1_000L / WORST_BYTES_PER_SEC).toInt()
        if (max > maxByMemoryMs) {
            max = maxByMemoryMs.coerceAtLeast(2_000)
            min = min.coerceAtMost(max); start = start.coerceAtMost(min); rebuffer = rebuffer.coerceAtMost(max)
            clamp = ClampReason.MEMORY
        }
        return BufferParams(min, max, start, rebuffer, mb * 1_048_576, raw.liveOffset, clamp)
    }

    private data class Raw(val min: Int, val max: Int, val start: Int, val rebuffer: Int, val mb: Int, val liveOffset: Long)
}

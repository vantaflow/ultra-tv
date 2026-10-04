package com.ultratv.tv.nativeapp.ui.sync

/**
 * Temps restant estimé par moyenne glissante de la vitesse de progression (points de pourcentage par
 * seconde) sur les [windowMs] dernières millisecondes. Renvoie null tant que la mesure n'est pas fiable.
 */
class EtaEstimator(private val windowMs: Long = 30_000, private val minSpanMs: Long = 3_000) {
    private val samples = ArrayDeque<Pair<Long, Int>>()

    fun add(nowMs: Long, percent: Int) {
        // Un recul (nouvelle synchro) repart de zéro.
        if (samples.isNotEmpty() && percent < samples.last().second) samples.clear()
        samples.addLast(nowMs to percent)
        while (samples.size > 2 && nowMs - samples.first().first > windowMs) samples.removeFirst()
    }

    fun etaSeconds(): Int? {
        if (samples.size < 2) return null
        val (t0, p0) = samples.first()
        val (t1, p1) = samples.last()
        val span = t1 - t0
        val dp = p1 - p0
        if (span < minSpanMs || dp <= 0) return null
        val rate = dp.toDouble() / span // % par ms
        return ((100 - p1) / rate / 1000.0).toInt().coerceAtLeast(0)
    }

    fun reset() = samples.clear()
}

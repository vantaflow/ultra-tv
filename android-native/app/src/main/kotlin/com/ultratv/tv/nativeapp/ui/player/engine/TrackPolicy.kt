package com.ultratv.tv.nativeapp.ui.player.engine

/**
 * Règles pures de choix des pistes vidéo d'ExoPlayer (testables sans lecteur) : qualité « Maximale », estimation
 * initiale du débit, lecture tunnelisée.
 */
object TrackPolicy {
    /** Une piste vidéo candidate (variante HLS / représentation DASH). [supported] : le décodeur de l'appareil la prend en charge. */
    data class VideoCandidate(val width: Int, val height: Int, val bitrate: Int, val supported: Boolean)

    /** Réglage Réglages › Lecture › Qualité : « auto » (adaptatif) ou « max » (piste la plus haute forcée). */
    const val QUALITY_AUTO = "auto"
    const val QUALITY_MAX = "max"
    fun isMax(pref: String?): Boolean = pref == QUALITY_MAX

    /**
     * Estimation de débit avant la première mesure. ExoPlayer part de ~1 à 2 Mb/s selon le réseau : il choisit alors la
     * variante la plus basse (240p-360p) et ne monte qu'après 10 s de tampon. Ici : 8 Mb/s par défaut (ABR corrige vite
     * à la baisse si faux), 25 Mb/s en qualité maximale, et jamais bien au-dessus du plafond de débit demandé.
     */
    const val DEFAULT_INITIAL_BPS = 8_000_000L
    const val MAX_QUALITY_INITIAL_BPS = 25_000_000L
    fun initialBitrateEstimate(maxBitrateBps: Int?, maxQuality: Boolean): Long = when {
        maxQuality -> MAX_QUALITY_INITIAL_BPS
        maxBitrateBps != null -> (maxBitrateBps * 3L / 2).coerceIn(2_000_000L, DEFAULT_INITIAL_BPS)
        else -> DEFAULT_INITIAL_BPS
    }

    /** Durée de tampon minimale (ms) avant de monter d'une variante : 4 s au lieu de 10 s par défaut. */
    const val MIN_INCREASE_BUFFER_MS = 4_000

    /**
     * Indice de la piste vidéo à forcer en qualité maximale : la plus haute résolution (nombre de pixels), puis le plus haut
     * débit, parmi les pistes prises en charge ; null s'il n'y en a aucune. Résolution inconnue (0) : seul le débit compte.
     */
    fun highestIndex(candidates: List<VideoCandidate>): Int? =
        candidates.withIndex().filter { it.value.supported }
            .maxWithOrNull(compareBy<IndexedValue<VideoCandidate>>({ it.value.width.toLong().coerceAtLeast(0) * it.value.height.coerceAtLeast(0) }, { it.value.bitrate }).thenBy { -it.index })
            ?.index
}

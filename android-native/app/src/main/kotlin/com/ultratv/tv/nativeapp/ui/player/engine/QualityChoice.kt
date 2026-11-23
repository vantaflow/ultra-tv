package com.ultratv.tv.nativeapp.ui.player.engine

import java.util.Locale

/**
 * Piste vidéo proposée par le moteur (variante HLS / représentation DASH, ou piste vidéo LibVLC). [id] est propre au
 * moteur ; [height] / [bitrateKbps] à 0 = inconnus.
 */
data class VideoQuality(val id: String, val height: Int, val bitrateKbps: Int, val selected: Boolean, val name: String? = null)

/**
 * Entrée « Qualité » du panneau des pistes (logique pure, testée) : « Auto » puis les variantes réellement disponibles, de
 * la plus haute à la plus basse, sans doublon. Les variantes au-dessus du plafond « Qualité maximale » des Réglages sont
 * grisées. Une seule variante (ou aucune) : « Qualité unique », jamais de liste vide.
 */
object QualityChoice {
    data class Option(val id: String, val height: Int, val label: String, val enabled: Boolean, val selected: Boolean)

    sealed interface Menu {
        /** Flux à une seule qualité (ou qualités inconnues) : [label] = sa résolution si connue. */
        data class Single(val label: String?) : Menu
        data class Choices(val autoSelected: Boolean, val options: List<Option>) : Menu
    }

    /**
     * [cap] : hauteur maximale autorisée (Réglages) ; null ou [Int.MAX_VALUE] = sans plafond. [manualId] : piste choisie à la
     * main (null = Auto).
     */
    fun menu(tracks: List<VideoQuality>, cap: Int?, manualId: String?): Menu {
        val known = tracks.filter { it.height > 0 || it.bitrateKbps > 0 || !it.name.isNullOrBlank() }
        // Doublons (même hauteur ET même débit : variantes d'un autre codec, pistes répétées) : une seule entrée.
        val distinct = known.distinctBy { if (it.height > 0 || it.bitrateKbps > 0) "${it.height}:${it.bitrateKbps}" else "n:${it.name}" }
            .sortedWith(compareByDescending<VideoQuality> { it.height }.thenByDescending { it.bitrateKbps })
        if (distinct.size <= 1) return Menu.Single(distinct.firstOrNull()?.let { labelOf(it, false) })
        val heights = distinct.groupingBy { it.height }.eachCount()
        val limit = cap ?: Int.MAX_VALUE
        val manual = manualId?.takeIf { id -> distinct.any { it.id == id } }
        val options = distinct.mapIndexed { i, t ->
            val label = if (t.height <= 0 && t.bitrateKbps <= 0) t.name ?: "#${i + 1}" else labelOf(t, (heights[t.height] ?: 0) > 1) ?: "#${i + 1}"
            Option(t.id, t.height, label, enabled = t.height <= limit, selected = t.id == manual)
        }
        return Menu.Choices(autoSelected = manual == null, options = options)
    }

    /** « 1080p », « 1080p · 5.0 Mb/s » (deux variantes de même hauteur), « 2.5 Mb/s » (hauteur inconnue). */
    fun labelOf(t: VideoQuality, withBitrate: Boolean): String? {
        val rate = t.bitrateKbps.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f Mb/s", it / 1000.0) }
        return when {
            t.height > 0 && withBitrate && rate != null -> "${t.height}p · $rate"
            t.height > 0 -> "${t.height}p"
            rate != null -> rate
            else -> t.name
        }
    }

    /**
     * Piste à reprendre pour une qualité choisie plus tôt dans la session ([height]) : même hauteur, sinon la plus haute en
     * dessous ; jamais au-dessus du plafond. Null = rester en Auto.
     */
    fun matchHeight(tracks: List<VideoQuality>, height: Int, cap: Int?): VideoQuality? {
        val limit = cap ?: Int.MAX_VALUE
        val ok = tracks.filter { it.height in 1..limit }
        if (ok.size < 2) return null
        return ok.filter { it.height == height }.maxByOrNull { it.bitrateKbps }
            ?: ok.filter { it.height < height }.maxWithOrNull(compareBy<VideoQuality> { it.height }.thenBy { it.bitrateKbps })
    }
}

/**
 * Qualité choisie à la main pendant la session de l'application (jamais enregistrée) : reprise aux chaînes et vidéos
 * suivantes tant que l'application tourne. null = Auto.
 */
object QualitySession {
    @Volatile var height: Int? = null
}

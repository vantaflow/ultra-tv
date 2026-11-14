package com.ultratv.tv.nativeapp.ui.player

import java.util.Locale

/** Mise en forme (pure, testée) de la résolution et du débit réels du flux, et seuil d'offre du bouton « Recharger ». */
object StreamInfoFormat {
    /** Durée de mise en mémoire tampon après laquelle « Recharger » est proposé. */
    const val RELOAD_OFFER_MS = 3_000L

    /** « 1280×720 · 720p » ; null si la taille est inconnue. Entrelacé/variantes non distingués. */
    fun resolution(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        return "$width×$height · ${height}p"
    }

    /** Reprend « 1280×720 » d'un [EngineStats.resolution] déjà formaté et ajoute « 720p ». */
    fun resolutionFromLabel(label: String?): String? {
        if (label.isNullOrBlank()) return null
        val m = Regex("^(\\d+)\\s*[×x]\\s*(\\d+)$").find(label.trim()) ?: return label
        return resolution(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    /** « 4.2 Mb/s » (point décimal fixe) ; null si le débit est inconnu ou nul. */
    fun bitrate(kbps: Int?): String? {
        if (kbps == null || kbps <= 0) return null
        return String.format(Locale.US, "%.1f Mb/s", kbps / 1000.0)
    }

    fun shouldOfferReload(bufferingForMs: Long): Boolean = bufferingForMs >= RELOAD_OFFER_MS
}

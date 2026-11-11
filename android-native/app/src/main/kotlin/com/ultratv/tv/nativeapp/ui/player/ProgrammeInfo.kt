package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.data.db.EpgEntity

/** Ce que la base sait d'un film / épisode pour la fiche « programme » (le titre et la durée viennent du lecteur). */
data class VodFacts(val year: Int?, val plot: String?, val genre: String?)

/** Calculs purs de la fiche programme et du bandeau « Ensuite » : testés sur la JVM. */
object ProgrammeInfo {
    /** Les [count] programmes qui commencent à la fin de [current] ou après (ou après [nowMs] sans programme en cours), dans l'ordre. */
    fun after(items: List<EpgEntity>, current: EpgEntity?, nowMs: Long, count: Int): List<EpgEntity> {
        val from = current?.endMs ?: nowMs
        return items.filter { it.startMs >= from }
            .distinctBy { it.startMs }.sortedBy { it.startMs }.take(count)
    }

    /** Année d'une date « 2019-05-12 », « 12/05/2019 » ou « 2019 » ; null si absente. */
    fun yearOf(date: String?): Int? = date?.let { Regex("(19|20)\\d{2}").find(it)?.value?.toIntOrNull() }

    /** Durée en « 1 h 35 » / « 45 min » ; null si inconnue (<= 0). */
    fun durationLabel(ms: Long): String? {
        if (ms <= 0) return null
        val m = (ms / 60_000).toInt().coerceAtLeast(1)
        return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"
    }

    /** Ligne « Ensuite 21:30 · Titre » ; null sans programme suivant. */
    fun thenLine(label: String, next: EpgEntity?, hm: (Long) -> String): String? =
        next?.takeIf { it.title.isNotBlank() }?.let { "$label ${hm(it.startMs)} · ${it.title}" }
}

package com.ultratv.tv.nativeapp.ui.detail

import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings

/**
 * Logique pure (sans Compose) des fiches film / série : ligne d'infos, choix de saison, saut à l'épisode, progression.
 * Séparée de l'écran pour être testée sur la JVM.
 */

/** Ligne d'infos de l'en-tête : segments de texte, puis badge IMDb et pastille Rotten Tomatoes si connus. */
data class InfoLine(val texts: List<String>, val imdb: String?, val rt: String?)

/**
 * « année | genre | durée (ou saisons · épisodes) | IMDb | RT ». [runtime] est déjà formaté (durée du film, ou
 * « 9 saisons · 182 épisodes »). Sans note IMDb, la note de la source ([fallback], sur 10) devient « ★ 7.4 ».
 */
fun buildInfoLine(year: String?, genre: String?, runtime: String?, ratings: ExternalRatings?, fallback: Double?): InfoLine {
    val genres = genre?.split(',', '/', '|', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(2)?.joinToString(", ")
    val star = if (ratings?.imdb == null && fallback != null && fallback > 0 && fallback <= 10)
        String.format(java.util.Locale.ROOT, "★ %.1f", fallback) else null
    return InfoLine(
        texts = listOfNotNull(year?.takeIf { it.isNotBlank() }, genres?.takeIf { it.isNotBlank() }, runtime?.takeIf { it.isNotBlank() }, star),
        imdb = ratings?.imdb?.let { String.format(java.util.Locale.ROOT, "IMDb %.1f", it) },
        rt = ratings?.rt?.let { "$it%" },
    )
}

/** Numéros de saison présents, croissants. */
fun seasonsOf(eps: List<EpisodeEntity>): List<Int> = eps.map { it.season }.distinct().sorted()

/** Saison affichée : le choix de l'utilisateur s'il existe encore, sinon celle de l'épisode à reprendre, sinon la première. */
fun activeSeasonOf(seasons: List<Int>, chosen: Int?, targetSeason: Int?): Int? =
    chosen?.takeIf { it in seasons } ?: targetSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()

/** Saison voisine de [current] (delta ±1) ; null s'il n'y en a pas (pas de rebouclage). */
fun stepSeason(seasons: List<Int>, current: Int?, delta: Int): Int? {
    val i = seasons.indexOf(current)
    if (i < 0) return null
    return seasons.getOrNull(i + delta)
}

/** Rang, dans la liste affichée, de l'épisode portant ce numéro ; null s'il n'existe pas. */
fun jumpIndex(shown: List<EpisodeEntity>, number: Int): Int? = shown.indexOfFirst { it.episode == number }.takeIf { it >= 0 }

/** Point d'entrée du focus dans la liste : l'épisode à reprendre s'il est affiché, sinon le premier. */
fun entryIndexOf(shown: List<EpisodeEntity>, target: EpisodeEntity?): Int =
    shown.indexOfFirst { it.remoteId == target?.remoteId }.takeIf { it >= 0 } ?: 0

/** Épisode terminé : lu jusqu'à moins d'une minute de la fin. */
fun isDone(durationMs: Long, positionMs: Long): Boolean = durationMs > 0 && positionMs >= durationMs - 60_000

/** Fraction lue (0 à 1) d'un épisode ; 1 s'il est terminé, 0 s'il n'est pas commencé. */
fun progressFraction(durationMs: Long, positionMs: Long): Float = when {
    isDone(durationMs, positionMs) -> 1f
    durationMs > 0 && positionMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    else -> 0f
}

/** « Reprendre » plutôt que « Regarder » : l'épisode visé a une position de lecture mémorisée. */
fun isResuming(positionMs: Long?): Boolean = (positionMs ?: 0L) > 0L

package com.ultratv.tv.nativeapp.data.tmdb

import org.json.JSONObject

/** Notes externes (IMDb, Rotten Tomatoes, Metacritic) relayées par le Worker depuis OMDb. Champ nul = inconnu. */
data class ExternalRatings(val imdb: Double? = null, val rt: Int? = null, val mc: Int? = null) {
    val isEmpty get() = imdb == null && rt == null && mc == null
}

object RatingsLogic {
    /** Réponse `/api/ratings` → notes, null si rien d'exploitable. */
    fun parse(json: String): ExternalRatings? {
        val r = runCatching { JSONObject(json).optJSONObject("ratings") }.getOrNull() ?: return null
        val imdb = r.optDouble("imdb", Double.NaN).takeIf { it.isFinite() && it > 0 && it <= 10 }
        val rt = r.optInt("rt", -1).takeIf { it in 0..100 }
        val mc = r.optInt("mc", -1).takeIf { it in 0..100 }
        return ExternalRatings(imdb, rt, mc).takeUnless { it.isEmpty }
    }

    /**
     * Libellés de la ligne méta d'une fiche : « IMDb 8.8 », « RT 87% ». Sans note IMDb, la note de la source / TMDB
     * ([fallback]) garde son « ★ 7.4 ». Rien de connu : liste vide.
     */
    fun labels(r: ExternalRatings?, fallback: Double?): List<String> = buildList {
        when {
            r?.imdb != null -> add(String.format(java.util.Locale.ROOT, "IMDb %.1f", r.imdb))
            fallback != null && fallback > 0 && fallback <= 10 -> add(String.format(java.util.Locale.ROOT, "★ %.1f", fallback))
        }
        r?.rt?.let { add("RT $it%") }
    }
}

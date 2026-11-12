package com.ultratv.tv.nativeapp.data.extmeta

import org.json.JSONObject

/** Images Fanart.tv d'un titre : logo officiel (« clearlogo »), fond HD et vignette paysage. Champ nul = absent. */
data class FanartArt(val logo: String?, val background: String?, val thumb: String?) {
    val isEmpty get() = logo == null && background == null && thumb == null
}

/** Épisode TVmaze réduit par le Worker ; [airAt] en ms epoch (null si la date n'est pas connue). */
data class TvEpisode(val season: Int?, val number: Int?, val name: String?, val airAt: Long?, val image: String? = null, val summary: String? = null)

/** Série TVmaze : statut (« Running », « Ended »…), chaîne, épisodes précédent et prochain. */
data class TvShowInfo(val id: Int?, val status: String?, val network: String?, val previous: TvEpisode?, val next: TvEpisode?) {
    val ended: Boolean get() = status.equals("Ended", ignoreCase = true)
}

/** Équipe TheSportsDB ; [badge] = URL de l'écusson (https), null si inconnu. */
data class TeamInfo(val name: String?, val short: String?, val badge: String?, val league: String?, val sport: String?)

/** Réponse `/api/sports/team` lisible : [team] nul = équipe inconnue (réponse à mémoriser aussi). */
class TeamAnswer(val team: TeamInfo?)

/** Lecture des réponses des proxys du Worker (voir cloudflare-config/src/extmeta.js). Fonctions pures, testées sur la JVM. */
object ExtMetaParser {
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }

    /** URL d'image acceptée : https uniquement (jamais une adresse en clair). */
    private fun https(u: String?): String? = u?.takeIf { it.startsWith("https://") }

    private fun obj(json: String?): JSONObject? = json?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun fanart(json: String?): FanartArt? {
        val o = obj(json) ?: return null
        return FanartArt(https(o.str("logo")), https(o.str("background")), https(o.str("thumb")))
    }

    fun episode(o: JSONObject?): TvEpisode? {
        if (o == null) return null
        fun int(k: String) = if (o.isNull(k)) null else o.optInt(k, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE && it >= 0 }
        val at = if (o.isNull("airAt")) null else o.optLong("airAt", 0L).takeIf { it > 0 }
        return TvEpisode(int("season"), int("number"), o.str("name"), at, https(o.str("image")), o.str("summary"))
    }

    fun tvShow(json: String?): TvShowInfo? {
        val o = obj(json) ?: return null
        val id = if (o.isNull("id")) null else o.optInt("id", 0).takeIf { it > 0 }
        return TvShowInfo(id, o.str("status"), o.str("network"), episode(o.optJSONObject("previous")), episode(o.optJSONObject("next")))
    }

    fun team(json: String?): TeamAnswer? {
        val o = obj(json) ?: return null
        if (!o.has("team")) return null
        val t = o.optJSONObject("team") ?: return TeamAnswer(null)
        return TeamAnswer(TeamInfo(t.str("name"), t.str("short"), https(t.str("badge")), t.str("league"), t.str("sport")))
    }
}

/** Ligne « épisode » d'une fiche série. */
sealed interface EpisodeLine {
    data class Next(val ep: TvEpisode) : EpisodeLine
    data class Last(val ep: TvEpisode) : EpisodeLine
    data object Ended : EpisodeLine
}

/** Choix des épisodes affichés (fiche série, rangée « Nouveaux épisodes »). Fonctions pures. */
object TvMazeLogic {
    const val WINDOW_MS = 7L * 24 * 3_600_000
    /** Séries interrogées au plus pour la rangée « Nouveaux épisodes ». */
    const val MAX_SERIES = 20

    /** Fiche série : prochain épisode s'il est annoncé ; sinon « Série terminée » ; sinon le dernier diffusé ; sinon rien. */
    fun lineOf(show: TvShowInfo?): EpisodeLine? {
        if (show == null) return null
        val next = show.next
        val prev = show.previous
        return when {
            next != null -> EpisodeLine.Next(next)
            show.ended -> EpisodeLine.Ended
            prev != null -> EpisodeLine.Last(prev)
            else -> null
        }
    }

    /** Séries à surveiller : en cours de visionnage d'abord, puis favorites ; sans doublon, au plus [max]. */
    fun candidates(inProgress: List<String>, favorites: List<String>, max: Int = MAX_SERIES): List<String> =
        (inProgress + favorites).filter { it.isNotBlank() }.distinct().take(max)

    /** Épisode retenu pour une série : [aired] = déjà diffusé (dans les 7 derniers jours), sinon à venir (dans les 7 jours). */
    data class Pick(val ep: TvEpisode, val aired: Boolean)

    fun pickOf(show: TvShowInfo?, nowMs: Long, windowMs: Long = WINDOW_MS): Pick? {
        if (show == null) return null
        show.previous?.let { p -> p.airAt?.let { if (it <= nowMs && nowMs - it <= windowMs) return Pick(p, aired = true) } }
        show.next?.let { n -> n.airAt?.let { if (it > nowMs && it - nowMs <= windowMs) return Pick(n, aired = false) } }
        return null
    }

    /**
     * « Nouveaux épisodes » : séries dont l'épisode précédent est sorti dans les 7 derniers jours ou dont le prochain sort
     * dans les 7 jours. Déjà diffusés d'abord (le plus récent en tête), puis à venir (le plus proche en tête).
     */
    fun <T> select(shows: List<Pair<T, TvShowInfo?>>, nowMs: Long, windowMs: Long = WINDOW_MS): List<Pair<T, Pick>> {
        val picked = shows.mapNotNull { (ref, s) -> pickOf(s, nowMs, windowMs)?.let { ref to it } }
        val aired = picked.filter { it.second.aired }.sortedByDescending { it.second.ep.airAt ?: 0L }
        val coming = picked.filterNot { it.second.aired }.sortedBy { it.second.ep.airAt ?: Long.MAX_VALUE }
        return aired + coming
    }
}

package com.ultratv.tv.nativeapp.data.tmdb

import android.content.Intent
import android.net.Uri
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.VodInfoEntity
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import org.json.JSONObject

const val TMDB_TTL_MS = 30L * 24 * 3_600_000
/** Réponse « introuvable » : on retente plus tôt (un titre peut arriver au catalogue TMDB). */
const val TMDB_NEGATIVE_TTL_MS = 3L * 24 * 3_600_000

/** Titre nettoyé + année servant à la recherche TMDB quand la source ne donne pas de `tmdb_id`. */
data class TmdbQuery(val title: String, val year: Int?)

fun tmdbQueryFor(rawName: String, knownYear: Int?): TmdbQuery {
    val c = TitleCleaner.clean(rawName)
    return TmdbQuery(c.title.ifBlank { rawName }.trim(), knownYear ?: c.year)
}

/** Identifiant TMDB fourni par la source : seulement un entier strictement positif. */
fun parseSourceTmdbId(raw: String?): Int? = raw?.trim()?.toIntOrNull()?.takeIf { it > 0 }

fun isFresh(row: TmdbInfoEntity, lang: String, nowMs: Long): Boolean {
    if (row.lang != lang) return false
    val ttl = if (row.tmdbId == null) TMDB_NEGATIVE_TTL_MS else TMDB_TTL_MS
    return nowMs - row.fetchedAt < ttl
}

object TmdbParser {
    /** Meilleur résultat d'une recherche : celui de la bonne année s'il y en a un, sinon le premier. */
    fun searchBest(json: String, year: Int?): Int? {
        val arr = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return null
        var first: Int? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", 0).takeIf { it > 0 } ?: continue
            if (first == null) first = id
            val y = (o.optString("release_date").ifBlank { o.optString("first_air_date") }).take(4).toIntOrNull()
            if (year != null && y != null && kotlin.math.abs(y - year) <= 1) return id
        }
        return first
    }

    fun details(json: String): TmdbDetails? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val id = o.optInt("id", 0).takeIf { it > 0 } ?: return null
        fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        val cast = o.optJSONObject("credits")?.optJSONArray("cast")?.let { a ->
            (0 until minOf(a.length(), 12)).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
        }.orEmpty()
        val vids = o.optJSONObject("videos")?.optJSONArray("results")
        val yt = (0 until (vids?.length() ?: 0)).mapNotNull { vids!!.optJSONObject(it) }
            .filter { it.optString("site") == "YouTube" && it.optString("key").isNotBlank() }
        val trailer = (yt.firstOrNull { it.optString("type") == "Trailer" && it.optBoolean("official", false) }
            ?: yt.firstOrNull { it.optString("type") == "Trailer" } ?: yt.firstOrNull())?.optString("key")
        return TmdbDetails(
            tmdbId = id, overview = str("overview"), posterPath = str("poster_path"), backdropPath = str("backdrop_path"),
            rating = o.optDouble("vote_average", 0.0).takeIf { it > 0.0 }, cast = cast, trailerKey = trailer,
            originalLanguage = str("original_language"),
        )
    }
}

/** Intent YouTube (application dédiée si présente) ; le repli web est l'URL standard. */
object TmdbTrailer {
    fun appIntent(key: String) = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key"))
    fun webIntent(key: String) = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl(key)))
    fun webUrl(key: String) = "https://www.youtube.com/watch?v=$key"

    /** Clé YouTube extraite d'une URL de bande-annonce (watch?v=, youtu.be/) ou la clé elle-même. */
    fun keyOf(urlOrKey: String): String? {
        val s = urlOrKey.trim()
        Regex("""(?:v=|youtu\.be/|embed/)([A-Za-z0-9_-]{11})""").find(s)?.let { return it.groupValues[1] }
        return s.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }

    /** Ouvre l'application YouTube si elle est là, sinon le navigateur ; sans effet si rien ne sait ouvrir le lien. */
    fun open(ctx: android.content.Context, urlOrKey: String) {
        val key = keyOf(urlOrKey)
        val intents = if (key != null) listOf(appIntent(key), webIntent(key)) else listOf(Intent(Intent.ACTION_VIEW, Uri.parse(urlOrKey)))
        for (i in intents) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
}

private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

/** La source reste prioritaire ; TMDB comble ce qu'elle ne fournit pas. */
fun mergeVodInfo(src: VodInfoEntity?, movie: MovieEntity, t: TmdbInfoEntity?): VodInfoEntity? {
    if (t?.tmdbId == null) return src
    val base = src ?: VodInfoEntity(providerId = movie.providerId, remoteId = movie.remoteId)
    return base.copy(
        plot = base.plot.orNullIfBlank() ?: t.overview,
        cast = base.cast.orNullIfBlank() ?: t.cast,
        rating = base.rating?.takeIf { it > 0.0 } ?: t.rating,
        backdrop = base.backdrop.orNullIfBlank() ?: t.backdropUrl,
        trailer = base.trailer.orNullIfBlank() ?: t.trailerKey?.let(TmdbTrailer::webUrl),
        tmdbId = base.tmdbId.orNullIfBlank() ?: t.tmdbId.toString(),
    )
}

fun mergeMovie(m: MovieEntity, t: TmdbInfoEntity?): MovieEntity =
    if (t?.tmdbId == null) m else m.copy(
        poster = m.poster.orNullIfBlank() ?: t.posterUrl,
        backdrop = m.backdrop.orNullIfBlank() ?: t.backdropUrl,
        plot = m.plot.orNullIfBlank() ?: t.overview,
        cast = m.cast.orNullIfBlank() ?: t.cast,
        rating = m.rating?.takeIf { it > 0.0 } ?: t.rating,
    )

fun mergeSeries(s: SeriesEntity, t: TmdbInfoEntity?): SeriesEntity =
    if (t?.tmdbId == null) s else s.copy(
        poster = s.poster.orNullIfBlank() ?: t.posterUrl,
        backdrop = s.backdrop.orNullIfBlank() ?: t.backdropUrl,
        plot = s.plot.orNullIfBlank() ?: t.overview,
        cast = s.cast.orNullIfBlank() ?: t.cast,
        rating = s.rating?.takeIf { it > 0.0 } ?: t.rating,
    )

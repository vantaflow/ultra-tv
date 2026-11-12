package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import org.json.JSONArray
import org.json.JSONObject

/** Épisode d'une saison TMDB (`/tv/{id}/season/{n}`) : seuls les champs utiles à l'affichage. */
data class TmdbEpisode(
    val number: Int,
    val name: String?,
    val overview: String?,
    val stillPath: String?,
    val airDate: String? = null,
    val runtime: Int? = null,
)

/** Lecture / écriture des saisons TMDB (réponse du proxy et cache persistant, même format JSON). */
object TmdbSeasonCodec {
    /** Validité du cache d'une saison (mémoire et disque). */
    const val TTL_MS = 7L * 24 * 3_600_000

    /** Réponse TMDB → épisodes (numéro > 0) ; liste vide si illisible. */
    fun parse(json: String): List<TmdbEpisode> {
        val arr = runCatching { JSONObject(json).optJSONArray("episodes") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
            val n = o.optInt("episode_number", 0).takeIf { it > 0 } ?: return@mapNotNull null
            TmdbEpisode(n, str("name"), str("overview"), str("still_path"), str("air_date"), o.optInt("runtime", 0).takeIf { it > 0 })
        }
    }

    /** Épisodes + horodatage → JSON du cache (au format TMDB, relu par [parse]). */
    fun encode(eps: List<TmdbEpisode>, atMs: Long): String {
        val arr = JSONArray()
        for (e in eps) arr.put(JSONObject().apply {
            put("episode_number", e.number)
            e.name?.let { put("name", it) }
            e.overview?.let { put("overview", it) }
            e.stillPath?.let { put("still_path", it) }
            e.airDate?.let { put("air_date", it) }
            e.runtime?.let { put("runtime", it) }
        })
        return JSONObject().put("at", atMs).put("episodes", arr).toString()
    }

    /** Cache lu → épisodes (liste vide = « déjà demandé, rien »), ou null si absent, illisible ou périmé. */
    fun decode(raw: String?, nowMs: Long): List<TmdbEpisode>? {
        val o = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val at = o.optLong("at", -1L)
        if (at < 0 || nowMs - at !in 0 until TTL_MS) return null
        if (!o.has("episodes")) return null
        return parse(raw)
    }
}

private val SXXEXX_ANY = Regex("s\\d{1,2}\\s*[ex]\\s*\\d{1,4}")
private val NXN = Regex("\\d{1,2}\\s*x\\s*\\d{1,4}")
private val EPISODE_WORDS = Regex(
    "(?<!\\p{L})(episodes?|épisodes?|episodios?|episódios?|eps?|folge|capítulos?|capitulos?|cap|chapters?|chapitre|odcinek|odc|" +
        "الحلقة|حلقة|حلقه|الحلقه|season|saison|temporada|sezon|staffel|part|partie|parte)(?!\\p{L})\\.?",
)
/** Lettre isolée suivie d'un numéro : « E03 », « T2 », « S1 ». */
private val LETTER_NUMBER = Regex("(?<!\\p{L})[est]\\.?\\s*\\d+")
private val LETTERS = Regex("\\p{L}+")

private fun tokens(s: String): List<String> = LETTERS.findAll(s.lowercase(java.util.Locale.ROOT)).map { it.value }.toList()

/**
 * Le titre d'épisode de la source est-il générique (« Episode 3 », « Épisode 03 », « Ep. 3 », « E03 », « S01E03 »,
 * « Folge 3 », « Capítulo 3 », « حلقة 3 », « Odcinek 3 », chiffres seuls, vide, nom de la série, nom + numéro) ?
 * Une fois ôtés les marqueurs d'épisode / de saison et les chiffres, il ne reste aucun mot, ou seulement des mots du nom
 * de la série ([seriesName] et [otherNames], ex. nom brut de la source et titre nettoyé). [number] : numéro de l'épisode
 * (le titre « 3 » de l'épisode 3 est générique, comme tout titre fait de chiffres seuls).
 */
fun isGenericEpisodeTitle(title: String?, seriesName: String?, @Suppress("UNUSED_PARAMETER") number: Int, vararg otherNames: String?): Boolean {
    val t = title?.trim()?.lowercase(java.util.Locale.ROOT)
    if (t.isNullOrEmpty()) return true
    val stripped = t.replace(SXXEXX_ANY, " ").replace(NXN, " ").replace(LETTER_NUMBER, " ").replace(EPISODE_WORDS, " ")
    val left = tokens(stripped)
    if (left.isEmpty()) return true
    val names = (listOf(seriesName) + otherNames).filterNotNull().flatMap(::tokens).toSet()
    return names.isNotEmpty() && names.containsAll(left)
}

/**
 * Épisode de la source complété par TMDB : titre TMDB seulement si celui de la source est générique (et que celui de TMDB
 * ne l'est pas, TMDB disant parfois lui-même « Épisode 5 ») ; résumé, vignette (image.tmdb.org) et durée seulement s'ils
 * manquent. Sans épisode TMDB, renvoyé tel quel.
 */
fun mergeEpisode(ep: EpisodeEntity, t: TmdbEpisode?, seriesName: String?, seriesTitle: String? = null): EpisodeEntity {
    if (t == null) return ep
    val name = t.name?.trim()?.takeIf { it.isNotEmpty() && !isGenericEpisodeTitle(it, seriesName, ep.episode, seriesTitle) }
    val title = if (name != null && isGenericEpisodeTitle(ep.title, seriesName, ep.episode, seriesTitle)) name else ep.title
    val plot = ep.plot?.takeIf { it.isNotBlank() } ?: t.overview?.trim()?.takeIf { it.isNotEmpty() } ?: ep.plot
    val image = ep.image?.takeIf { it.isNotBlank() } ?: t.stillPath?.let(TmdbImages::still) ?: ep.image
    val duration = ep.duration?.takeIf { it.isNotBlank() } ?: t.runtime?.toString() ?: ep.duration
    return if (title == ep.title && plot == ep.plot && image == ep.image && duration == ep.duration) ep
    else ep.copy(title = title, plot = plot, image = image, duration = duration)
}

/** [mergeEpisode] sur une liste, d'après les saisons TMDB connues (numéro de saison → épisodes). */
fun mergeEpisodes(eps: List<EpisodeEntity>, seasons: Map<Int, List<TmdbEpisode>>, seriesName: String?, seriesTitle: String? = null): List<EpisodeEntity> {
    if (seasons.isEmpty()) return eps
    val byKey = seasons.mapValues { (_, l) -> l.associateBy { it.number } }
    return eps.map { mergeEpisode(it, byKey[it.season]?.get(it.episode), seriesName, seriesTitle) }
}

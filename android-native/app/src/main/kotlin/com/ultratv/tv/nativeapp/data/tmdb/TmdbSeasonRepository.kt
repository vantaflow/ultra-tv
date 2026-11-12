package com.ultratv.tv.nativeapp.data.tmdb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saisons TMDB (titres, résumés et vignettes des épisodes), par série / saison / langue. Cache mémoire (40 saisons) puis
 * petit fichier JSON par saison dans le cache de l'appli, valables 7 jours (réponse vide comprise : saison inconnue).
 * Aucune requête hors appairage ; toute erreur est silencieuse (null, non mémorisée) : la source s'affiche telle quelle.
 * Pas de colonne Room : la fusion se fait à l'affichage ([mergeEpisodes]).
 */
@Singleton
class TmdbSeasonRepository @Inject constructor(
    private val api: TmdbApi,
    private val tmdb: TmdbRepository,
    @ApplicationContext ctx: Context,
) {
    private val dir = File(ctx.cacheDir, "tmdb_seasons")

    private class Entry(val at: Long, val eps: List<TmdbEpisode>)
    private val mem = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Entry>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 40
    })

    private fun key(tvId: Int, season: Int, lang: String) = "v2-$tvId-$season-${lang.replace(Regex("[^A-Za-z-]"), "")}"

    /** Saison en cache (mémoire puis disque), sans aucune requête ; null si absente ou périmée. */
    suspend fun cached(tvId: Int, season: Int, now: Long = System.currentTimeMillis()): List<TmdbEpisode>? =
        runCatching { cachedFor(key(tvId, season, tmdb.lang()), now) }.getOrNull()

    private suspend fun cachedFor(k: String, now: Long): List<TmdbEpisode>? {
        mem[k]?.takeIf { now - it.at in 0 until TmdbSeasonCodec.TTL_MS }?.let { return it.eps }
        return withContext(Dispatchers.IO) {
            val f = File(dir, "$k.json")
            if (!f.isFile) return@withContext null
            val raw = runCatching { f.readText() }.getOrNull()
            TmdbSeasonCodec.decode(raw, now)?.also { mem[k] = Entry(f.lastModified().coerceAtMost(now), it) }
        }
    }

    /** Saison [season] de la série TMDB [tvId] : cache, sinon UNE requête au proxy. Null hors appairage ou en cas d'échec. */
    suspend fun season(tvId: Int, season: Int, now: Long = System.currentTimeMillis()): List<TmdbEpisode>? {
        if (tvId <= 0 || season < 0) return null
        return try {
            val lang = tmdb.lang()
            val k = key(tvId, season, lang)
            cachedFor(k, now)?.let { return it }
            if (!api.isEnabled()) return null
            var eps = api.season(tvId, season, lang).orEmpty()
            // TMDB sans titres dans la langue de l'appli : il renvoie lui-même « Épisode 3 ». On reprend alors les titres
            // anglais, sinon ceux de la langue d'origine de la série (turc, coréen…), en gardant résumés et vignettes.
            val generic = { l: List<TmdbEpisode> -> l.isNotEmpty() && l.all { isGenericEpisodeTitle(it.name, null, it.number) } }
            if (generic(eps)) {
                val fallbacks = buildList {
                    if (!lang.startsWith("en")) add("en-US")
                    runCatching { api.details(TmdbKind.TV, tvId, lang)?.originalLanguage }.getOrNull()
                        ?.takeIf { it.length == 2 && !lang.startsWith(it) && it != "en" }?.let { add(it) }
                }
                for (fl in fallbacks) {
                    val other = runCatching { api.season(tvId, season, fl).orEmpty() }.getOrDefault(emptyList()).associateBy { it.number }
                    if (other.isEmpty()) continue
                    eps = eps.map { e -> other[e.number]?.name?.takeIf { !isGenericEpisodeTitle(it, null, e.number) }?.let { e.copy(name = it) } ?: e }
                    if (!generic(eps)) break
                }
            }
            mem[k] = Entry(now, eps)
            withContext(Dispatchers.IO) {
                runCatching {
                    dir.mkdirs()
                    // Au-delà de MAX fichiers, on repart de zéro : les saisons se redemandent au besoin (cache du Worker).
                    if ((dir.list()?.size ?: 0) >= MAX_FILES) dir.listFiles()?.forEach { it.delete() }
                    File(dir, "$k.json").writeText(TmdbSeasonCodec.encode(eps, now))
                }
            }
            eps
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object { const val MAX_FILES = 300 }
}

package com.ultratv.tv.nativeapp.data.tmdb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Identifiants externes d'un titre TMDB (`external_ids`) : IMDb et TheTVDB. Les deux peuvent manquer. */
data class ExternalIds(val imdbId: String?, val tvdbId: Int?) {
    val isEmpty get() = imdbId == null && tvdbId == null
}

/**
 * Encodage des identifiants externes dans les préférences : « horodatage|imdb|tvdb » (champ vide = inconnu). Pas de
 * colonne Room (aucune migration) : quelques dizaines d'octets par titre ouvert, valables [TTL_MS].
 */
object ExternalIdsLogic {
    const val TTL_MS = 30L * 24 * 3_600_000

    fun key(kind: TmdbKind, tmdbId: Int) = "${kind.path}:$tmdbId"

    fun encode(ids: ExternalIds, atMs: Long): String = "$atMs|${ids.imdbId.orEmpty()}|${ids.tvdbId ?: ""}"

    /** Valeur lue → identifiants (éventuellement tous nuls : « déjà demandé, rien »), ou null si absente, illisible ou périmée. */
    fun decode(raw: String?, nowMs: Long): ExternalIds? {
        val p = raw?.split('|') ?: return null
        if (p.size != 3) return null
        val at = p[0].toLongOrNull() ?: return null
        if (nowMs - at !in 0 until TTL_MS) return null
        return ExternalIds(TmdbParser.imdbIdOf(p[1]), p[2].toIntOrNull()?.takeIf { it > 0 })
    }
}

/** Petit magasin persistant des identifiants externes (SharedPreferences, borné à [MAX] entrées). */
@Singleton
class TmdbExternalIdsStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("tmdb_external_ids", Context.MODE_PRIVATE)

    fun get(kind: TmdbKind, tmdbId: Int, nowMs: Long): ExternalIds? = ExternalIdsLogic.decode(sp.getString(ExternalIdsLogic.key(kind, tmdbId), null), nowMs)

    fun put(kind: TmdbKind, tmdbId: Int, ids: ExternalIds, nowMs: Long) {
        val e = sp.edit()
        // Au-delà de MAX entrées, on repart de zéro : les identifiants se redemandent au besoin (requête mise en cache par le Worker).
        if (sp.all.size >= MAX) e.clear()
        e.putString(ExternalIdsLogic.key(kind, tmdbId), ExternalIdsLogic.encode(ids, nowMs)).apply()
    }

    private companion object { const val MAX = 2_000 }
}

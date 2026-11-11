package com.ultratv.tv.nativeapp.data.remote

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.SearchResults

/** Résultat de recherche exposé au téléphone : identifiant LOCAL (Room), titre, année. Jamais d'URL ni d'identifiant IPTV. */
data class RemoteHit(val kind: String, val id: Long, val title: String, val year: Int? = null)

/** Ce qui est en cours sur la TV. */
data class RemoteNow(val title: String?, val kind: String?, val playing: Boolean?, val playerShown: Boolean)

/** Ce que le serveur sait faire ; l'implémentation réelle ([RemoteApiImpl]) s'appuie sur le catalogue local. */
interface RemoteApi {
    suspend fun search(query: String, kind: String): List<RemoteHit>
    /** Ouvre le résultat sur la TV ; false si introuvable (catalogue changé depuis). */
    suspend fun open(kind: String, id: Long): Boolean
    fun control(action: RemoteAction)
    fun now(): RemoteNow
}

object RemoteKinds {
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val LIVE = "live"
    val all = setOf(MOVIE, SERIES, LIVE)
}

/** Projection des résultats du catalogue vers des [RemoteHit] : c'est ici que tout champ sensible (URL de flux, affiche) est écarté. */
object RemoteHits {
    const val LIMIT = 30

    fun from(r: SearchResults, kind: String, limit: Int = LIMIT): List<RemoteHit> = when (kind) {
        RemoteKinds.MOVIE -> r.movies.take(limit).map { it.toHit() }
        RemoteKinds.SERIES -> r.series.take(limit).map { it.toHit() }
        RemoteKinds.LIVE -> r.channels.filter { it.title.isNotBlank() && !it.isSeparator && !it.junk }.take(limit).map { it.toHit() }
        else -> emptyList()
    }

    private fun MovieEntity.toHit() = RemoteHit(RemoteKinds.MOVIE, id, title.ifBlank { name }, year)
    private fun SeriesEntity.toHit() = RemoteHit(RemoteKinds.SERIES, id, title.ifBlank { name }, year)
    private fun ChannelEntity.toHit() = RemoteHit(RemoteKinds.LIVE, id, title)
}

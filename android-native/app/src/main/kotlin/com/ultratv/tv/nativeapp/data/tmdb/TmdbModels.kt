package com.ultratv.tv.nativeapp.data.tmdb

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

enum class TmdbKind(val path: String) { MOVIE("movie"), TV("tv") }

/** Détails TMDB utiles aux fiches (réponse « détail + credits + videos » déjà réduite). */
data class TmdbDetails(
    val tmdbId: Int,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double?,
    val cast: List<String>,
    val trailerKey: String?,
    /** Code ISO 639-1 de la langue d'origine (« fr », « ja »…), pour le détecteur de langue. */
    val originalLanguage: String?,
)

/**
 * Cache Room des fiches TMDB. [tmdbId] nul = « introuvable » (réponse négative mise en cache pour ne pas
 * réinterroger le Worker à chaque ouverture). [lang] = langue demandée : un changement de langue invalide la ligne.
 */
@Entity(tableName = "tmdb_info", primaryKeys = ["kind", "providerId", "remoteId"])
data class TmdbInfoEntity(
    val kind: String,
    val providerId: Long,
    val remoteId: String,
    val tmdbId: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double? = null,
    /** Noms séparés par des virgules. */
    val cast: String? = null,
    val trailerKey: String? = null,
    val originalLanguage: String? = null,
    val lang: String = "",
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val posterUrl: String? get() = posterPath?.let { TmdbImages.poster(it) }
    val backdropUrl: String? get() = backdropPath?.let { TmdbImages.backdrop(it) }
}

@Dao
interface TmdbDao {
    @Query("SELECT * FROM tmdb_info WHERE kind = :kind AND providerId = :pid AND remoteId = :rid")
    suspend fun get(kind: String, pid: Long, rid: String): TmdbInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TmdbInfoEntity)

    @Query("DELETE FROM tmdb_info WHERE fetchedAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String) = BASE + "w500" + path
    /** Affiche de repli (grilles, recherche) : plus légère que [poster]. */
    fun poster342(path: String) = BASE + "w342" + path
    fun backdrop(path: String) = BASE + "w1280" + path
}

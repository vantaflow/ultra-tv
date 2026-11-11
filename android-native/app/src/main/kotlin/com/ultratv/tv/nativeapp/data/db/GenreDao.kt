package com.ultratv.tv.nativeapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Genre automatique d'un titre (jointure titre ↔ genre). Clé par (source, type, identifiant fournisseur) et non par `id` :
 * l'upsert de synchro est un REPLACE qui change l'`id` mais garde le `remoteId`, donc le calcul survit aux synchros.
 * [lvl] = masque des données disponibles au moment du calcul (1 genre de la liste, 2 genre de la fiche, 4 genre TMDB) :
 * quand une fiche ou un enrichissement TMDB apporte une donnée de plus, le titre est recalculé (et lui seul).
 */
@Entity(
    tableName = "title_genre",
    primaryKeys = ["providerId", "kind", "remoteId", "genre"],
    indices = [Index(value = ["providerId", "kind", "genre", "remoteId"])],
)
data class TitleGenreEntity(
    val providerId: Long,
    /** "MOVIE" ou "SERIES". */
    val kind: String,
    val remoteId: String,
    /** Code de [com.ultratv.tv.nativeapp.data.repo.Genre]. */
    val genre: String,
    @ColumnInfo(defaultValue = "0") val lvl: Int = 0,
)

/** Données brutes d'un titre à classer. [mask] est calculé en SQL (même expression que le test de péremption). */
data class GenreSource(
    val id: Long,
    val remoteId: String,
    val genre: String?,
    val vodGenre: String?,
    val tmdbGenreIds: String?,
    val origLang: String?,
    val mask: Int,
)

/** Genre automatique d'un titre (lecture pour « Parce que vous avez regardé »). */
data class TitleGenreRow(val remoteId: String, val genre: String)

@Dao
interface GenreDao {
    @Query("SELECT remoteId AS remoteId, genre AS genre FROM title_genre WHERE providerId = :pid AND kind = :kind AND remoteId IN (:remoteIds) AND genre != 'OTHER'")
    suspend fun genresOf(pid: Long, kind: String, remoteIds: List<String>): List<TitleGenreRow>

    // Titres à (re)classer : aucune ligne de genre au niveau de données actuel (nouveau titre, ou donnée apparue depuis).
    // Pagination par clé (id > :afterId) : chaque lot repart de l'index, jamais d'OFFSET.
    @Query("""
        SELECT m.id AS id, m.remoteId AS remoteId, m.genre AS genre, v.genre AS vodGenre, t.genreIds AS tmdbGenreIds, t.originalLanguage AS origLang,
          (CASE WHEN TRIM(COALESCE(m.genre, '')) != '' THEN 1 ELSE 0 END + CASE WHEN TRIM(COALESCE(v.genre, '')) != '' THEN 2 ELSE 0 END + CASE WHEN TRIM(COALESCE(t.genreIds, '')) != '' THEN 4 ELSE 0 END) AS mask
        FROM movie m
        LEFT JOIN vod_info v ON v.providerId = m.providerId AND v.remoteId = m.remoteId
        LEFT JOIN tmdb_info t ON t.kind = 'movie' AND t.providerId = m.providerId AND t.remoteId = m.remoteId
        WHERE m.providerId = :pid AND m.id > :afterId
        AND NOT EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = m.providerId AND g.kind = 'MOVIE' AND g.remoteId = m.remoteId
          AND g.lvl = (CASE WHEN TRIM(COALESCE(m.genre, '')) != '' THEN 1 ELSE 0 END + CASE WHEN TRIM(COALESCE(v.genre, '')) != '' THEN 2 ELSE 0 END + CASE WHEN TRIM(COALESCE(t.genreIds, '')) != '' THEN 4 ELSE 0 END))
        ORDER BY m.id LIMIT :limit
    """)
    suspend fun pendingMovies(pid: Long, afterId: Long, limit: Int): List<GenreSource>

    @Query("""
        SELECT s.id AS id, s.remoteId AS remoteId, s.genre AS genre, NULL AS vodGenre, t.genreIds AS tmdbGenreIds, t.originalLanguage AS origLang,
          (CASE WHEN TRIM(COALESCE(s.genre, '')) != '' THEN 1 ELSE 0 END + CASE WHEN TRIM(COALESCE(t.genreIds, '')) != '' THEN 4 ELSE 0 END) AS mask
        FROM series s
        LEFT JOIN tmdb_info t ON t.kind = 'tv' AND t.providerId = s.providerId AND t.remoteId = s.remoteId
        WHERE s.providerId = :pid AND s.id > :afterId
        AND NOT EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = s.providerId AND g.kind = 'SERIES' AND g.remoteId = s.remoteId
          AND g.lvl = (CASE WHEN TRIM(COALESCE(s.genre, '')) != '' THEN 1 ELSE 0 END + CASE WHEN TRIM(COALESCE(t.genreIds, '')) != '' THEN 4 ELSE 0 END))
        ORDER BY s.id LIMIT :limit
    """)
    suspend fun pendingSeries(pid: Long, afterId: Long, limit: Int): List<GenreSource>

    @Query("SELECT COUNT(*) FROM movie WHERE providerId = :pid")
    suspend fun movieCount(pid: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE providerId = :pid")
    suspend fun seriesCount(pid: Long): Int

    @Query("SELECT COUNT(DISTINCT remoteId) FROM title_genre WHERE providerId = :pid AND kind = :kind")
    suspend fun classifiedCount(pid: Long, kind: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<TitleGenreEntity>)

    @Query("DELETE FROM title_genre WHERE providerId = :pid AND kind = :kind AND remoteId IN (:remoteIds)")
    suspend fun deleteFor(pid: Long, kind: String, remoteIds: List<String>)

    /** Remplace les genres d'un lot de titres en UNE transaction (courte : le lecteur de l'écran n'attend jamais longtemps). */
    @Transaction
    suspend fun replaceBatch(pid: Long, kind: String, remoteIds: List<String>, rows: List<TitleGenreEntity>) {
        deleteFor(pid, kind, remoteIds)
        insertAll(rows)
    }

    /** Titres disparus du catalogue (synchro, catégorie désactivée) : leurs genres sont purgés. */
    @Query("DELETE FROM title_genre WHERE providerId = :pid AND kind = 'MOVIE' AND NOT EXISTS (SELECT 1 FROM movie m WHERE m.providerId = title_genre.providerId AND m.remoteId = title_genre.remoteId)")
    suspend fun purgeMovies(pid: Long)

    @Query("DELETE FROM title_genre WHERE providerId = :pid AND kind = 'SERIES' AND NOT EXISTS (SELECT 1 FROM series s WHERE s.providerId = title_genre.providerId AND s.remoteId = title_genre.remoteId)")
    suspend fun purgeSeries(pid: Long)

    /** Nombre de titres par genre, en UNE requête GROUP BY servie par l'index (providerId, kind, genre, remoteId). */
    @Query("SELECT genre AS categoryId, COUNT(*) AS n, 0 AS sections FROM title_genre WHERE providerId = :pid AND kind = :kind GROUP BY genre")
    fun observeCounts(pid: Long, kind: String): Flow<List<CategoryCount>>

    @Query("DELETE FROM title_genre WHERE providerId = :pid")
    suspend fun deleteForProvider(pid: Long)
}

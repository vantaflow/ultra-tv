package com.ultratv.tv.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Ordre PERSONNEL des chaînes d'un profil (déplacement manuel dans le Direct). Une ligne = la place d'une chaîne dans une
 * liste : [scope] est l'identifiant de la catégorie du fournisseur, ou « __fav__ » pour les favoris. Les chaînes sans
 * ligne gardent l'ordre naturel (numéro, puis identifiant) et passent APRÈS celles qui en ont une. Clé par
 * (fournisseur, identifiant fournisseur) et non par `channel.id` : la resynchronisation réécrit les chaînes (REPLACE) en
 * changeant leur `id`. Remplace l'ancienne colonne `channel.userPosition` (globale et jamais lue).
 */
@Entity(
    tableName = "channel_order",
    primaryKeys = ["profileId", "providerId", "scope", "remoteId"],
    indices = [Index(value = ["profileId", "providerId", "scope", "position"])],
)
data class ChannelOrderEntity(
    val profileId: Long,
    val providerId: Long,
    val scope: String,
    val remoteId: String,
    /** Place dans la liste, à partir de 1. */
    val position: Int,
)

data class ChannelOrderRow(val remoteId: String, val position: Int)

@Dao
interface ChannelOrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ChannelOrderEntity>)

    @Query("SELECT remoteId AS remoteId, position AS position FROM channel_order WHERE profileId = :profileId AND providerId = :pid AND scope = :scope")
    suspend fun positions(profileId: Long, pid: Long, scope: String): List<ChannelOrderRow>

    @Query("SELECT position FROM channel_order WHERE profileId = :profileId AND providerId = :pid AND scope = :scope AND remoteId = :rid")
    suspend fun positionOf(profileId: Long, pid: Long, scope: String, rid: String): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM channel_order WHERE profileId = :profileId AND providerId = :pid AND scope = :scope)")
    suspend fun has(profileId: Long, pid: Long, scope: String): Boolean

    /** Listes (catégories, favoris) qui ont un ordre personnel pour ce profil et cette source. */
    @Query("SELECT DISTINCT scope FROM channel_order WHERE profileId = :profileId AND providerId = :pid")
    fun observeScopes(profileId: Long, pid: Long): Flow<List<String>>

    @Query("DELETE FROM channel_order WHERE profileId = :profileId AND providerId = :pid AND scope = :scope")
    suspend fun clear(profileId: Long, pid: Long, scope: String)

    @Query("DELETE FROM channel_order WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}

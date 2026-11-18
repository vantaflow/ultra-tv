package com.ultratv.tv.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Chaîne ajoutée À LA MAIN à une source existante (Xtream, M3U, Stalker). Stockée À PART de la table `channel` :
 * la synchro supprime et réécrit cette dernière, alors que cette table n'est jamais touchée par elle. Après chaque
 * synchro, [com.ultratv.tv.nativeapp.data.repo.ManualChannels.reinject] recopie ces lignes dans `channel`.
 *
 * [remoteId] vaut « custom:<uuid> » (même valeur sur toutes les plateformes) : favoris, historique et ordre personnel
 * s'y accrochent comme pour une chaîne ordinaire. [categoryId] : identifiant d'une catégorie de la source, ou
 * [com.ultratv.tv.nativeapp.data.repo.ManualChannels.CUSTOM_CAT] (catégorie « Mes chaînes », créée à la demande ;
 * [categoryName] est alors son libellé). SECRET : [streamUrl] peut contenir des identifiants, jamais journalisée.
 */
@Entity(tableName = "manual_channel", primaryKeys = ["providerId", "remoteId"])
data class ManualChannelEntity(
    val providerId: Long,
    val remoteId: String,
    val name: String,
    val streamUrl: String,
    val logo: String? = null,
    val epgChannelId: String? = null,
    val categoryId: String,
    val categoryName: String? = null,
    val createdAt: Long = 0,
)

@Dao
interface ManualChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ManualChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ManualChannelEntity>)

    @Query("SELECT * FROM manual_channel WHERE providerId = :pid ORDER BY createdAt, remoteId")
    suspend fun forProvider(pid: Long): List<ManualChannelEntity>

    @Query("SELECT * FROM manual_channel WHERE providerId = :pid ORDER BY createdAt, remoteId")
    fun observeForProvider(pid: Long): Flow<List<ManualChannelEntity>>

    @Query("SELECT * FROM manual_channel WHERE providerId = :pid AND remoteId = :rid")
    suspend fun byRemoteId(pid: Long, rid: String): ManualChannelEntity?

    @Query("DELETE FROM manual_channel WHERE providerId = :pid AND remoteId = :rid")
    suspend fun delete(pid: Long, rid: String)

    @Query("DELETE FROM manual_channel WHERE providerId = :pid")
    suspend fun deleteForProvider(pid: Long)
}

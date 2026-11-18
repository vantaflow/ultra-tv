package com.ultratv.tv.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Chaîne masquée par UN profil (la source de vérité par profil). [categoryId] et [name] servent à la retrouver dans
 * Réglages › Catégories et à recharger sa catégorie quand elle est réaffichée.
 */
@Entity(tableName = "profile_hidden_channel", primaryKeys = ["profileId", "providerId", "remoteId"])
data class ProfileHiddenChannelEntity(
    val profileId: Long,
    val providerId: Long,
    val remoteId: String,
    val categoryId: String?,
    val name: String,
)

/**
 * PROJECTION des chaînes masquées du profil COURANT (sans colonne profil). Recopiée depuis [ProfileHiddenChannelEntity]
 * au changement de profil et à chaque masquage : les requêtes de listes y font un `NOT EXISTS` sur la clé primaire, sans
 * paramètre de profil à faire passer partout, sans limite de variables SQLite et sans rien lire quand la table est vide.
 */
@Entity(tableName = "channel_hidden_now", primaryKeys = ["providerId", "remoteId"])
data class ChannelHiddenNowEntity(val providerId: Long, val remoteId: String)

/**
 * Ordre des catégories PAR PROFIL (place 1..n dans un type d'une source). `category.position` (colonne indexée derrière
 * `channel.catRank`) reste la PROJECTION de l'ordre du profil courant : elle est réécrite au changement de profil.
 */
@Entity(tableName = "profile_category_order", primaryKeys = ["profileId", "providerId", "kind", "remoteId"])
data class ProfileCategoryOrderEntity(
    val profileId: Long,
    val providerId: Long,
    val kind: String,
    val remoteId: String,
    val position: Int,
)

/** seq d'une chaîne masquée par le profil courant (numérotation par profil, voir ChannelNumbering). */
data class HiddenSeqRow(val providerId: Long, val categoryId: String?, val seq: Int)

data class HiddenKeyRow(val providerId: Long, val remoteId: String)

/** Filtre « masquée pour le profil courant » à greffer sur une requête de chaînes (alias de table selon la requête). */
object Hid {
    // Garde non corrélée (évaluée une fois) : aucune sonde par ligne tant que le profil ne masque rien.
    private const val GUARD = "NOT EXISTS (SELECT 1 FROM channel_hidden_now)"
    const val C = "($GUARD OR NOT EXISTS (SELECT 1 FROM channel_hidden_now hn WHERE hn.providerId = c.providerId AND hn.remoteId = c.remoteId))"
    const val CH = "($GUARD OR NOT EXISTS (SELECT 1 FROM channel_hidden_now hn WHERE hn.providerId = ch.providerId AND hn.remoteId = ch.remoteId))"
    /** Requête SANS alias (`FROM channel WHERE …`). */
    const val T = "($GUARD OR NOT EXISTS (SELECT 1 FROM channel_hidden_now hn WHERE hn.providerId = channel.providerId AND hn.remoteId = channel.remoteId))"
}

@Dao
interface ProfileViewDao {
    // ── Chaînes masquées par profil ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideAll(rows: List<ProfileHiddenChannelEntity>)

    @Query("DELETE FROM profile_hidden_channel WHERE profileId = :profileId AND providerId = :pid AND remoteId IN (:ids)")
    suspend fun unhide(profileId: Long, pid: Long, ids: List<String>)

    @Query("DELETE FROM profile_hidden_channel WHERE profileId = :profileId AND providerId = :pid")
    suspend fun clearHidden(profileId: Long, pid: Long)

    @Query("SELECT * FROM profile_hidden_channel WHERE providerId = :pid")
    suspend fun hiddenOfProvider(pid: Long): List<ProfileHiddenChannelEntity>

    @Query("SELECT * FROM profile_hidden_channel WHERE profileId = :profileId AND providerId = :pid")
    suspend fun hiddenOf(profileId: Long, pid: Long): List<ProfileHiddenChannelEntity>

    @Query("SELECT * FROM profile_hidden_channel WHERE profileId = :profileId AND providerId = :pid")
    fun observeHiddenOf(profileId: Long, pid: Long): Flow<List<ProfileHiddenChannelEntity>>

    // ── Projection du profil courant ──
    @Query("SELECT providerId AS providerId, remoteId AS remoteId FROM channel_hidden_now")
    suspend fun nowRows(): List<HiddenKeyRow>

    @Query("SELECT providerId AS providerId, remoteId AS remoteId FROM profile_hidden_channel WHERE profileId = :profileId")
    suspend fun rowsOfProfile(profileId: Long): List<HiddenKeyRow>

    /** Observe les seq des chaînes numérotées masquées par le profil courant (petit : seulement les masquées). */
    @Query("SELECT c.providerId AS providerId, c.categoryId AS categoryId, c.seq AS seq FROM channel c INNER JOIN channel_hidden_now hn ON hn.providerId = c.providerId AND hn.remoteId = c.remoteId WHERE c.seq > 0")
    fun observeHiddenSeqs(): Flow<List<HiddenSeqRow>>

    @Query("DELETE FROM channel_hidden_now")
    suspend fun clearNow()

    @Query("INSERT OR REPLACE INTO channel_hidden_now (providerId, remoteId) SELECT providerId, remoteId FROM profile_hidden_channel WHERE profileId = :profileId")
    suspend fun projectNow(profileId: Long)

    // ── Ordre des catégories par profil ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOrder(rows: List<ProfileCategoryOrderEntity>)

    @Query("DELETE FROM profile_category_order WHERE profileId = :profileId AND providerId = :pid AND kind = :kind")
    suspend fun clearOrder(profileId: Long, pid: Long, kind: String)

    @Query("SELECT * FROM profile_category_order WHERE providerId = :pid ORDER BY profileId, kind, position")
    suspend fun ordersOfProvider(pid: Long): List<ProfileCategoryOrderEntity>

    /** Sources dont l'ordre des catégories du Direct diffère de celui du profil [profileId] (rangs des chaînes à recalculer). */
    @Query("""
        SELECT DISTINCT providerId FROM category WHERE kind = 'LIVE' AND position !=
        COALESCE((SELECT o.position FROM profile_category_order o WHERE o.profileId = :profileId AND o.providerId = category.providerId AND o.kind = category.kind AND o.remoteId = category.remoteId), 0)
    """)
    suspend fun providersWithLiveOrderChange(profileId: Long): List<Long>

    /** Réécrit `category.position` d'après l'ordre du profil (0 = ordre du fournisseur) ; seules les lignes qui changent. */
    @Query("""
        UPDATE category SET position =
        COALESCE((SELECT o.position FROM profile_category_order o WHERE o.profileId = :profileId AND o.providerId = category.providerId AND o.kind = category.kind AND o.remoteId = category.remoteId), 0)
        WHERE position !=
        COALESCE((SELECT o.position FROM profile_category_order o WHERE o.profileId = :profileId AND o.providerId = category.providerId AND o.kind = category.kind AND o.remoteId = category.remoteId), 0)
    """)
    suspend fun projectPositions(profileId: Long)
}

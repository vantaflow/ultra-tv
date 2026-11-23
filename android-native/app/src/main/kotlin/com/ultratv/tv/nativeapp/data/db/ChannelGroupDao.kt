package com.ultratv.tv.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Groupe de chaînes PERSONNEL (« Sport », « Enfants »…) d'un profil, pour une source. Purement local : les réglages
 * d'affichage partagés par le Worker ne gardent que les champs qu'il connaît (un champ nouveau y serait perdu).
 */
@Entity(tableName = "channel_group", indices = [Index(value = ["profileId", "providerId", "position"])])
data class ChannelGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val providerId: Long,
    val name: String,
    /** Place du groupe dans la liste des catégories (après Favoris), à partir de 1. */
    val position: Int,
    val createdAt: Long,
)

/**
 * Chaîne rangée dans un groupe. Clé par identifiant fournisseur (et non `channel.id`) : la resynchronisation réécrit les
 * chaînes en changeant leur `id`. Une chaîne disparue du fournisseur reste notée et revient si elle réapparaît.
 */
@Entity(tableName = "channel_group_member", primaryKeys = ["groupId", "remoteId"], indices = [Index(value = ["groupId", "position"])])
data class ChannelGroupMemberEntity(
    val groupId: Long,
    val remoteId: String,
    /** Place dans le groupe, à partir de 1 (ordre d'ajout, modifiable). */
    val position: Int,
)

/** Groupe avec le nombre de ses chaînes réellement affichables (présentes, non masquées, hors catégories exclues). */
data class ChannelGroupRow(val id: Long, val name: String, val n: Int)

/** Filtre commun des chaînes d'un groupe (alias `c` = channel, `m` = channel_group_member). */
private const val GROUP_FILTER = "c.isSeparator = 0 AND ${Hid.C} AND (:useEx = 0 OR c.categoryId IS NULL OR c.categoryId NOT IN (:ex))"

@Dao
abstract class ChannelGroupDao {
    // ── Groupes ──
    @Insert
    abstract suspend fun insertGroup(g: ChannelGroupEntity): Long

    @Query("SELECT * FROM channel_group WHERE profileId = :profileId AND providerId = :pid ORDER BY position, id")
    abstract suspend fun groups(profileId: Long, pid: Long): List<ChannelGroupEntity>

    @Query("SELECT * FROM channel_group WHERE id = :id AND profileId = :profileId")
    abstract suspend fun group(profileId: Long, id: Long): ChannelGroupEntity?

    @Query("SELECT COUNT(*) FROM channel_group WHERE profileId = :profileId AND providerId = :pid")
    abstract suspend fun countGroups(profileId: Long, pid: Long): Int

    @Query("SELECT COALESCE(MAX(position), 0) FROM channel_group WHERE profileId = :profileId AND providerId = :pid")
    abstract suspend fun maxGroupPosition(profileId: Long, pid: Long): Int

    @Query("UPDATE channel_group SET name = :name WHERE id = :id AND profileId = :profileId")
    abstract suspend fun rename(profileId: Long, id: Long, name: String): Int

    @Query("DELETE FROM channel_group WHERE id = :id AND profileId = :profileId")
    abstract suspend fun deleteGroupRow(profileId: Long, id: Long): Int

    @Query("DELETE FROM channel_group_member WHERE groupId = :id")
    abstract suspend fun deleteMembersOf(id: Long)

    /** Supprime un groupe et ses chaînes (les chaînes restent dans leurs catégories). */
    @Transaction
    open suspend fun deleteGroup(profileId: Long, id: Long) {
        if (deleteGroupRow(profileId, id) > 0) deleteMembersOf(id)
    }

    /**
     * Groupes de la source pour le profil, avec le nombre de chaînes affichables (même filtre que les listes, pour que le
     * compteur annoncé corresponde à ce qui s'affiche). [useEx]/[ex] : catégories exclues (adultes d'un profil Enfants).
     */
    @Query("""
        SELECT g.id AS id, g.name AS name,
          (SELECT COUNT(*) FROM channel_group_member m JOIN channel c ON c.providerId = g.providerId AND c.remoteId = m.remoteId
           WHERE m.groupId = g.id AND $GROUP_FILTER) AS n
        FROM channel_group g WHERE g.profileId = :profileId AND g.providerId = :pid ORDER BY g.position, g.id
    """)
    abstract fun observeGroups(profileId: Long, pid: Long, useEx: Int, ex: List<String>): Flow<List<ChannelGroupRow>>

    // ── Chaînes d'un groupe ──
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMember(m: ChannelGroupMemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMembers(rows: List<ChannelGroupMemberEntity>)

    @Query("SELECT COALESCE(MAX(position), 0) FROM channel_group_member WHERE groupId = :id")
    abstract suspend fun maxMemberPosition(id: Long): Int

    @Query("SELECT COUNT(*) FROM channel_group_member WHERE groupId = :id")
    abstract suspend fun memberCount(id: Long): Int

    /** Ajoute la chaîne à la fin du groupe ; faux si elle y était déjà. */
    @Transaction
    open suspend fun addMember(id: Long, remoteId: String): Boolean =
        insertMember(ChannelGroupMemberEntity(id, remoteId, maxMemberPosition(id) + 1)) != -1L

    @Query("DELETE FROM channel_group_member WHERE groupId = :id AND remoteId = :remoteId")
    abstract suspend fun removeMember(id: Long, remoteId: String): Int

    /** Identifiants fournisseur du groupe dans l'ordre du groupe (chaînes absentes de la source comprises). */
    @Query("SELECT remoteId FROM channel_group_member WHERE groupId = :id ORDER BY position, remoteId")
    abstract suspend fun keys(id: Long): List<String>

    /** Réécrit l'ordre du groupe (places 1, 2, 3… dans l'ordre de [keys]). */
    @Transaction
    open suspend fun setOrder(id: Long, keys: List<String>) {
        deleteMembersOf(id)
        upsertMembers(keys.distinct().mapIndexed { i, k -> ChannelGroupMemberEntity(id, k, i + 1) })
    }

    /** Groupes du profil et de la source qui contiennent la chaîne. */
    @Query("""
        SELECT m.groupId FROM channel_group_member m JOIN channel_group g ON g.id = m.groupId
        WHERE g.profileId = :profileId AND g.providerId = :pid AND m.remoteId = :remoteId
    """)
    abstract suspend fun groupsOf(profileId: Long, pid: Long, remoteId: String): List<Long>

    /** Chaînes affichables du groupe, paginées, dans l'ordre du groupe (Direct). */
    @Query("""
        SELECT c.* FROM channel_group_member m JOIN channel c ON c.providerId = :pid AND c.remoteId = m.remoteId
        WHERE m.groupId = :id AND $GROUP_FILTER AND (:useLang = 0 OR c.lang IN (:langs))
        ORDER BY m.position, c.id
    """)
    abstract fun paged(pid: Long, id: Long, useLang: Int, langs: List<String>, useEx: Int, ex: List<String>): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Idem, seulement les chaînes qui ont un guide dans [from, to] (Guide TV, comme les favoris). */
    @Query("""
        SELECT c.* FROM channel_group_member m JOIN channel c ON c.providerId = :pid AND c.remoteId = m.remoteId
        WHERE m.groupId = :id AND $GROUP_FILTER
        AND EXISTS (SELECT 1 FROM epg e WHERE e.channelId = c.id AND e.endMs >= :from AND e.startMs <= :to)
        ORDER BY m.position, c.id
    """)
    abstract fun pagedWithEpg(pid: Long, id: Long, from: Long, to: Long, useEx: Int, ex: List<String>): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Chaînes affichables du groupe (file de zapping, tiroir du lecteur), dans l'ordre du groupe, au plus [limit]. */
    @Query("""
        SELECT c.* FROM channel_group_member m JOIN channel c ON c.providerId = :pid AND c.remoteId = m.remoteId
        WHERE m.groupId = :id AND $GROUP_FILTER
        ORDER BY m.position, c.id LIMIT :limit
    """)
    abstract suspend fun list(pid: Long, id: Long, useEx: Int, ex: List<String>, limit: Int): List<ChannelEntity>

    /** Profil supprimé : ses groupes partent avec lui. */
    @Query("DELETE FROM channel_group_member WHERE groupId IN (SELECT id FROM channel_group WHERE profileId = :profileId)")
    abstract suspend fun deleteMembersOfProfile(profileId: Long)

    @Query("DELETE FROM channel_group WHERE profileId = :profileId")
    abstract suspend fun deleteGroupsOfProfile(profileId: Long)

    @Transaction
    open suspend fun deleteForProfile(profileId: Long) {
        deleteMembersOfProfile(profileId)
        deleteGroupsOfProfile(profileId)
    }
}

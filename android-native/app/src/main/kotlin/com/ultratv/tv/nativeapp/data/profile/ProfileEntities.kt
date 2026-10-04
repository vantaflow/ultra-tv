package com.ultratv.tv.nativeapp.data.profile

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Identifiant du profil « Principal », créé par la migration 12 → 13 : il hérite de tout l'existant. */
const val DEFAULT_PROFILE_ID = 1L

/**
 * Un profil de visionnage. Les sources, le catalogue, l'EPG et les réglages techniques restent GLOBAUX ;
 * favoris, historique, catégories masquées, thème et préférences d'affichage sont PAR PROFIL.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Couleur d'avatar, ARGB (voir [ProfileColors]). */
    val color: Int,
    /** Initiale affichée dans l'avatar (1 caractère, majuscule). */
    val initial: String,
    val isKids: Boolean = false,
    /** SHA-256 hexadécimal du PIN, null = profil non protégé. Jamais le PIN en clair. */
    val pinHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Préférence d'un profil : paire clé / valeur texte (clés : [ProfilePrefs]). */
@Entity(tableName = "profile_pref", primaryKeys = ["profileId", "key"])
data class ProfilePrefEntity(
    val profileId: Long,
    val key: String,
    val value: String,
)

/** Catégorie masquée par un profil (clé `KIND:providerId:remoteId`, comme l'ancien HiddenCategoriesStore). */
@Entity(tableName = "profile_hidden_category", primaryKeys = ["profileId", "categoryKey"])
data class ProfileHiddenCategoryEntity(
    val profileId: Long,
    val categoryKey: String,
)

data class CategoryNameRow(
    val kind: String,
    val providerId: Long,
    val remoteId: String,
    val name: String,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Insert
    suspend fun insert(p: ProfileEntity): Long

    @Update
    suspend fun update(p: ProfileEntity)

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    // ── Préférences ──
    @Query("SELECT * FROM profile_pref WHERE profileId = :profileId")
    fun observePrefs(profileId: Long): Flow<List<ProfilePrefEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPref(p: ProfilePrefEntity)

    @Query("DELETE FROM profile_pref WHERE profileId = :profileId AND `key` = :key")
    suspend fun removePref(profileId: Long, key: String)

    // ── Catégories masquées ──
    @Query("SELECT categoryKey FROM profile_hidden_category WHERE profileId = :profileId")
    fun observeHidden(profileId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(h: ProfileHiddenCategoryEntity)

    @Query("DELETE FROM profile_hidden_category WHERE profileId = :profileId AND categoryKey = :key")
    suspend fun unhide(profileId: Long, key: String)

    @Query("DELETE FROM profile_hidden_category WHERE profileId = :profileId")
    suspend fun clearHidden(profileId: Long)

    // ── Nettoyage à la suppression d'un profil ──
    @Query("DELETE FROM profile_pref WHERE profileId = :id")
    suspend fun deletePrefsOf(id: Long)

    @Query("DELETE FROM profile_hidden_category WHERE profileId = :id")
    suspend fun deleteHiddenOf(id: Long)

    @Query("DELETE FROM favorite WHERE profileId = :id")
    suspend fun deleteFavoritesOf(id: Long)

    @Query("DELETE FROM watch_history WHERE profileId = :id")
    suspend fun deleteHistoryOf(id: Long)

    /** Noms de catégories, pour l'heuristique « adulte » du profil Enfants. */
    @Query("SELECT kind AS kind, providerId AS providerId, remoteId AS remoteId, name AS name FROM category")
    fun observeCategoryNames(): Flow<List<CategoryNameRow>>
}

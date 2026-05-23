package com.ultratv.tv.nativeapp.data.reminders

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Scheduled programme reminder. Keyed by (channelRemoteId, programmeStartMs)
 * so re-scheduling the same programme is idempotent. We trigger 60 seconds
 * before [startMs] and only need the metadata to render the notification.
 */
@Entity(
    tableName = "reminder",
    indices = [Index(value = ["channelRemoteId", "startMs"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val channelRemoteId: String,
    val channelName: String,
    val programmeTitle: String,
    val startMs: Long,
    val endMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE startMs > :nowMs ORDER BY startMs ASC")
    fun observeUpcoming(nowMs: Long = System.currentTimeMillis()): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE startMs > :nowMs ORDER BY startMs ASC")
    suspend fun upcoming(nowMs: Long = System.currentTimeMillis()): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReminderEntity): Long

    @Delete
    suspend fun delete(item: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminder WHERE endMs < :cutoffMs")
    suspend fun deleteExpired(cutoffMs: Long = System.currentTimeMillis())
}

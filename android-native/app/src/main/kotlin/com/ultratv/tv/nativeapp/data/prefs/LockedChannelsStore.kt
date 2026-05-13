package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lockedChsDs by preferencesDataStore(name = "locked_channels")

/**
 * Per-user set of locked channel IDs. A locked channel is gated by the
 * parental PIN — clicking it prompts the PIN dialog before playback. Lives
 * outside Room because the unit is "the user's choice", not catalogue data
 * (and surviving a destructive DB migration is a feature, not a bug).
 *
 * Key format: "${providerId}:${channelRemoteId}"
 */
@Singleton
class LockedChannelsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val KEY = stringSetPreferencesKey("locked_channel_ids")

    val locked: Flow<Set<String>> = ctx.lockedChsDs.data.map { it[KEY] ?: emptySet() }

    suspend fun set(providerId: Long, remoteId: String, locked: Boolean) {
        ctx.lockedChsDs.edit { p ->
            val cur = (p[KEY] ?: emptySet()).toMutableSet()
            val k = keyFor(providerId, remoteId)
            if (locked) cur.add(k) else cur.remove(k)
            p[KEY] = cur
        }
    }

    fun keyFor(providerId: Long, remoteId: String) = "$providerId:$remoteId"
}

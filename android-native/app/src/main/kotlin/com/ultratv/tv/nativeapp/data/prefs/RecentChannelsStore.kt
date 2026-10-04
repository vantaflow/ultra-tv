package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ultratv.tv.nativeapp.ui.player.zap.ZapLogic
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentChannelsDs by preferencesDataStore(name = "recent_channels")

/** Référence stable d'une chaîne (le rowid Room change à la resynchro, pas la paire source / stream_id). */
data class ChannelRef(val providerId: Long, val remoteId: String)

/** Les 20 dernières chaînes regardées, la plus récente en tête (liste « Récentes » du zapping). */
@Singleton
class RecentChannelsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val key = stringPreferencesKey("recent")

    val recent: Flow<List<ChannelRef>> = ctx.recentChannelsDs.data.map { decode(it[key]) }

    suspend fun record(ref: ChannelRef) {
        ctx.recentChannelsDs.edit { p ->
            p[key] = encode(ZapLogic.pushRecent(decode(p[key]).map { encodeOne(it) }, encodeOne(ref)).mapNotNull { decodeOne(it) })
        }
    }

    companion object {
        private const val SEP = "\n"
        fun encodeOne(r: ChannelRef) = "${r.providerId}|${r.remoteId}"
        fun decodeOne(s: String): ChannelRef? {
            val i = s.indexOf('|')
            val pid = s.substring(0, if (i < 0) 0 else i).toLongOrNull() ?: return null
            return ChannelRef(pid, s.substring(i + 1)).takeIf { it.remoteId.isNotBlank() }
        }
        fun encode(list: List<ChannelRef>) = list.joinToString(SEP) { encodeOne(it) }
        fun decode(raw: String?): List<ChannelRef> = raw?.split(SEP)?.mapNotNull { decodeOne(it) }.orEmpty()
    }
}

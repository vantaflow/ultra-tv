package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchDs by preferencesDataStore(name = "search_history")

/**
 * Persists the last 10 search queries. Order: most recent first; duplicates
 * are coalesced. Storage uses a single string with `` as separator
 * (cheaper than a StringSet which doesn't preserve insertion order).
 */
@Singleton
class SearchHistoryStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val KEY = stringPreferencesKey("recent_queries")
    private val SEP = ""

    val recent: Flow<List<String>> = ctx.searchDs.data.map { p ->
        p[KEY]?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun record(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        ctx.searchDs.edit { p ->
            val current = (p[KEY]?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList())
                .filter { it != q }
            p[KEY] = (listOf(q) + current).take(10).joinToString(SEP)
        }
    }

    suspend fun clear() {
        ctx.searchDs.edit { it[KEY] = "" }
    }
}

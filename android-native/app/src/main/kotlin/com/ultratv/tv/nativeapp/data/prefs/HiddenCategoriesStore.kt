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

private val Context.hiddenCatsDs by preferencesDataStore(name = "hidden_categories")

/**
 * Per-user set of category IDs (kind:providerId:remoteId) the user has chosen
 * to hide from the UI. Different from category.locked which is PIN-gated —
 * hidden categories simply don't appear at all in lists or chips.
 *
 * Key format: "${kind}:${providerId}:${remoteId}"  e.g. "MOVIE:3:42"
 */
@Singleton
class HiddenCategoriesStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val KEY = stringSetPreferencesKey("hidden_category_ids")

    val hidden: Flow<Set<String>> = ctx.hiddenCatsDs.data.map { it[KEY] ?: emptySet() }

    suspend fun set(id: String, hidden: Boolean) {
        ctx.hiddenCatsDs.edit { p ->
            val cur = (p[KEY] ?: emptySet()).toMutableSet()
            if (hidden) cur.add(id) else cur.remove(id)
            p[KEY] = cur
        }
    }

    suspend fun clearAll() {
        ctx.hiddenCatsDs.edit { it[KEY] = emptySet() }
    }

    fun keyFor(kind: String, providerId: Long, remoteId: String) = "$kind:$providerId:$remoteId"
}

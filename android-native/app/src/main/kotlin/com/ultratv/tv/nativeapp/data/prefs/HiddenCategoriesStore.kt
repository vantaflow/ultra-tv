package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hiddenCatsDs by preferencesDataStore(name = "hidden_categories")

/**
 * Catégories masquées PAR PROFIL (clé `kind:providerId:remoteId`, ex. « MOVIE:3:42 »). Différent de
 * category.locked (protégé par PIN) et de category.enabled (téléchargement, GLOBAL) : une catégorie masquée
 * disparaît simplement des listes et des puces. Pour un profil Enfants, les catégories adultes sont ajoutées d'office.
 * Les anciens choix (DataStore, avant les profils) sont importés une fois dans le profil Principal.
 */
@Singleton
class HiddenCategoriesStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val profiles: ProfileRepository,
) {
    private val legacyKey = stringSetPreferencesKey("hidden_category_ids")
    private val importedKey = booleanPreferencesKey("imported_into_profiles")

    val hidden: Flow<Set<String>> = profiles.hiddenCategoryKeys

    suspend fun set(id: String, hidden: Boolean) = profiles.setCategoryHidden(id, hidden)

    suspend fun clearAll() = profiles.clearHiddenCategories()

    /** Reprend l'ancienne liste globale dans le profil Principal (une seule fois). */
    suspend fun importLegacyOnce() {
        val p = ctx.hiddenCatsDs.data.first()
        if (p[importedKey] == true) return
        (p[legacyKey] ?: emptySet()).forEach { profiles.setCategoryHiddenFor(com.ultratv.tv.nativeapp.data.profile.DEFAULT_PROFILE_ID, it, true) }
        ctx.hiddenCatsDs.edit { it[importedKey] = true; it.remove(legacyKey) }
    }

    fun keyFor(kind: String, providerId: Long, remoteId: String) = "$kind:$providerId:$remoteId"
}

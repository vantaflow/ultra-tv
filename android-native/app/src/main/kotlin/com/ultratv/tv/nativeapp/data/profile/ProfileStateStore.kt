package com.ultratv.tv.nativeapp.data.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** État léger du sélecteur de profils : dernier profil actif et mode de démarrage. */
interface ProfileStateStore {
    val currentId: Flow<Long?>
    val startupMode: Flow<StartupMode>
    suspend fun setCurrentId(id: Long)
    suspend fun setStartupMode(m: StartupMode)
}

private val Context.profileStateDs by preferencesDataStore(name = "profile_state")

@Singleton
class DataStoreProfileStateStore @Inject constructor(@ApplicationContext private val ctx: Context) : ProfileStateStore {
    private val kId = longPreferencesKey("current_profile_id")
    private val kMode = stringPreferencesKey("startup_mode")
    override val currentId: Flow<Long?> = ctx.profileStateDs.data.map { it[kId] }
    override val startupMode: Flow<StartupMode> = ctx.profileStateDs.data.map { StartupMode.parse(it[kMode]) }
    override suspend fun setCurrentId(id: Long) { ctx.profileStateDs.edit { it[kId] = id } }
    override suspend fun setStartupMode(m: StartupMode) { ctx.profileStateDs.edit { it[kMode] = m.name } }
}

/** Implémentation mémoire (tests). */
class InMemoryProfileStateStore(initial: Long? = null) : ProfileStateStore {
    private val id = MutableStateFlow(initial)
    private val mode = MutableStateFlow(StartupMode.ALWAYS_ASK)
    override val currentId: Flow<Long?> = id
    override val startupMode: Flow<StartupMode> = mode
    override suspend fun setCurrentId(id: Long) { this.id.value = id }
    override suspend fun setStartupMode(m: StartupMode) { mode.value = m }
}

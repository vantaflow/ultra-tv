package com.ultratv.tv.nativeapp.data.remote

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.remoteDs by preferencesDataStore(name = "phone_remote")

/** Activation de la télécommande sur le téléphone et jeton aléatoire, gardés sur l'appareil (jamais envoyés ailleurs). */
@Singleton
class RemoteStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val tokenKey = stringPreferencesKey("token")

    val enabled: Flow<Boolean> = ctx.remoteDs.data.map { it[enabledKey] ?: false }

    suspend fun setEnabled(v: Boolean) { ctx.remoteDs.edit { it[enabledKey] = v } }

    /** Jeton courant ; créé au premier appel. */
    suspend fun token(): String {
        ctx.remoteDs.data.first()[tokenKey]?.takeIf { it.length >= 32 }?.let { return it }
        return regenerate()
    }

    /** Nouveau jeton : les anciens liens et QR codes cessent aussitôt de fonctionner. */
    suspend fun regenerate(): String {
        val t = RemoteNet.newToken()
        ctx.remoteDs.edit { it[tokenKey] = t }
        return t
    }
}

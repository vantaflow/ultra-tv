package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Nombre de connexions simultanées autorisées par source (`user_info.max_connections` du serveur Xtream).
 * Stocké à part de Room (pas de migration : la version du schéma reste 15). Tant que la valeur n'est pas lue,
 * on suppose 1, la plus prudente.
 */
@Singleton
class ProviderLimitsStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("provider_limits", Context.MODE_PRIVATE)

    fun maxConnections(providerId: Long): Int = sp.getInt("max_conn_$providerId", DEFAULT_MAX_CONNECTIONS).coerceAtLeast(1)

    fun setMaxConnections(providerId: Long, value: Int) { sp.edit().putInt("max_conn_$providerId", value.coerceAtLeast(1)).apply() }

    companion object {
        const val DEFAULT_MAX_CONNECTIONS = 1

        /** `user_info.max_connections` : le serveur l'envoie tantôt en chaîne (« 2 »), tantôt en nombre ; absent ou illisible → null. */
        fun parseMaxConnections(root: JsonObject): Int? {
            val info = root["user_info"] as? JsonObject ?: return null
            val v = info["max_connections"] as? JsonPrimitive ?: return null
            return (v.intOrNull ?: v.contentOrNull?.trim()?.toIntOrNull())?.takeIf { it >= 1 }
        }
    }
}

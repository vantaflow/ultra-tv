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

    /** Adresses de guide (`url-tvg` / `x-tvg-url`) de l'en-tête de la playlist M3U de la source, séparées par un saut de ligne. */
    fun epgUrls(providerId: Long): List<String> = sp.getString("epg_urls_$providerId", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun setEpgUrls(providerId: Long, urls: List<String>) { sp.edit().putString("epg_urls_$providerId", urls.joinToString("\n")).apply() }

    /**
     * Guide personnalisé de la source (adresse + mode remplacer / compléter). Stocké ici, par identifiant de source, comme
     * [epgUrls] : l'en-tête de la playlist réécrit [epgUrls] à chaque synchro, pas ceci. Aucune migration Room.
     */
    fun customEpg(providerId: Long): com.ultratv.tv.nativeapp.data.xmltv.CustomGuide? {
        val url = sp.getString("custom_epg_url_$providerId", "").orEmpty().trim()
        if (url.isEmpty()) return null
        return com.ultratv.tv.nativeapp.data.xmltv.CustomGuide(url, com.ultratv.tv.nativeapp.data.xmltv.GuideMode.parse(sp.getString("custom_epg_mode_$providerId", null)))
    }

    /** null : retire le guide personnalisé. */
    fun setCustomEpg(providerId: Long, guide: com.ultratv.tv.nativeapp.data.xmltv.CustomGuide?) {
        sp.edit().apply {
            if (guide == null) { remove("custom_epg_url_$providerId"); remove("custom_epg_mode_$providerId") }
            else { putString("custom_epg_url_$providerId", guide.url.trim()); putString("custom_epg_mode_$providerId", guide.mode.name) }
        }.apply()
    }

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

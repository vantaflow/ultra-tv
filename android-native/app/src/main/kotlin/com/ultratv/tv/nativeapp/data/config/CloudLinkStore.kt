package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liens source locale ↔ source cloud, dernière version connue (ETag) et résumé du compte. Stocké à part de Room
 * (aucune migration : le schéma reste à la version 15). Aucun secret ici : les identifiants restent dans la table des sources.
 */
@Singleton
class CloudLinkStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("cloud_links", Context.MODE_PRIVATE)

    fun cloudIdOf(localId: Long): String? = sp.getString("link_$localId", null)
    fun localIdOf(cloudId: String): Long? = sp.getAll().entries.firstOrNull { it.key.startsWith("link_") && it.value == cloudId }?.key?.removePrefix("link_")?.toLongOrNull()
    fun appliedName(cloudId: String): String? = sp.getString("name_$cloudId", null)
    fun sharedWith(cloudId: String): Int = sp.getInt("shared_$cloudId", 0)

    fun link(localId: Long, cloudId: String, name: String, sharedWith: Int) {
        sp.edit().putString("link_$localId", cloudId).putString("name_$cloudId", name).putInt("shared_$cloudId", sharedWith).apply()
    }

    fun unlink(localId: Long) {
        val cid = cloudIdOf(localId)
        sp.edit().remove("link_$localId").apply { if (cid != null) { remove("name_$cid"); remove("shared_$cid") } }.apply()
    }

    /** Réglages d'affichage : horodatage de la dernière version appliquée/publiée et son empreinte (anti ping-pong). */
    fun prefsAt(cloudId: String): Long = sp.getLong("prefs_at_$cloudId", 0L)
    fun prefsPrint(cloudId: String): String? = sp.getString("prefs_fp_$cloudId", null)
    fun setPrefs(cloudId: String, at: Long, print: String) { sp.edit().putLong("prefs_at_$cloudId", at).putString("prefs_fp_$cloudId", print).apply() }

    /** Réglages reçus pas encore appliqués (catalogue de la source pas encore chargé). */
    fun pendingPrefs(cloudId: String): String? = sp.getString("prefs_pending_$cloudId", null)
    fun setPendingPrefs(cloudId: String, json: String?) { sp.edit().apply { if (json == null) remove("prefs_pending_$cloudId") else putString("prefs_pending_$cloudId", json) }.apply() }
    fun pendingPrefsIds(): List<String> = sp.getAll().keys.filter { it.startsWith("prefs_pending_") }.map { it.removePrefix("prefs_pending_") }

    /** Favoris tels que connus après le dernier échange (JSON), et horodatage du dernier historique envoyé. */
    fun stateFavs(cloudId: String): String? = sp.getString("st_fav_$cloudId", null)
    fun setStateFavs(cloudId: String, json: String) { sp.edit().putString("st_fav_$cloudId", json).apply() }
    fun stateHistSince(cloudId: String): Long = sp.getLong("st_hist_$cloudId", 0L)
    fun setStateHistSince(cloudId: String, at: Long) { sp.edit().putLong("st_hist_$cloudId", at).apply() }

    fun etag(): String? = sp.getString("etag", null)
    fun setEtag(v: String?) { sp.edit().putString("etag", v).apply() }
    /** Oublie la version connue : la prochaine synchro relit tout (appairage, changement de Worker). */
    fun resetVersion() { sp.edit().remove("etag").apply() }

    var lastSyncAt: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) { sp.edit().putLong("last_sync", v).apply() }

    fun saveDevices(selfId: String?, devices: List<CloudDevice>) {
        val a = JSONArray(); devices.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("model", it.label)) }
        sp.edit().putString("devices", a.toString()).putString("self", selfId).apply()
    }

    fun devices(): List<CloudDevice> = runCatching {
        val a = JSONArray(sp.getString("devices", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { d -> CloudDevice(d.optString("id"), d.optString("name"), d.optString("model")) } }
    }.getOrDefault(emptyList())

    fun selfId(): String? = sp.getString("self", null)

    fun clearAll() { sp.edit().clear().apply() }
}

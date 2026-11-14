package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Chaîne masquée par l'utilisateur : identifiant du fournisseur, catégorie (pour la recharger) et nom (pour la retrouver). */
data class HiddenChannel(val id: String, val cat: String?, val name: String)

/**
 * Chaînes masquées, par source. Une chaîne masquée est retirée de la base et ignorée par la synchro : elle disparaît
 * partout (liste, zapping, guide, recherche, accueil). Partagée entre appareils avec les réglages d'affichage.
 */
@Singleton
class HiddenChannelsStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("hidden_channels", Context.MODE_PRIVATE)
    private val version = MutableStateFlow(0)

    fun list(pid: Long): List<HiddenChannel> = parse(sp.getString(key(pid), null))
    fun ids(pid: Long): Set<String> = list(pid).mapTo(HashSet()) { it.id }
    fun observe(pid: Long): Flow<List<HiddenChannel>> = version.map { list(pid) }

    fun add(pid: Long, items: List<HiddenChannel>) {
        if (items.isEmpty()) return
        val cur = list(pid).associateBy { it.id }.toMutableMap()
        items.forEach { cur[it.id] = it }
        write(pid, cur.values.toList())
    }
    fun remove(pid: Long, ids: Collection<String>) {
        if (ids.isEmpty()) return
        write(pid, list(pid).filter { it.id !in ids })
    }
    fun replace(pid: Long, items: List<HiddenChannel>) = write(pid, items.distinctBy { it.id })

    private fun write(pid: Long, items: List<HiddenChannel>) {
        sp.edit().putString(key(pid), toJson(items)).apply()
        version.value++
    }
    private fun key(pid: Long) = "p$pid"

    companion object {
        const val MAX = 5000
        fun toJson(items: List<HiddenChannel>): String = JSONArray().apply {
            items.take(MAX).forEach { h -> put(JSONObject().put("i", h.id).put("c", h.cat ?: JSONObject.NULL).put("n", h.name.take(120))) }
        }.toString()
        fun parse(raw: String?): List<HiddenChannel> = runCatching {
            val a = JSONArray(raw ?: return emptyList())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("i").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                HiddenChannel(id, if (o.isNull("c")) null else o.optString("c").takeIf { it.isNotEmpty() }, o.optString("n"))
            }
        }.getOrDefault(emptyList())
    }
}

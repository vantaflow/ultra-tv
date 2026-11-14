package com.ultratv.tv.nativeapp.data.profile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Profil tel que partagé entre appareils : nom, couleur, profil Enfants, date de modification, `on = false` = supprimé. Jamais de PIN. */
data class SharedProfile(val n: String, val c: Int, val k: Boolean, val at: Long, val on: Boolean = true)

/**
 * Journal local des modifications de profils (date de la dernière modification par nom, et suppressions) : c'est lui qui
 * dit, face aux autres appareils, quelle version d'un profil est la plus récente.
 */
@Singleton
class ProfileSyncLog @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("profile_sync", Context.MODE_PRIVATE)

    @Synchronized fun all(): Map<String, SharedProfile> = ProfileSyncLogic.parseLog(sp.getString("log", null))
    @Synchronized fun set(name: String, at: Long, on: Boolean) {
        val m = all().toMutableMap()
        m[ProfileSyncLogic.key(name)] = SharedProfile(name, 0, false, at, on)
        sp.edit().putString("log", ProfileSyncLogic.logJson(m.values)).apply()
    }
    fun touch(name: String) = set(name, System.currentTimeMillis(), true)
    fun tombstone(name: String) = set(name, System.currentTimeMillis(), false)
}

/** Règles pures de la synchro des profils (testées à part). */
object ProfileSyncLogic {
    fun key(name: String) = name.trim().lowercase()

    sealed class Action {
        data class Create(val p: SharedProfile) : Action()
        data class Update(val id: Long, val p: SharedProfile) : Action()
        data class Delete(val id: Long, val p: SharedProfile) : Action()
    }

    /** Ce que cet appareil envoie : ses profils (date du journal, sinon de création) et ses suppressions. */
    fun local(profiles: List<ProfileEntity>, log: Map<String, SharedProfile>): List<SharedProfile> {
        val names = profiles.map { key(it.name) }.toSet()
        return profiles.map { SharedProfile(it.name, it.color, it.isKids, log[key(it.name)]?.takeIf { l -> l.on }?.at ?: it.createdAt, true) } +
            log.values.filter { !it.on && key(it.n) !in names }
    }

    /** Ce qu'il faut appliquer ici d'après la liste fusionnée reçue : le plus récent gagne, par nom. */
    fun plan(profiles: List<ProfileEntity>, log: Map<String, SharedProfile>, remote: List<SharedProfile>): List<Action> = buildList {
        for (r in remote) {
            val k = key(r.n)
            val l = profiles.firstOrNull { key(it.name) == k }
            val localAt = log[k]?.at ?: l?.createdAt ?: 0L
            if (r.on) {
                if (l == null) { if (r.at > localAt || log[k] == null) add(Action.Create(r)) }
                else if (r.at > localAt && (l.color != r.c || l.isKids != r.k)) add(Action.Update(l.id, r))
            } else if (l != null && r.at > localAt) add(Action.Delete(l.id, r))
        }
    }

    fun toJson(list: List<SharedProfile>): String = JSONObject().put("profiles", JSONArray().apply {
        list.forEach { p -> put(JSONObject().put("n", p.n).put("c", p.c).put("k", p.k).put("at", p.at).put("on", p.on)) }
    }).toString()

    fun parse(raw: String?): List<SharedProfile> = runCatching {
        val a = JSONObject(raw ?: return emptyList()).optJSONArray("profiles") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val n = o.optString("n").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SharedProfile(n, o.optInt("c"), o.optBoolean("k"), o.optLong("at"), o.optBoolean("on", true))
        }
    }.getOrDefault(emptyList())

    internal fun logJson(items: Collection<SharedProfile>): String = JSONArray().apply {
        items.forEach { p -> put(JSONObject().put("n", p.n).put("at", p.at).put("on", p.on)) }
    }.toString()

    internal fun parseLog(raw: String?): Map<String, SharedProfile> = runCatching {
        val a = JSONArray(raw ?: return emptyMap())
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> SharedProfile(o.optString("n"), 0, false, o.optLong("at"), o.optBoolean("on", true)) } }
            .filter { it.n.isNotBlank() }.associateBy { key(it.n) }
    }.getOrDefault(emptyMap())
}

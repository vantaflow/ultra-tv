package com.ultratv.tv.nativeapp.data.profile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Profil tel que partagé entre appareils : nom, couleur, profil Enfants, date de modification, `on = false` = supprimé. Jamais de PIN. */
/** [from] : ancien nom quand le profil vient d'être renommé (les autres appareils renomment au lieu de supprimer / recréer). */
data class SharedProfile(val n: String, val c: Int, val k: Boolean, val at: Long, val on: Boolean = true, val from: String? = null)

/**
 * Journal local des modifications de profils (date de la dernière modification par nom, et suppressions) : c'est lui qui
 * dit, face aux autres appareils, quelle version d'un profil est la plus récente.
 */
@Singleton
class ProfileSyncLog @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("profile_sync", Context.MODE_PRIVATE)

    @Synchronized fun all(): Map<String, SharedProfile> = ProfileSyncLogic.parseLog(sp.getString("log", null))
    @Synchronized fun set(name: String, at: Long, on: Boolean, from: String? = null) {
        val m = all().toMutableMap()
        m[ProfileSyncLogic.key(name)] = SharedProfile(name, 0, false, at, on, from)
        sp.edit().putString("log", ProfileSyncLogic.logJson(m.values)).apply()
    }
    fun touch(name: String, from: String? = null) = set(name, System.currentTimeMillis(), true, from)
    fun tombstone(name: String) = set(name, System.currentTimeMillis(), false)
}

/** Règles pures de la synchro des profils (testées à part). */
object ProfileSyncLogic {
    fun key(name: String) = name.trim().lowercase()

    sealed class Action {
        data class Create(val p: SharedProfile) : Action()
        data class Update(val id: Long, val p: SharedProfile) : Action()
        data class Delete(val id: Long, val p: SharedProfile) : Action()
        /** Renommé ailleurs : on renomme ici (favoris, historique et réglages du profil gardés). */
        data class Rename(val id: Long, val p: SharedProfile) : Action()
    }

    /** Ce que cet appareil envoie : ses profils (date du journal, sinon de création) et ses suppressions. */
    fun local(profiles: List<ProfileEntity>, log: Map<String, SharedProfile>): List<SharedProfile> {
        val names = profiles.map { key(it.name) }.toSet()
        return profiles.map { p -> val l = log[key(p.name)]?.takeIf { it.on }; SharedProfile(p.name, p.color, p.isKids, l?.at ?: p.createdAt, true, l?.from) } +
            log.values.filter { !it.on && key(it.n) !in names }
    }

    /** Ce qu'il faut appliquer ici d'après la liste fusionnée reçue : le plus récent gagne, par nom. */
    fun plan(profiles: List<ProfileEntity>, log: Map<String, SharedProfile>, remote: List<SharedProfile>): List<Action> = buildList {
        // Renommages d'abord : « Papa » devenu « Papa 2 » ailleurs → on renomme le profil local au lieu de le supprimer
        // (sa tombe « Papa ») et d'en créer un vide (« Papa 2 »).
        val renamed = mutableSetOf<String>()
        for (r in remote) {
            val from = r.from ?: continue
            if (!r.on || profiles.any { key(it.name) == key(r.n) }) continue
            val l = profiles.firstOrNull { key(it.name) == key(from) } ?: continue
            if (r.at <= (log[key(from)]?.at ?: l.createdAt)) continue
            add(Action.Rename(l.id, r)); renamed += key(r.n); renamed += key(from)
        }
        for (r in remote) {
            if (key(r.n) in renamed) continue
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
        list.forEach { p -> put(JSONObject().put("n", p.n).put("c", p.c).put("k", p.k).put("at", p.at).put("on", p.on).apply { p.from?.let { put("f", it) } }) }
    }).toString()

    fun parse(raw: String?): List<SharedProfile> = runCatching {
        val a = JSONObject(raw ?: return emptyList()).optJSONArray("profiles") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val n = o.optString("n").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SharedProfile(n, o.optInt("c"), o.optBoolean("k"), o.optLong("at"), o.optBoolean("on", true), o.optString("f").trim().takeIf { it.isNotEmpty() })
        }
    }.getOrDefault(emptyList())

    internal fun logJson(items: Collection<SharedProfile>): String = JSONArray().apply {
        items.forEach { p -> put(JSONObject().put("n", p.n).put("at", p.at).put("on", p.on).apply { p.from?.let { put("f", it) } }) }
    }.toString()

    internal fun parseLog(raw: String?): Map<String, SharedProfile> = runCatching {
        val a = JSONArray(raw ?: return emptyMap())
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> SharedProfile(o.optString("n"), 0, false, o.optLong("at"), o.optBoolean("on", true), o.optString("f").trim().takeIf { it.isNotEmpty() }) } }
            .filter { it.n.isNotBlank() }.associateBy { key(it.n) }
    }.getOrDefault(emptyMap())
}

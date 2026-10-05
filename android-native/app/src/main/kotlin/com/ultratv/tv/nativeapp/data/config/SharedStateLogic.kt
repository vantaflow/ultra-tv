package com.ultratv.tv.nativeapp.data.config

import org.json.JSONArray
import org.json.JSONObject

/** Favori partagé : profil (par NOM), type, identifiant du fournisseur ; `on = false` = retiré (tombe). */
data class SharedFav(val p: String, val k: String, val r: String, val on: Boolean, val at: Long) { val key get() = "$p|$k|$r" }

/** Position / dernier vu partagé. */
data class SharedHist(val p: String, val k: String, val r: String, val t: String, val img: String?, val pos: Long, val dur: Long, val at: Long, val par: String?) {
    val key get() = "$p|$k|$r"
}

/** Règles pures de l'état partagé (favoris, reprises) : testables sans Android. */
object SharedStateLogic {
    /**
     * Met à jour l'état connu des favoris à partir de l'état local : un favori apparu est « on » maintenant,
     * un favori disparu devient une tombe datée de maintenant (sinon un autre appareil le recréerait).
     */
    fun localFavorites(known: Map<String, SharedFav>, current: Set<Triple<String, String, String>>, now: Long): Map<String, SharedFav> {
        val out = known.toMutableMap()
        val currentKeys = current.map { (p, k, r) -> "$p|$k|$r" }.toSet()
        for ((p, k, r) in current) { val key = "$p|$k|$r"; val o = out[key]; if (o == null || !o.on) out[key] = SharedFav(p, k, r, true, now) }
        for ((key, o) in known) if (o.on && key !in currentKeys) out[key] = o.copy(on = false, at = now)
        return out
    }

    /** Entrées distantes plus récentes que l'état connu : à appliquer localement. */
    fun remoteFavChanges(known: Map<String, SharedFav>, remote: List<SharedFav>): List<SharedFav> =
        remote.filter { e -> known[e.key].let { it == null || e.at > it.at || (e.at == it.at && e.on != it.on && !e.on) } }

    fun favToJson(e: SharedFav) = JSONObject().put("p", e.p).put("k", e.k).put("r", e.r).put("on", e.on).put("at", e.at)
    fun histToJson(e: SharedHist) = JSONObject().put("p", e.p).put("k", e.k).put("r", e.r).put("t", e.t).put("img", e.img ?: JSONObject.NULL)
        .put("pos", e.pos).put("dur", e.dur).put("at", e.at).put("par", e.par ?: JSONObject.NULL)

    fun parseFav(o: JSONObject): SharedFav? {
        val p = o.optString("p"); val k = o.optString("k"); val r = o.optString("r"); val at = o.optLong("at")
        return if (p.isBlank() || k.isBlank() || r.isBlank() || at <= 0) null else SharedFav(p, k, r, o.optBoolean("on", true), at)
    }

    fun parseHist(o: JSONObject): SharedHist? {
        val p = o.optString("p"); val k = o.optString("k"); val r = o.optString("r"); val at = o.optLong("at")
        if (p.isBlank() || k.isBlank() || r.isBlank() || at <= 0) return null
        fun s(n: String) = if (o.isNull(n)) null else o.optString(n).takeIf { it.isNotBlank() }
        return SharedHist(p, k, r, o.optString("t"), s("img"), o.optLong("pos"), o.optLong("dur"), at, s("par"))
    }

    fun favsToJson(m: Collection<SharedFav>) = JSONArray().apply { m.forEach { put(favToJson(it)) } }
    fun parseFavs(a: JSONArray?): List<SharedFav> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parseFav) }
    fun parseHists(a: JSONArray?): List<SharedHist> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parseHist) }
}

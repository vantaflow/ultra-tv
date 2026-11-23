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
    /** Nom du profil par défaut sans nom, partagé avec le bureau (qui applique la même règle). */
    const val DEFAULT_PROFILE = "Principal"

    /** Nom partagé d'un profil : rogné, « Principal » s'il est vide (sinon le bureau et la TV ne se rapprochaient pas). */
    fun profileName(raw: String): String = raw.trim().ifBlank { DEFAULT_PROFILE }

    /** Identifiant -> nom partagé. */
    fun nameOf(profiles: List<Pair<Long, String>>): Map<Long, String> = profiles.associate { (id, n) -> id to profileName(n) }

    /** Nom partagé (sans casse) -> identifiant. */
    fun idOf(profiles: List<Pair<Long, String>>): Map<String, Long> = profiles.associate { (id, n) -> profileName(n).lowercase() to id }

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

    /** Chaîne / titre du catalogue local pour un identifiant reçu : nom, image et adresse. */
    data class LocalItem(val title: String, val image: String?, val streamUrl: String)

    /** Ligne d'historique locale de même identifiant : nom, image et adresse. */
    data class LocalRow(val title: String, val image: String?, val streamUrl: String)

    /**
     * Nom, image et adresse d'une ligne d'historique reçue d'un autre appareil (pure, testée).
     *
     * Avant : nom distant + image et adresse de la ligne LOCALE de même identifiant, quel que soit son nom. Quand l'identifiant
     * désigne désormais une autre chaîne (fournisseur qui renumérote, image distante absente ou locale à l'appareil), la carte
     * « Dernières chaînes regardées » montrait « Geo National Wild » avec le logo — et l'adresse — de « La chaîne météo ».
     * Règle : une image et une adresse locales (catalogue [catalog] puis ligne locale [local]) ne sont reprises que si elles
     * portent le MÊME nom que la ligne reçue ; sinon l'image distante seule et aucune adresse (ligne non affichée à
     * l'accueil plutôt qu'une carte qui lance une autre chaîne).
     */
    fun mergeHist(e: SharedHist, local: LocalRow?, catalog: LocalItem?): Triple<String, String?, String> {
        val title = e.t.ifBlank { catalog?.title ?: local?.title.orEmpty() }
        val cat = catalog?.takeIf { e.t.isBlank() || sameTitle(it.title, e.t) }
        val loc = local?.takeIf { e.t.isBlank() || sameTitle(it.title, e.t) }
        val image = cat?.image ?: e.img ?: loc?.image
        val url = cat?.streamUrl?.takeIf { it.isNotBlank() } ?: loc?.streamUrl?.takeIf { it.isNotBlank() } ?: ""
        return Triple(title, image, url)
    }

    /** Même nom à la casse, aux espaces et à la ponctuation près (« Serie Club » = « SERIE-CLUB »). */
    internal fun sameTitle(a: String, b: String): Boolean {
        fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        return norm(a) == norm(b)
    }
}

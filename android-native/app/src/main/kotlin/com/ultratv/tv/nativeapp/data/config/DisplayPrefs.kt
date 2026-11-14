package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.repo.LanguageDetector
import org.json.JSONArray
import org.json.JSONObject

/**
 * Réglages d'affichage partagés d'une source entre appareils (Worker : `providers[].prefs`).
 * Convention commune TV / bureau : langues en minuscules, « other » = sans langue détectée, « multi » = multilingue,
 * `null` = toutes les langues ; `disabled` = identifiants de catégorie du FOURNISSEUR, par type.
 */
data class CloudPrefs(
    val langs: List<String>?,
    val disabled: Map<String, List<String>>,
    val updatedAt: Long,
    /** Ordre des catégories par type (clé du protocole) ; `null` = non transmis (ancienne version : on n'y touche pas). */
    val order: Map<String, List<String>>? = null,
    /** Chaînes masquées ; `null` = non transmis. */
    val hidden: List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel>? = null,
    /** Ordre personnel des chaînes : par profil (NOM) et liste (catégorie ou favoris) ; `null` = non transmis. */
    val chOrder: List<ChannelOrderShare>? = null,
    /** Catégories masquées PAR PROFIL (nom) : type (clé du protocole) → identifiants ; `null` = non transmis. */
    val pHidden: List<ProfileHiddenShare>? = null,
)

/** Catégories masquées d'un profil [p] (nom) pour le type [k] (live / movie / series). */
data class ProfileHiddenShare(val p: String, val k: String, val ids: List<String>)

/** Ordre des chaînes d'une liste pour un profil, partagé entre appareils : [p] nom du profil, [s] liste, [k] identifiants dans l'ordre. */
data class ChannelOrderShare(val p: String, val s: String, val k: List<String>)

/** État local équivalent (langues du profil + catégories désactivées de la source). */
data class LocalDisplayPrefs(
    val selected: Set<String>,
    val includeMulti: Boolean,
    val includeUnknown: Boolean,
    val disabled: Map<String, Set<String>>,
    /** Ordre des catégories par type Android (identifiants du fournisseur, ordre affiché). */
    val order: Map<String, List<String>> = emptyMap(),
    val hidden: List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel> = emptyList(),
    val chOrder: List<ChannelOrderShare> = emptyList(),
    val pHidden: List<ProfileHiddenShare> = emptyList(),
)

object DisplayPrefs {
    const val OTHER = "other"
    const val MULTI = "multi"
    const val MAX_IDS = 5000

    /** Type de catégorie Android ↔ clé du protocole. */
    val KINDS = linkedMapOf("LIVE" to "live", "MOVIE" to "movie", "SERIES" to "series")

    fun langsToCloud(p: LocalDisplayPrefs): List<String>? {
        if (p.selected.isEmpty()) return null
        return buildList {
            addAll(p.selected.map { it.lowercase() }.filter { it.isNotBlank() }.sorted())
            if (p.includeMulti) add(MULTI)
            if (p.includeUnknown) add(OTHER)
        }.distinct()
    }

    /** Langues reçues → (sélection, multilingue, sans langue). `null` = toutes : sélection vide, tout visible. */
    fun langsFromCloud(langs: List<String>?): Triple<Set<String>, Boolean, Boolean> {
        if (langs == null) return Triple(emptySet(), true, true)
        val l = langs.map { it.lowercase() }
        val selected = l.filter { it != MULTI && it != OTHER && it != LanguageDetector.MULTI.lowercase() && it.isNotBlank() }.toSet()
        // Liste vide ou réduite à multi/other : pas de langue explicite → tout visible (en cas de doute on affiche).
        if (selected.isEmpty()) return Triple(emptySet(), true, true)
        return Triple(selected, MULTI in l, OTHER in l)
    }

    fun toCloud(p: LocalDisplayPrefs, updatedAt: Long): CloudPrefs = CloudPrefs(
        langs = langsToCloud(p),
        disabled = KINDS.entries.associate { (k, c) -> c to (p.disabled[k] ?: emptySet()).sorted() },
        updatedAt = updatedAt,
        order = KINDS.entries.associate { (k, c) -> c to (p.order[k] ?: emptyList()) },
        hidden = p.hidden.sortedBy { it.id },
        chOrder = p.chOrder.sortedWith(compareBy({ it.p }, { it.s })),
        pHidden = p.pHidden.sortedWith(compareBy({ it.p }, { it.k })),
    )

    /** Empreinte stable de l'état (sans l'horodatage) : évite de republier ce qu'on vient d'appliquer ou d'envoyer. */
    fun fingerprint(p: CloudPrefs): String {
        val langs = p.langs?.sorted()?.joinToString(",") ?: "*"
        val dis = KINDS.values.joinToString("|") { k -> k + ":" + (p.disabled[k] ?: emptyList()).sorted().joinToString(",") }
        // Ordre (tel quel : c'est lui qui compte) et chaînes masquées.
        val ord = KINDS.values.joinToString("|") { k -> k + ":" + (p.order?.get(k) ?: emptyList()).joinToString(",") }
        val hid = (p.hidden ?: emptyList()).map { it.id }.sorted().joinToString(",")
        val cho = (p.chOrder ?: emptyList()).joinToString("|") { it.p.lowercase() + "/" + it.s + ":" + it.k.joinToString(",") }
        val phi = (p.pHidden ?: emptyList()).joinToString("|") { it.p.lowercase() + "/" + it.k + ":" + it.ids.sorted().joinToString(",") }
        return "$langs#$dis#$ord#$hid#$cho#$phi".hashCode().toString(16) + ":" + (langs.length + dis.length + ord.length + hid.length + cho.length + phi.length)
    }

    fun tooLarge(p: CloudPrefs): Boolean = p.disabled.values.any { it.size > MAX_IDS } || (p.order?.values?.any { it.size > MAX_IDS } ?: false) || (p.hidden?.size ?: 0) > MAX_IDS

    fun toJson(p: CloudPrefs): String = JSONObject().apply {
        put("langs", p.langs?.let { JSONArray(it) } ?: JSONObject.NULL)
        put("disabled", JSONObject().apply { KINDS.values.forEach { k -> put(k, JSONArray(p.disabled[k] ?: emptyList<String>())) } })
        put("updatedAt", p.updatedAt)
        p.order?.let { o -> put("order", JSONObject().apply { KINDS.values.forEach { k -> put(k, JSONArray(o[k] ?: emptyList<String>())) } }) }
        p.hidden?.let { h -> put("hidden", JSONArray(com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore.toJson(h))) }
        p.chOrder?.let { l -> put("chOrder", JSONArray().apply { l.forEach { c -> put(JSONObject().put("p", c.p).put("s", c.s).put("k", JSONArray(c.k))) } }) }
        p.pHidden?.let { l -> put("pHidden", JSONArray().apply { l.forEach { c -> put(JSONObject().put("p", c.p).put("k", c.k).put("ids", JSONArray(c.ids))) } }) }
    }.toString()

    fun parse(o: JSONObject?): CloudPrefs? {
        if (o == null) return null
        val updatedAt = o.optLong("updatedAt")
        if (updatedAt <= 0) return null
        val langs = if (o.isNull("langs")) null else o.optJSONArray("langs")?.let { a -> (0 until a.length()).map { a.optString(it) } }
        val d = o.optJSONObject("disabled")
        val disabled = KINDS.values.associateWith { k -> d?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() }
        val order = o.optJSONObject("order")?.let { od -> KINDS.values.associateWith { k -> od.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } } ?: emptyList() } }
        val hidden = o.optJSONArray("hidden")?.let { com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore.parse(it.toString()) }
        val chOrder = o.optJSONArray("chOrder")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val c = a.optJSONObject(i) ?: return@mapNotNull null
                val k = c.optJSONArray("k")?.let { ka -> (0 until ka.length()).map { ka.optString(it) }.filter { it.isNotEmpty() } } ?: return@mapNotNull null
                ChannelOrderShare(c.optString("p").takeIf { it.isNotBlank() } ?: return@mapNotNull null, c.optString("s").takeIf { it.isNotEmpty() } ?: return@mapNotNull null, k)
            }
        }
        val pHidden = o.optJSONArray("pHidden")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val c = a.optJSONObject(i) ?: return@mapNotNull null
                val ids = c.optJSONArray("ids")?.let { ia -> (0 until ia.length()).map { ia.optString(it) }.filter { it.isNotEmpty() } } ?: return@mapNotNull null
                ProfileHiddenShare(c.optString("p").takeIf { it.isNotBlank() } ?: return@mapNotNull null, c.optString("k").takeIf { it in KINDS.values } ?: return@mapNotNull null, ids)
            }
        }
        return CloudPrefs(langs, disabled, updatedAt, order, hidden, chOrder, pHidden)
    }
}

/**
 * Changements de réglages d'affichage FAITS PAR L'UTILISATEUR (écran Catégories, Réglages › Langues).
 * Seuls ces changements sont publiés : une synchro ou l'application de réglages reçus ne doit rien renvoyer.
 * `ACTIVE` = la source affichée.
 */
object DisplayPrefsEvents {
    const val ACTIVE = -1L
    private val _changes = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val changes: kotlinx.coroutines.flow.SharedFlow<Long> = _changes
    fun changed(providerId: Long = ACTIVE) { _changes.tryEmit(providerId) }
}

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
)

/** État local équivalent (langues du profil + catégories désactivées de la source). */
data class LocalDisplayPrefs(
    val selected: Set<String>,
    val includeMulti: Boolean,
    val includeUnknown: Boolean,
    val disabled: Map<String, Set<String>>,
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
    )

    /** Empreinte stable de l'état (sans l'horodatage) : évite de republier ce qu'on vient d'appliquer ou d'envoyer. */
    fun fingerprint(p: CloudPrefs): String {
        val langs = p.langs?.sorted()?.joinToString(",") ?: "*"
        val dis = KINDS.values.joinToString("|") { k -> k + ":" + (p.disabled[k] ?: emptyList()).sorted().joinToString(",") }
        return "$langs#$dis".hashCode().toString(16) + ":" + (langs.length + dis.length)
    }

    fun tooLarge(p: CloudPrefs): Boolean = p.disabled.values.any { it.size > MAX_IDS }

    fun toJson(p: CloudPrefs): String = JSONObject().apply {
        put("langs", p.langs?.let { JSONArray(it) } ?: JSONObject.NULL)
        put("disabled", JSONObject().apply { KINDS.values.forEach { k -> put(k, JSONArray(p.disabled[k] ?: emptyList<String>())) } })
        put("updatedAt", p.updatedAt)
    }.toString()

    fun parse(o: JSONObject?): CloudPrefs? {
        if (o == null) return null
        val updatedAt = o.optLong("updatedAt")
        if (updatedAt <= 0) return null
        val langs = if (o.isNull("langs")) null else o.optJSONArray("langs")?.let { a -> (0 until a.length()).map { a.optString(it) } }
        val d = o.optJSONObject("disabled")
        val disabled = KINDS.values.associateWith { k -> d?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() }
        return CloudPrefs(langs, disabled, updatedAt)
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

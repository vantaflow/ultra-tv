package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryEntity

/** Comment télécharger une partie du catalogue selon les catégories actives. */
enum class SyncStrategy {
    /** Tout est actif : une seule requête en flux. */
    GLOBAL,
    /** Peu de catégories actives et le serveur sait filtrer : une requête par catégorie active (parallélisme borné). */
    PER_CATEGORY,
    /** Majorité active, ou serveur sans filtre : une requête globale en flux ; les éléments des catégories désactivées ne sont pas stockés. */
    GLOBAL_FILTERED,
}

object CatalogPlan {
    /** Au-dessous de cette part de catégories actives, le téléchargement catégorie par catégorie gagne. */
    const val PER_CATEGORY_MAX_SHARE = 0.4

    fun strategy(total: Int, enabled: Int, filterSupport: Int): SyncStrategy = when {
        total == 0 || enabled >= total -> SyncStrategy.GLOBAL
        filterSupport == 0 -> SyncStrategy.GLOBAL_FILTERED
        enabled.toDouble() / total < PER_CATEGORY_MAX_SHARE -> SyncStrategy.PER_CATEGORY
        else -> SyncStrategy.GLOBAL_FILTERED
    }

    /** Requêtes simultanées autorisées selon le niveau de l'appareil (2 à 4). */
    fun parallelism(tier: com.ultratv.tv.nativeapp.adaptive.Tier) = when (tier) {
        com.ultratv.tv.nativeapp.adaptive.Tier.LOW -> 2
        com.ultratv.tv.nativeapp.adaptive.Tier.MID -> 3
        com.ultratv.tv.nativeapp.adaptive.Tier.HIGH -> 4
    }

    /**
     * Fusionne les catégories reçues du serveur avec l'état local : l'activation, l'ordre et le verrouillage
     * de l'utilisateur sont conservés ; une catégorie NOUVELLE est active, sauf si sa langue détectée est exclue.
     */
    fun merge(
        fetched: List<CategoryEntity>,
        existing: List<CategoryEntity>,
        selectedLanguages: Set<String>,
        includeMulti: Boolean = true,
        includeUnknown: Boolean = true,
    ): List<CategoryEntity> {
        val old = existing.associateBy { it.remoteId }
        return fetched.map { c ->
            val prev = old[c.remoteId]
            if (prev != null) c.copy(enabled = prev.enabled, position = prev.position, locked = prev.locked || c.locked)
            else c.copy(enabled = LanguageFilter.allows(c.lang, selectedLanguages, includeMulti, includeUnknown))
        }
    }
}

/** Règle de visibilité par langue : en cas de doute on AFFICHE. */
object LanguageFilter {
    fun allows(lang: String, selected: Set<String>, includeMulti: Boolean = true, includeUnknown: Boolean = true): Boolean = when {
        selected.isEmpty() -> true                                   // aucune sélection = toutes les langues
        lang == LanguageDetector.MULTI -> includeMulti               // multilingue : visible dès qu'une langue est choisie
        lang == LanguageDetector.UNDETERMINED -> includeUnknown
        else -> lang in selected
    }
}

/**
 * Langues choisies par l'utilisateur (Réglages › Langues) : [selected] vide = toutes. Les langues MASQUÉES ne sont pas
 * chargées du tout : leurs catégories sont désactivées et leurs titres ne sont NI écrits en base, NI indexés (FTS),
 * NI rattachés au guide (une chaîne non chargée n'a pas de programmes). Les titres sans langue détectée restent chargés
 * tant que « Sans langue détectée » est actif.
 */
data class LangSelection(val selected: Set<String> = emptySet(), val includeMulti: Boolean = true, val includeUnknown: Boolean = true) {
    val all: Boolean get() = selected.isEmpty()

    fun allows(lang: String): Boolean = LanguageFilter.allows(lang, selected, includeMulti, includeUnknown)

    /**
     * Un titre est-il chargé ? [categoryLang] = langue de sa catégorie (null = sans catégorie). Une catégorie ACTIVE dont
     * la langue est masquée (réactivée à la main dans l'écran Catégories) est chargée en entier : choix explicite.
     */
    fun keepsItem(itemLang: String, categoryLang: String?): Boolean =
        all || (categoryLang != null && !allows(categoryLang)) || allows(itemLang)

    companion object {
        fun of(csv: String, includeMulti: Boolean, includeUnknown: Boolean) =
            LangSelection(csv.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(), includeMulti, includeUnknown)

        private fun candidates(a: LangSelection, b: LangSelection) = a.selected + b.selected + LanguageDetector.MULTI + LanguageDetector.UNDETERMINED

        /** Des contenus jusque-là masqués deviennent visibles : il faut les (re)télécharger. */
        fun reveals(old: LangSelection, new: LangSelection): Boolean =
            if (old.all) false else new.all || candidates(old, new).any { !old.allows(it) && new.allows(it) }

        /** Des contenus jusque-là visibles deviennent masqués : il faut les purger. */
        fun hides(old: LangSelection, new: LangSelection): Boolean =
            if (new.all) false else old.all || candidates(old, new).any { old.allows(it) && !new.allows(it) }
    }
}

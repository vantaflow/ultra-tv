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
     * de l'utilisateur sont conservés ; une catégorie NOUVELLE est active, sauf si sa langue détectée est exclue
     * ou si [maskNew] (voir [mergeDetailed]).
     */
    fun merge(
        fetched: List<CategoryEntity>,
        existing: List<CategoryEntity>,
        selectedLanguages: Set<String>,
        includeMulti: Boolean = true,
        includeUnknown: Boolean = true,
        maskNew: Boolean = false,
    ): List<CategoryEntity> = mergeDetailed(fetched, existing, selectedLanguages, includeMulti, includeUnknown, maskNew).categories

    /** Résultat de [mergeDetailed] : catégories à écrire et identifiants des NOUVELLES catégories masquées d'office. */
    data class Merged(val categories: List<CategoryEntity>, val newMasked: List<String>)

    /**
     * Comme [merge], plus :
     * - identifiant changé mais nom identique (espaces de bord ignorés) à une catégorie existante : même catégorie, son
     *   état est repris (activation, verrou ; l'ordre aussi si l'ancien identifiant a disparu de la liste) ;
     * - [maskNew] (synchros suivantes d'une source, voir [shouldMaskNew]) : une catégorie inconnue est créée DÉSACTIVÉE
     *   (masquée pour tous les profils, rien n'est téléchargé) et listée dans [Merged.newMasked]. Une catégorie que la
     *   langue exclut reste gérée par le filtre des langues (non listée).
     */
    fun mergeDetailed(
        fetched: List<CategoryEntity>,
        existing: List<CategoryEntity>,
        selectedLanguages: Set<String>,
        includeMulti: Boolean = true,
        includeUnknown: Boolean = true,
        maskNew: Boolean = false,
    ): Merged {
        val old = existing.associateBy { it.remoteId }
        val fetchedIds = fetched.mapTo(HashSet()) { it.remoteId }
        val byName = existing.groupBy { it.name.trim() }
        val masked = ArrayList<String>()
        val out = fetched.map { c ->
            val prev = old[c.remoteId]
            if (prev != null) return@map c.copy(enabled = prev.enabled, position = prev.position, locked = prev.locked || c.locked)
            val sameName = byName[c.name.trim()].orEmpty()
            val renamed = sameName.firstOrNull { it.remoteId !in fetchedIds } ?: sameName.firstOrNull()
            if (renamed != null) {
                return@map c.copy(enabled = renamed.enabled, locked = renamed.locked || c.locked, position = if (renamed.remoteId !in fetchedIds) renamed.position else c.position)
            }
            val langOk = LanguageFilter.allows(c.lang, selectedLanguages, includeMulti, includeUnknown)
            if (maskNew && langOk) { masked += c.remoteId; c.copy(enabled = false) } else c.copy(enabled = langOk)
        }
        return Merged(out, masked)
    }

    /**
     * Nouvelles catégories masquées d'office ? Jamais à la PREMIÈRE synchro d'une source (aucune catégorie connue pour ce
     * type, « Mes chaînes » ajoutées à la main mises à part), ni pour les bibliothèques des serveurs multimédias et des
     * NAS (choisies par l'utilisateur à l'ajout). Xtream, M3U et Stalker sont concernés.
     */
    fun shouldMaskNew(providerKind: String, existing: List<CategoryEntity>): Boolean =
        providerKind !in LIBRARY_KINDS && existing.any { it.remoteId != ManualChannels.CUSTOM_CAT }

    private val LIBRARY_KINDS = setOf("JELLYFIN", "EMBY", "PLEX", "NAS")
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
 * Langues choisies par l'utilisateur (Réglages › Langues) : [selected] vide = toutes. Le filtre est par CATÉGORIE : une
 * langue MASQUÉE désactive ses catégories, dont les titres ne sont NI écrits en base, NI indexés (FTS), NI rattachés au
 * guide. Une catégorie active est chargée en entier, quelle que soit la langue détectée sur chaque titre.
 */
data class LangSelection(val selected: Set<String> = emptySet(), val includeMulti: Boolean = true, val includeUnknown: Boolean = true) {
    val all: Boolean get() = selected.isEmpty()

    fun allows(lang: String): Boolean = LanguageFilter.allows(lang, selected, includeMulti, includeUnknown)

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

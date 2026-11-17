package com.ultratv.tv.nativeapp.data.profile

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Règles pures des sources visibles par profil (testées à part). Les sources sont désignées par leur nom (stable entre appareils). */
object ProfileSourcesRules {
    private fun key(name: String) = name.trim().lowercase()

    fun encode(names: List<String>): String = names.joinToString("\n")

    /** Liste enregistrée → noms ; null (= toutes les sources) si vide. */
    fun decode(raw: String?): List<String>? = raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }

    /**
     * Sources visibles : toutes si le profil n'a pas fait de choix, ou si aucun nom ne correspond à une source de cet
     * appareil (choix venu d'un autre appareil : jamais de catalogue vide à cause d'un nom inconnu).
     */
    fun visible(providers: List<ProviderEntity>, names: List<String>?): List<ProviderEntity> {
        val srcNames = sourceNames(names)
        if (srcNames.isNullOrEmpty()) return providers
        val wanted = srcNames.map(::key).toSet()
        return providers.filter { key(it.name) in wanted }.ifEmpty { providers }
    }

    // ── Bibliothèques (sections) d'un serveur Plex / Jellyfin / Emby masquées pour un profil ──
    // Stockées dans la MÊME liste que les sources (donc synchronisées avec les appareils par le mécanisme existant des profils,
    // sans nouveau champ côté site) : une entrée « !serveur/bibliothèque ». Une ancienne version les voit comme des noms de
    // source inconnus et les ignore. Plafond du site : 12 entrées de 40 caractères au plus pour toute la liste.
    private const val HIDE = "!"
    private const val ENTRY_MAX = 40

    private fun libEntry(server: String, library: String) = (HIDE + server.trim() + "/" + library.trim()).take(ENTRY_MAX)

    /** Noms de sources de la liste (sans les entrées de bibliothèques masquées) ; null si aucun (= toutes). */
    fun sourceNames(names: List<String>?): List<String>? = names?.filter { !it.startsWith(HIDE) }?.takeIf { it.isNotEmpty() }

    /** Bibliothèque visible pour le profil ? */
    fun libraryVisible(names: List<String>?, server: String, library: String): Boolean {
        val e = libEntry(server, library).lowercase()
        return names.orEmpty().none { it.startsWith(HIDE) && it.trim().lowercase() == e }
    }

    /** Masque ou réaffiche une bibliothèque ; les sources choisies sont conservées. Null = plus aucun choix. */
    fun toggleLibrary(names: List<String>?, server: String, library: String): List<String>? {
        val e = libEntry(server, library)
        val cur = names.orEmpty()
        val next = if (libraryVisible(names, server, library)) cur + e else cur.filter { it.trim().lowercase() != e.lowercase() }
        return next.takeIf { it.isNotEmpty() }
    }

    /** Remplace la partie « sources » de la liste en gardant les bibliothèques masquées. */
    fun withSources(names: List<String>?, sources: List<String>?): List<String>? =
        ((sources ?: emptyList()) + names.orEmpty().filter { it.startsWith(HIDE) }).takeIf { it.isNotEmpty() }

    /** Identifiant de la source à rendre active : null si la source active est déjà visible (ou s'il n'y en a aucune). */
    fun activeToSet(providers: List<ProviderEntity>, names: List<String>?): Long? {
        val vis = visible(providers, names)
        if (vis.isEmpty()) return null
        val active = providers.firstOrNull { it.active }
        return if (active != null && vis.any { it.id == active.id }) null else vis.first().id
    }

    /** Nouveau choix après avoir coché ou décoché une source ; tout coché ou plus rien de coché = « toutes » (null). */
    fun toggle(allNames: List<String>, current: List<String>?, name: String): List<String>? {
        val base = (sourceNames(current) ?: allNames).toMutableList()
        val i = base.indexOfFirst { key(it) == key(name) }
        if (i >= 0) base.removeAt(i) else base.add(name)
        val sources = if (base.isEmpty() || base.map(::key).toSet() == allNames.map(::key).toSet()) null else base
        return withSources(current, sources)
    }
}

/** Pastille courte d'une source (mode « Toutes les sources ») : son nom, tronqué à 8 caractères. */
object SourceBadge {
    fun of(name: String): String {
        val n = name.trim().ifEmpty { "?" }
        return if (n.length <= 8) n else n.take(7).trimEnd() + "\u2026"
    }
}

/**
 * Applique le choix de sources du profil courant : au changement de profil (ou de choix), la source active devient
 * la première source visible si l'active actuelle ne l'est pas. Le catalogue affiché suit donc le profil.
 */
@Singleton
class ProfileSources @Inject constructor(
    private val profiles: ProfileRepository,
    private val providers: ProviderRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            // Seuls le profil et son choix déclenchent : une source ajoutée plus tard n'est pas rattachée de force.
            profiles.currentSources.combine(profiles.currentId) { names, id -> id to names }.distinctUntilChanged().collect { (_, names) ->
                runCatching {
                    val all = providers.observeProviders().first()
                    ProfileSourcesRules.activeToSet(all, names)?.let { providers.setDefault(it) }
                }
            }
        }
    }

    /** Un choix manuel de source par défaut doit rester visible : on l'ajoute au choix du profil courant s'il en a un. */
    suspend fun ensureVisible(providerId: Long) {
        val names = profiles.sourcesOf(profiles.currentIdNow) ?: return
        if (ProfileSourcesRules.sourceNames(names) == null) return
        val all = providers.observeProviders().first()
        val p = all.firstOrNull { it.id == providerId } ?: return
        if (ProfileSourcesRules.visible(all, names).any { it.id == providerId }) return
        profiles.setSourcesFor(profiles.currentIdNow, names + p.name)
    }
}

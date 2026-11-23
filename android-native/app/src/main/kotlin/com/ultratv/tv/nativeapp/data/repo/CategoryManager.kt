package com.ultratv.tv.nativeapp.data.repo

import androidx.room.withTransaction
import com.ultratv.tv.nativeapp.data.db.CategoryCount
import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.db.observeCategoryCounts
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import javax.inject.Inject
import javax.inject.Singleton

/** Ligne de l'écran « Gérer les catégories ». */
data class CategoryRow(val remoteId: String, val label: String, val badge: String?, val count: Int?, val enabled: Boolean, val lang: String, val locked: Boolean, val fav: Boolean = false)

/**
 * Activation / désactivation / ordre des catégories. UN SEUL interrupteur : désactivée = ni téléchargée, ni mise à jour,
 * ni affichée ; ses éléments sont purgés tout de suite. La réactiver déclenche une synchro ciblée sur cette seule catégorie.
 */
@Singleton
@OptIn(kotlinx.coroutines.FlowPreview::class)
class CategoryManager @Inject constructor(
    private val db: UltraDb,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val sync: SyncCoordinator,
    private val langDisabled: com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore,
    private val hidden: com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
    private val hiding: ProfileChannelHiding,
    private val switcher: ProfileSwitcher,
) {
    fun observe(pid: Long, kind: String, query: String): Flow<List<CategoryRow>> {
        val counts: Flow<List<CategoryCount>> = when (kind) { "LIVE" -> channelDao.observeCategoryCounts(pid); "MOVIE" -> movieDao.observeCategoryCounts(pid); else -> seriesDao.observeCategoryCounts(pid) }
        // Pendant une synchro la table des chaînes change sans arrêt : compteurs recalculés au plus une fois par seconde
        // (le premier tout de suite), sinon l'écran se fige sur une box modeste.
        val throttled = counts.atMostEvery(1_000)
        return combine(categoryDao.observeOrdered(pid, kind), throttled) { cats, cnt -> rows(cats, cnt, query) }.conflate()
    }

    /** Total de catégories d'un type : un COUNT SQL (la liste complète des catégories n'est plus chargée pour en prendre la taille). */
    fun observeTotals(pid: Long, kind: String): Flow<Int> = categoryDao.observeCount(pid, kind)

    /** Verrouille (code parental exigé) ou déverrouille une catégorie entière. */
    suspend fun setLocked(pid: Long, kind: String, remoteId: String, locked: Boolean) = categoryDao.setLockedRemote(pid, kind, remoteId, locked)

    suspend fun setEnabled(pid: Long, kind: String, ids: List<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        // Décision manuelle : la catégorie ne dépend plus de la langue (réactiver une langue n'y touchera pas).
        langDisabled.remove(pid, kind, ids)
        db.withTransaction {
            ids.chunked(500).forEach { chunk ->
                categoryDao.setEnabled(pid, kind, chunk, enabled)
                if (!enabled) when (kind) { "LIVE" -> channelDao.deleteForCategories(pid, chunk); "MOVIE" -> movieDao.deleteForCategories(pid, chunk); else -> seriesDao.deleteForCategories(pid, chunk) }
            }
        }
        if (enabled) sync.requestCategories(pid, kind, ids)
    }

    /**
     * Enregistre l'ordre (1..n) tel qu'affiché, pour le profil COURANT (chaque profil a le sien : table
     * `profile_category_order`) ; `category.position` en est la projection. Direct : le rang précalculé des chaînes suit
     * (voir [LiveOrder]).
     */
    suspend fun saveOrder(pid: Long, kind: String, orderedIds: List<String>) = db.withTransaction {
        switcher.saveOrderFor(profiles.currentIdNow, pid, kind, orderedIds)
        orderedIds.forEachIndexed { i, id -> categoryDao.setPosition(pid, kind, id, i + 1) }
        if (kind == LiveOrder.KIND) LiveOrder.apply(channelDao, pid, LiveOrder.ranks(categoryDao.forProviderKind(pid, kind)))
    }

    /**
     * Enregistre un nouvel ordre pour une PARTIE des catégories (liste filtrée, cochées d'abord) : ces catégories
     * reprennent les places qu'elles occupaient dans l'ordre complet, les autres ne bougent pas. Avant, enregistrer depuis
     * une liste filtrée numérotait 1..n les seules catégories visibles et mélangeait tout le reste.
     */
    suspend fun saveOrderOf(pid: Long, kind: String, visibleOrder: List<String>) {
        val full = categoryDao.orderedIds(pid, kind)
        saveOrder(pid, kind, mergeOrder(full, visibleOrder))
    }

    /** Ordre alphabétique (nom affiché), pour tout le type. */
    suspend fun sortAlphabetically(pid: Long, kind: String) {
        saveOrder(pid, kind, alphabeticalIds(categoryDao.forProviderKind(pid, kind)))
    }

    /**
     * Retour à l'ordre du fournisseur (ordre d'arrivée des catégories) après un tri A → Z : avant, le tri écrasait
     * l'ordre et rien ne permettait de le retrouver. Enregistré comme un ordre ordinaire (profil + partage).
     */
    suspend fun restoreProviderOrder(pid: Long, kind: String) {
        saveOrder(pid, kind, providerIds(categoryDao.forProviderKind(pid, kind)))
    }

    /** Vrai tant que les catégories sont rangées de A à Z : le bouton propose alors l'ordre du fournisseur. */
    fun observeAlphabetical(pid: Long, kind: String): Flow<Boolean> =
        categoryDao.observeOrdered(pid, kind).map { isAlphabetical(it) }.distinctUntilChanged()

    /** Ordre complet actuel (identifiants du fournisseur). */
    suspend fun orderedIds(pid: Long, kind: String): List<String> = categoryDao.orderedIds(pid, kind)

    // ── Chaînes masquées (PAR PROFIL, voir [ProfileChannelHiding]) ───────────────────────────────

    /** Chaînes masquées par le profil COURANT (écran Catégories). */
    fun observeHidden(pid: Long): Flow<List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel>> =
        profiles.currentId.flatMapLatest { hiding.observe(it, pid) }

    /** Chaînes masquées par TOUS les profils (supprimées de la base) : champ historique `hidden` du cloud. */
    fun hiddenOf(pid: Long) = hidden.list(pid)

    /** Chaînes masquées par le seul profil [profileId] et pas par tous : champ `pHiddenCh` du cloud. */
    suspend fun hiddenOnlyBy(profileId: Long, pid: Long): List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel> {
        val all = hidden.ids(pid)
        return hiding.listOf(profileId, pid).filter { it.id !in all }
    }

    /** Chaînes d'une catégorie : présentes en base + masquées par le profil courant (pour pouvoir les réafficher). */
    suspend fun channelsOf(pid: Long, cat: String): List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel> {
        val mine = hiding.listOf(profiles.currentIdNow, pid)
        val mineIds = mine.mapTo(HashSet()) { it.id }
        return channelDao.listInCategory(pid, cat).filter { it.remoteId !in mineIds }.map { com.ultratv.tv.nativeapp.data.prefs.HiddenChannel(it.remoteId, it.categoryId, it.name) } +
            mine.filter { it.cat == cat }
    }

    /** Masque des chaînes pour le profil COURANT : les autres profils les voient toujours (supprimées seulement si tous les masquent). */
    suspend fun hideChannels(pid: Long, items: List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel>) =
        hiding.hide(profiles.currentIdNow, pid, items, profiles.currentIdNow)

    /** Réaffiche des chaînes pour le profil courant (leurs catégories sont rechargées si plus aucun profil ne les masquait… ou les masquait tous). */
    suspend fun showChannels(pid: Long, ids: Collection<String>) = hiding.show(profiles.currentIdNow, pid, ids, profiles.currentIdNow)

    /** Réglages reçus du cloud : chaînes masquées de [profileId] (remplacement complet). */
    suspend fun replaceHiddenFor(profileId: Long, pid: Long, items: List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel>) =
        hiding.replaceFor(profileId, pid, items, profiles.currentIdNow)

    /** Tout ce que masque le profil [profileId] (réglages cloud). */
    suspend fun hiddenOfProfile(profileId: Long, pid: Long) = hiding.listOf(profileId, pid)

    /** À appeler après une série de [replaceHiddenFor] : supprime les chaînes devenues unanimes, recharge les autres. */
    suspend fun reconcileHidden(pid: Long) = hiding.reconcile(pid)

    companion object {
        /** Fusion d'un ordre partiel dans l'ordre complet : les places des catégories visibles leur sont réattribuées. Pur. */
        fun mergeOrder(full: List<String>, visibleOrder: List<String>): List<String> {
            val vis = visibleOrder.toSet()
            val queue = ArrayDeque(visibleOrder.filter { it in full })
            val out = full.map { id -> if (id in vis) queue.removeFirstOrNull() ?: id else id }.toMutableList()
            // Catégories visibles inconnues de l'ordre complet (cas limite) : à la fin.
            out += visibleOrder.filter { it !in full }
            return out.distinct()
        }

        /** Construit les lignes : filtre (nom, badge, langue), badge pays/qualité extrait par le parseur. Pur et testable. */
        fun rows(cats: List<CategoryEntity>, counts: List<CategoryCount>, query: String): List<CategoryRow> {
            val byId = counts.associate { it.categoryId to it.n }
            val q = query.trim().lowercase()
            return cats.map { c ->
                val parsed = ChannelNameParser.parseCategory(c.name)
                val badge = parsed.badge ?: when (parsed.quality) { ChannelNameParser.Q_4K -> "4K"; ChannelNameParser.Q_FHD -> "FHD"; else -> c.lang.uppercase().takeIf { it.isNotEmpty() && it != LanguageDetector.MULTI } }
                CategoryRow(c.remoteId, com.ultratv.tv.nativeapp.ui.common.prettyCategoryName(c.name), badge, if (c.enabled) byId[c.remoteId] ?: 0 else null, c.enabled, c.lang, c.locked)
            }.filter { r -> q.isEmpty() || matches(r.label, q) || r.badge?.lowercase() == q || r.lang == q }
                // (Tri « actives d'abord » appliqué par l'écran, FIGÉ : voir [pinEnabledFirst].)
        }

        /** Ordre « actives d'abord » (ordre réel conservé dans chaque groupe) : identifiants à figer. */
        fun pinEnabledFirst(list: List<CategoryRow>): List<String> = list.sortedByDescending { it.enabled }.map { it.remoteId }

        /**
         * Applique un ordre figé [pin] : activer / désactiver une catégorie ne la déplace plus (la sélection reste où elle
         * est). Inconnues du pin (nouvelles) : à la fin, dans leur ordre.
         */
        fun applyPin(list: List<CategoryRow>, pin: List<String>): List<CategoryRow> {
            // Placement direct (sans tri ni objet par comparaison) : un tri avec rang « boxé » a déclenché un manque
            // de mémoire sur une TV à 128 Mo de tas. Rang = position dans le pin, sinon pin.size + position réelle.
            val idx = HashMap<String, Int>(pin.size * 2)
            pin.forEachIndexed { i, k -> idx.putIfAbsent(k, i) }
            val slots = arrayOfNulls<CategoryRow>(pin.size + list.size)
            var overflow: MutableList<CategoryRow>? = null
            list.forEachIndexed { i, r ->
                val rank = idx[r.remoteId] ?: (pin.size + i)
                if (slots[rank] == null) slots[rank] = r else (overflow ?: ArrayList<CategoryRow>().also { overflow = it }).add(r)
            }
            val out = ArrayList<CategoryRow>(list.size)
            for (r in slots) if (r != null) out.add(r)
            overflow?.let { out.addAll(it) } // identifiant en double dans la liste : gardé, à la fin
            return out
        }

        /** Filtre court (« FR », « AR », « 4K ») = mot entier : « FR » ne doit pas ramener « AFRICA ». Sinon, sous-chaîne. */
        internal fun matches(label: String, q: String): Boolean {
            val l = label.lowercase()
            if (q.length > 3 || q.any { it.isWhitespace() }) return l.contains(q)
            return l.split(Regex("[^\\p{L}\\p{N}+]+")).any { it == q }
        }
    }
}

/** Identifiants rangés de A à Z (nom affiché, sans tenir compte de la casse). */
internal fun alphabeticalIds(cats: List<CategoryEntity>): List<String> =
    cats.sortedBy { com.ultratv.tv.nativeapp.ui.common.prettyCategoryName(it.name).lowercase() }.map { it.remoteId }

/** Identifiants dans l'ordre du fournisseur (ordre d'arrivée = id). */
internal fun providerIds(cats: List<CategoryEntity>): List<String> = cats.sortedBy { it.id }.map { it.remoteId }

/** [ordered] (ordre affiché) est-il l'ordre A → Z ? Faux s'il y a moins de deux catégories (rien à basculer). */
internal fun isAlphabetical(ordered: List<CategoryEntity>): Boolean =
    ordered.size > 1 && ordered.map { it.remoteId } == alphabeticalIds(ordered)

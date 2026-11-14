package com.ultratv.tv.nativeapp.data.repo

import androidx.room.withTransaction
import com.ultratv.tv.nativeapp.data.db.CategoryCount
import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import javax.inject.Inject
import javax.inject.Singleton

/** Ligne de l'écran « Gérer les catégories ». */
data class CategoryRow(val remoteId: String, val label: String, val badge: String?, val count: Int?, val enabled: Boolean, val lang: String, val locked: Boolean)

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
) {
    fun observe(pid: Long, kind: String, query: String): Flow<List<CategoryRow>> {
        val counts: Flow<List<CategoryCount>> = when (kind) { "LIVE" -> channelDao.observeCategoryCounts(pid); "MOVIE" -> movieDao.observeCategoryCounts(pid); else -> seriesDao.observeCategoryCounts(pid) }
        // Pendant une synchro la table des chaînes change sans arrêt : compteurs recalculés au plus une fois par seconde
        // (le premier tout de suite), sinon l'écran se fige sur une box modeste.
        val throttled = counts.atMostEvery(1_000)
        return combine(categoryDao.observeOrdered(pid, kind), throttled) { cats, cnt -> rows(cats, cnt, query) }.conflate()
    }

    fun observeTotals(pid: Long, kind: String): Flow<Int> = categoryDao.observeOrdered(pid, kind).let { f -> kotlinx.coroutines.flow.flow { f.collect { emit(it.size) } } }

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

    /** Enregistre l'ordre (1..n) tel qu'affiché. Direct : le rang précalculé des chaînes suit (voir [LiveOrder]). */
    suspend fun saveOrder(pid: Long, kind: String, orderedIds: List<String>) = db.withTransaction {
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
        val cats = categoryDao.forProviderKind(pid, kind)
        saveOrder(pid, kind, cats.sortedBy { com.ultratv.tv.nativeapp.ui.common.prettyCategoryName(it.name).lowercase() }.map { it.remoteId })
    }

    /** Ordre complet actuel (identifiants du fournisseur). */
    suspend fun orderedIds(pid: Long, kind: String): List<String> = categoryDao.orderedIds(pid, kind)

    // ── Chaînes masquées ────────────────────────────────────────────────────────────────────────

    fun observeHidden(pid: Long) = hidden.observe(pid)
    fun hiddenOf(pid: Long) = hidden.list(pid)

    /** Chaînes d'une catégorie : présentes en base + masquées de cette catégorie (pour pouvoir les réafficher). */
    suspend fun channelsOf(pid: Long, cat: String): List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel> =
        channelDao.listInCategory(pid, cat).map { com.ultratv.tv.nativeapp.data.prefs.HiddenChannel(it.remoteId, it.categoryId, it.name) } +
            hidden.list(pid).filter { it.cat == cat }

    /** Masque des chaînes : retirées tout de suite de la base, ignorées par les prochaines synchros. */
    suspend fun hideChannels(pid: Long, items: List<com.ultratv.tv.nativeapp.data.prefs.HiddenChannel>) {
        if (items.isEmpty()) return
        hidden.add(pid, items)
        items.map { it.id }.chunked(500).forEach { channelDao.deleteByRemoteIds(pid, it) }
    }

    /** Réaffiche des chaînes : retirées de la liste, leurs catégories rechargées. */
    suspend fun showChannels(pid: Long, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val cats = hidden.list(pid).filter { it.id in ids }.mapNotNull { it.cat }.distinct()
        hidden.remove(pid, ids)
        if (cats.isNotEmpty()) sync.requestCategories(pid, "LIVE", cats)
    }

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
            val idx = pin.withIndex().associate { (i, k) -> k to i }
            return list.withIndex().sortedBy { (i, r) -> idx[r.remoteId] ?: (pin.size + i) }.map { it.value }
        }

        /** Filtre court (« FR », « AR », « 4K ») = mot entier : « FR » ne doit pas ramener « AFRICA ». Sinon, sous-chaîne. */
        internal fun matches(label: String, q: String): Boolean {
            val l = label.lowercase()
            if (q.length > 3 || q.any { it.isWhitespace() }) return l.contains(q)
            return l.split(Regex("[^\\p{L}\\p{N}+]+")).any { it == q }
        }
    }
}

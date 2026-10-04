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
) {
    fun observe(pid: Long, kind: String, query: String): Flow<List<CategoryRow>> {
        val counts: Flow<List<CategoryCount>> = when (kind) { "LIVE" -> channelDao.observeCategoryCounts(pid); "MOVIE" -> movieDao.observeCategoryCounts(pid); else -> seriesDao.observeCategoryCounts(pid) }
        // Pendant une synchro la table des chaînes change sans arrêt : compteurs recalculés au plus une fois par seconde
        // (le premier tout de suite), sinon l'écran se fige sur une box modeste.
        val throttled = kotlinx.coroutines.flow.merge(counts.take(1), counts.drop(1).sample(1_000))
        return combine(categoryDao.observeOrdered(pid, kind), throttled) { cats, cnt -> rows(cats, cnt, query) }.conflate()
    }

    fun observeTotals(pid: Long, kind: String): Flow<Int> = categoryDao.observeOrdered(pid, kind).let { f -> kotlinx.coroutines.flow.flow { f.collect { emit(it.size) } } }

    suspend fun setEnabled(pid: Long, kind: String, ids: List<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        db.withTransaction {
            ids.chunked(500).forEach { chunk ->
                categoryDao.setEnabled(pid, kind, chunk, enabled)
                if (!enabled) when (kind) { "LIVE" -> channelDao.deleteForCategories(pid, chunk); "MOVIE" -> movieDao.deleteForCategories(pid, chunk); else -> seriesDao.deleteForCategories(pid, chunk) }
            }
        }
        if (enabled) sync.requestCategories(pid, kind, ids)
    }

    /** Enregistre l'ordre (1..n) tel qu'affiché. */
    suspend fun saveOrder(pid: Long, kind: String, orderedIds: List<String>) = db.withTransaction {
        orderedIds.forEachIndexed { i, id -> categoryDao.setPosition(pid, kind, id, i + 1) }
    }

    companion object {
        /** Construit les lignes : filtre (nom, badge, langue), badge pays/qualité extrait par le parseur. Pur et testable. */
        fun rows(cats: List<CategoryEntity>, counts: List<CategoryCount>, query: String): List<CategoryRow> {
            val byId = counts.associate { it.categoryId to it.n }
            val q = query.trim().lowercase()
            return cats.map { c ->
                val parsed = ChannelNameParser.parseCategory(c.name)
                val badge = parsed.badge ?: when (parsed.quality) { ChannelNameParser.Q_4K -> "4K"; ChannelNameParser.Q_FHD -> "FHD"; else -> c.lang.uppercase().takeIf { it.isNotEmpty() && it != LanguageDetector.MULTI } }
                CategoryRow(c.remoteId, parsed.label, badge, if (c.enabled) byId[c.remoteId] ?: 0 else null, c.enabled, c.lang, c.locked)
            }.filter { r -> q.isEmpty() || r.label.lowercase().contains(q) || r.badge?.lowercase() == q || r.lang == q }
        }
    }
}

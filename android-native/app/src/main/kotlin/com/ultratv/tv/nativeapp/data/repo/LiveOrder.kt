package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity

/**
 * Ordre de la liste « Tout » du Direct et du zapping Haut/Bas : les catégories dans l'ordre choisi par l'utilisateur
 * (Paramètres › Catégories), puis la playlist dans chaque catégorie. Cet ordre est MATÉRIALISÉ dans `channel.catRank`
 * (rang de la catégorie de la chaîne) pour qu'un seul index serve la pagination et le zapping sans jointure ni tri.
 *
 * Même tri que [com.ultratv.tv.nativeapp.data.db.CategoryDao.observeForProviderKind] et que l'ancienne requête jointe :
 *  1. catégories placées (position ≠ 0) par position croissante puis id → rangs 1..p ;
 *  2. chaînes sans catégorie du Direct (categoryId nul ou inconnu) → rang p + 1 (l'ancien LEFT JOIN donnait NULL,
 *     classé avant la position 0 par SQLite) ;
 *  3. catégories non placées (position = 0) par id → rangs p + 2…
 *
 * Rangs toujours ≥ 1 : 0 signifie « pas encore calculé » (base migrée depuis la version 17, voir [LiveOrderBackfill]).
 *
 * Écrivains : la synchro ([Ranks.stamp] sur les lignes insérées, puis [apply] en filet de sécurité pour les lignes
 * restées en place, et relecture des rangs après chaque réécriture de la table des catégories), le réordonnancement
 * des catégories ([apply] dans CategoryManager.saveOrder) et le remplissage différé ([fill]). Activer / désactiver une
 * catégorie ne change aucun rang (désactivée = ses chaînes sont supprimées ; réactivée = rechargée avec son rang).
 */
object LiveOrder {
    const val KIND = "LIVE"

    /** Catégories du Direct dans l'ordre d'affichage (les autres types sont ignorés). */
    fun displayOrder(cats: List<CategoryEntity>): List<CategoryEntity> =
        cats.filter { it.kind == KIND }.sortedWith(compareBy({ if (it.position == 0) 1 else 0 }, { it.position }, { it.id }))

    /** Rangs calculés pour une source : par identifiant de catégorie, et celui des chaînes sans catégorie. */
    class Ranks internal constructor(val byCategory: Map<String, Int>, val orphan: Int) {
        fun of(categoryId: String?): Int = categoryId?.let { byCategory[it] } ?: orphan

        override fun equals(other: Any?) = other is Ranks && other.orphan == orphan && other.byCategory == byCategory
        override fun hashCode() = byCategory.hashCode() * 31 + orphan

        /** Pose le rang sur des lignes à insérer (copie seulement si la valeur change). */
        fun stamp(rows: List<ChannelEntity>): List<ChannelEntity> = rows.map { c ->
            val r = of(c.categoryId)
            if (c.catRank == r) c else c.copy(catRank = r)
        }
    }

    fun ranks(cats: List<CategoryEntity>): Ranks {
        val ordered = displayOrder(cats)
        val placed = ordered.count { it.position != 0 }
        val byCategory = HashMap<String, Int>(ordered.size * 2)
        ordered.forEachIndexed { i, c -> byCategory[c.remoteId] = if (i < placed) i + 1 else i + 2 }
        return Ranks(byCategory, placed + 1)
    }

    /** Aucune catégorie connue : toutes les chaînes au rang des « sans catégorie ». */
    val NONE: Ranks = ranks(emptyList())

    /**
     * Réécrit le rang des chaînes d'une source d'après [ranks] : une mise à jour par catégorie (index
     * providerId+categoryId) qui ne touche que les lignes dont le rang change — les déclencheurs FTS ne se déclenchent
     * donc que pour elles. À appeler dans une transaction.
     */
    suspend fun apply(dao: ChannelDao, pid: Long, ranks: Ranks) {
        for ((cat, r) in ranks.byCategory) dao.setCatRank(pid, cat, r)
        dao.setCatRankOrphans(pid, ranks.orphan)
    }

    /**
     * Même effet que [apply], mais par lots de [batch] lignes, chacun dans sa propre transaction (remplissage en tâche de
     * fond : jamais de longue écriture qui bloquerait les lectures ou l'eMMC). [pause] laisse respirer entre deux lots.
     * Renvoie le nombre de lignes réécrites.
     */
    suspend fun fill(dao: ChannelDao, pid: Long, ranks: Ranks, batch: Int = 2_000, pause: suspend () -> Unit = {}): Int {
        var total = 0
        for ((cat, r) in ranks.byCategory) {
            while (true) { val n = dao.setCatRankBatch(pid, cat, r, batch); total += n; if (n < batch) break; pause() }
        }
        while (true) { val n = dao.setCatRankOrphansBatch(pid, ranks.orphan, batch); total += n; if (n < batch) break; pause() }
        return total
    }
}

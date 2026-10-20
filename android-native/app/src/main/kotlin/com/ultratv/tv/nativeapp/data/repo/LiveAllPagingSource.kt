package com.ultratv.tv.nativeapp.data.repo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity

/** Position dans l'ordre de « Tout » : (rang de catégorie, numéro, id), clé unique et croissante. */
data class LiveKey(val catRank: Int, val num: Int, val id: Long) {
    companion object {
        /** Avant la toute première chaîne. */
        val START = LiveKey(Int.MIN_VALUE, Int.MIN_VALUE, Long.MIN_VALUE)
        fun of(c: ChannelEntity) = LiveKey(c.catRank, c.num, c.id)
        /** Juste avant [c] : la page suivante commence PAR elle (id entier : id > id - 1 ⇔ id ≥ id). */
        fun justBefore(c: ChannelEntity) = LiveKey(c.catRank, c.num, c.id - 1)
    }
}

/** Filtres de la liste « Tout » : catégories masquées du profil et langues de la vue. */
data class LiveAllFilter(val hidden: List<String> = emptyList(), val useLang: Int = 0, val langs: List<String> = emptyList()) {
    // Liste vide → un élément neutre : Room n'aime pas « IN () » et l'interrupteur use* coupe de toute façon le filtre.
    internal val hiddenArg get() = hidden.ifEmpty { listOf("") }
    internal val langsArg get() = langs.ifEmpty { listOf("") }
    internal val useHidden get() = if (hidden.isEmpty()) 0 else 1
}

/**
 * Liste « Tout » du Direct paginée PAR CLÉ sur l'index (providerId, junk, isSeparator, catRank, num) : chaque page est une
 * recherche d'index (aucun tri, aucun OFFSET), et une synchro qui insère pendant le défilement ne peut ni dupliquer ni
 * décaler les lignes déjà affichées.
 *
 * Contrairement aux sources Room, elle n'observe PAS la table : elle est invalidée par l'appelant (LiveViewModel) une seule
 * fois à la fin d'une synchro ou après une modification hors synchro (réordonnancement, catégorie désactivée), au lieu
 * d'être recréée — requête refaite, liste rediffée — à chaque lot de 500 chaînes inséré.
 */
class LiveAllPagingSource(
    private val dao: ChannelDao,
    private val pid: Long,
    private val filter: LiveAllFilter,
) : PagingSource<LiveKey, ChannelEntity>() {

    override val keyReuseSupported: Boolean get() = true

    override suspend fun load(params: LoadParams<LiveKey>): LoadResult<LiveKey, ChannelEntity> = try {
        val n = params.loadSize
        when (params) {
            is LoadParams.Prepend -> {
                val rows = before(params.key, n).asReversed()
                LoadResult.Page(rows, prevKey = if (rows.size < n) null else LiveKey.of(rows.first()), nextKey = null)
            }
            is LoadParams.Append -> {
                val rows = after(params.key, n)
                LoadResult.Page(rows, prevKey = null, nextKey = if (rows.size < n) null else LiveKey.of(rows.last()))
            }
            is LoadParams.Refresh -> {
                val from = params.key ?: LiveKey.START
                val rows = after(from, n)
                // Rafraîchi autour d'une chaîne (pas en tête) : la page précédente se charge à partir de la première ligne.
                val prev = if (from == LiveKey.START || rows.isEmpty()) null else LiveKey.of(rows.first())
                LoadResult.Page(rows, prevKey = prev, nextKey = if (rows.size < n) null else LiveKey.of(rows.last()))
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    /** Relance centrée sur la chaîne visible : la liste ne saute pas en tête après une synchro. */
    override fun getRefreshKey(state: PagingState<LiveKey, ChannelEntity>): LiveKey? {
        val anchor = state.anchorPosition ?: return null
        return state.closestItemToPosition(anchor)?.let { LiveKey.justBefore(it) }
    }

    private suspend fun after(k: LiveKey, n: Int) =
        dao.allAfter(pid, k.catRank, k.num, k.id, n, filter.useHidden, filter.hiddenArg, filter.useLang, filter.langsArg)

    private suspend fun before(k: LiveKey, n: Int) =
        dao.allBefore(pid, k.catRank, k.num, k.id, n, filter.useHidden, filter.hiddenArg, filter.useLang, filter.langsArg)

    companion object {
        /**
         * Fenêtre de zapping autour de [c] dans « Tout » (sans filtre, comme avant) : jusqu'à [side] chaînes avant elle,
         * elle-même, puis la suite jusqu'à [side] * 2 + 1 chaînes. Deux recherches d'index au lieu de charger les
         * 55 000 identifiants pour un indexOf.
         */
        suspend fun window(dao: ChannelDao, c: ChannelEntity, side: Int = 200): List<ChannelEntity> {
            val f = LiveAllFilter()
            val before = dao.allBefore(c.providerId, c.catRank, c.num, c.id, side, 0, f.hiddenArg, 0, f.langsArg).asReversed()
            val k = LiveKey.justBefore(c)
            val after = dao.allAfter(c.providerId, k.catRank, k.num, k.id, side * 2 + 1 - before.size, 0, f.hiddenArg, 0, f.langsArg)
            return before + after
        }
    }
}

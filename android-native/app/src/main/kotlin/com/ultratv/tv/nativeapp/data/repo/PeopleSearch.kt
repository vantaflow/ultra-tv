package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.tmdb.PersonLogic
import com.ultratv.tv.nativeapp.data.tmdb.PersonRepository
import com.ultratv.tv.nativeapp.data.tmdb.PersonSearch
import com.ultratv.tv.nativeapp.data.tmdb.PersonWorks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acteurs et réalisateurs dans la recherche. Source locale : noms cités par les distributions de la playlist (séries,
 * films, fiches déjà ouvertes), hors ligne, via un index en mémoire bâti une fois puis réutilisé quelques minutes.
 * Source TMDB (appareil appairé) : recherche de personne du Worker, gardée seulement si la personne a des titres visibles
 * dans la playlist. Catégories masquées / verrouillées et titres adultes du profil Enfants sont écartés.
 */
@Singleton
class PeopleSearch @Inject constructor(
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val catalog: CatalogRepository,
    private val persons: PersonRepository,
) {
    private class Cached(val pids: List<Long>, val at: Long, val index: PersonSearch.Index)

    @Volatile private var cached: Cached? = null
    private val lock = Mutex()
    private val verified = HashMap<String, Boolean>()

    private suspend fun index(pids: List<Long>): PersonSearch.Index {
        cached?.takeIf { it.pids == pids && System.currentTimeMillis() - it.at < TTL_MS }?.let { return it.index }
        return lock.withLock {
            cached?.takeIf { it.pids == pids && System.currentTimeMillis() - it.at < TTL_MS }?.let { return@withLock it.index }
            val rows = ArrayList<Pair<PersonSearch.Occ, String>>()
            for (pid in pids) {
                movieDao.castRows(pid).mapTo(rows) { PersonSearch.Occ(pid, 'M', it.categoryId) to it.names }
                movieDao.infoCastRows(pid).mapTo(rows) { PersonSearch.Occ(pid, 'M', it.categoryId) to it.names }
                seriesDao.castRows(pid).mapTo(rows) { PersonSearch.Occ(pid, 'S', it.categoryId) to it.names }
            }
            val built = withContext(Dispatchers.Default) { PersonSearch.build(rows) }
            cached = Cached(pids, System.currentTimeMillis(), built)
            built
        }
    }

    private suspend fun excluded(pids: List<Long>): Set<Triple<Long, Char, String>> {
        val out = HashSet<Triple<Long, Char, String>>()
        for (pid in pids) {
            catalog.excludedCategoryIds(pid, "MOVIE").forEach { out += Triple(pid, 'M', it) }
            catalog.excludedCategoryIds(pid, "SERIES").forEach { out += Triple(pid, 'S', it) }
        }
        return out
    }

    /** Personnes dont le nom correspond à la saisie, citées dans la playlist (hors ligne). */
    suspend fun local(pids: List<Long>, query: String, limit: Int = LIMIT): List<String> {
        if (pids.isEmpty() || query.trim().length < PersonSearch.MIN_QUERY) return emptyList()
        val idx = index(pids)
        val ex = excluded(pids)
        return withContext(Dispatchers.Default) {
            PersonSearch.search(idx, query, limit) { o -> o.category == null || Triple(o.pid, o.kind, o.category) !in ex }.map { it.name }
        }
    }

    /**
     * Personnes de TMDB correspondant à la saisie, absentes de [already], qui ont au moins un titre visible dans la
     * playlist (vérifié sur la filmographie, au plus [CHECKED] candidats). Vide sans Worker.
     */
    suspend fun remote(pids: List<Long>, query: String, already: List<String>, limit: Int = LIMIT): List<String> {
        if (pids.isEmpty()) return emptyList()
        val have = already.map { PersonLogic.key(it) }.toSet()
        val candidates = persons.searchNames(query, 6)
            .filter { PersonSearch.matches(it, query) && PersonLogic.key(it) !in have }.take(CHECKED)
        if (candidates.isEmpty()) return emptyList()
        return coroutineScope { candidates.map { n -> async { n.takeIf { hasWorks(pids, it) } } }.awaitAll().filterNotNull().take(limit) }
    }

    private suspend fun hasWorks(pids: List<Long>, name: String): Boolean {
        val k = PersonLogic.key(name)
        synchronized(verified) { verified[k] }?.let { return it }
        var ok = false
        for (pid in pids) {
            if (visible(pid, persons.works(pid, name)).let { it.movies.isNotEmpty() || it.series.isNotEmpty() }) { ok = true; break }
        }
        synchronized(verified) { verified[k] = ok }
        return ok
    }

    /** Œuvres de la personne sans catégories masquées / verrouillées ni, en profil Enfants, titres adultes. */
    suspend fun visible(pid: Long, w: PersonWorks): PersonWorks {
        val exM = catalog.excludedCategoryIds(pid, "MOVIE").toHashSet()
        val exS = catalog.excludedCategoryIds(pid, "SERIES").toHashSet()
        val kids = catalog.kidsMode()
        return PersonWorks(
            w.movies.visibleIn(exM) { it.categoryId }.withoutAdultTitles(kids) { it.title },
            w.series.visibleIn(exS) { it.categoryId }.withoutAdultTitles(kids) { it.title },
        )
    }

    private companion object {
        const val TTL_MS = 3 * 60_000L
        const val LIMIT = 12
        const val CHECKED = 3
    }
}

package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.GenreDao
import com.ultratv.tv.nativeapp.data.db.GenreSource
import com.ultratv.tv.nativeapp.data.db.TitleGenreEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Détection des genres automatiques, EN ARRIÈRE-PLAN et PAR LOTS : lit les données déjà présentes (genre de la liste ou de la
 * fiche Xtream, genres TMDB déjà enrichis) — aucune requête réseau — et écrit la table `title_genre`. Incrémental : seuls les
 * titres sans genre, ou dont une donnée est apparue depuis le dernier calcul, sont traités ; un relancement après une synchro
 * ne coûte donc que les nouveautés. Thread unique de priorité minimale, courte pause entre deux lots : l'interface ne ralentit pas.
 */
@Singleton
class GenreDetector @Inject constructor(private val dao: GenreDao) {
    /** Avancement d'un calcul en cours : [done] titres classés sur [total]. */
    data class Progress(val done: Int, val total: Int) {
        val percent: Int get() = if (total <= 0) 100 else (done.toLong() * 100 / total).toInt().coerceIn(0, 100)
    }

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    /** Avancement par type ("MOVIE" / "SERIES") ; une clé absente = rien en cours. */
    val progress: StateFlow<Map<String, Progress>> = _progress

    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "genre-detect").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Lance (sans bloquer) le calcul d'un type pour une source ; sans effet s'il est déjà en cours. */
    fun ensure(providerId: Long, kind: String) {
        val key = "$providerId:$kind"
        if (jobs[key]?.isActive == true) return
        jobs[key] = scope.launch {
            try { runScan(providerId, kind) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            finally { _progress.value = _progress.value - kind }
        }
    }

    /** Un passage complet (testable directement). Retourne le nombre de titres classés pendant ce passage. */
    suspend fun runScan(providerId: Long, kind: String, chunk: Int = CHUNK, pauseMs: Long = PAUSE_MS): Int {
        val movies = kind == "MOVIE"
        if (movies) dao.purgeMovies(providerId) else dao.purgeSeries(providerId)
        val total = if (movies) dao.movieCount(providerId) else dao.seriesCount(providerId)
        var done = dao.classifiedCount(providerId, kind)
        var processed = 0
        var after = 0L
        _progress.value = _progress.value + (kind to Progress(done.coerceAtMost(total), total))
        while (true) {
            val rows = if (movies) dao.pendingMovies(providerId, after, chunk) else dao.pendingSeries(providerId, after, chunk)
            if (rows.isEmpty()) break
            after = rows.last().id
            val out = ArrayList<TitleGenreEntity>(rows.size * 2)
            for (r in rows) out += entitiesFor(providerId, kind, r)
            dao.replaceBatch(providerId, kind, rows.map { it.remoteId }, out)
            processed += rows.size
            // Un titre déjà classé puis recalculé ne compte qu'une fois : le compteur ne dépasse jamais le total.
            done = (done + rows.size).coerceAtMost(total)
            _progress.value = _progress.value + (kind to Progress(done, total))
            if (!currentCoroutineContextActive()) break
            if (pauseMs > 0) delay(pauseMs)
        }
        return processed
    }

    private suspend fun currentCoroutineContextActive() = kotlin.coroutines.coroutineContext[Job]?.isActive != false

    companion object {
        const val CHUNK = 400
        const val PAUSE_MS = 40L

        /** Lignes de jointure d'un titre (au moins une : « Autres » si rien n'est reconnu). */
        fun entitiesFor(providerId: Long, kind: String, r: GenreSource): List<TitleGenreEntity> =
            GenreGrouping.classify(r.tmdbGenreIds, r.origLang, r.genre, r.vodGenre).map { TitleGenreEntity(providerId, kind, r.remoteId, it.name, r.mask) }
    }
}

package com.ultratv.tv.nativeapp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic
import com.ultratv.tv.nativeapp.data.mediaserver.MediaServer
import com.ultratv.tv.nativeapp.data.nas.Nas
import com.ultratv.tv.nativeapp.data.repo.HistoryRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

/** Une section de « Mes bibliothèques » : un serveur (ou partage NAS), son état, ses compteurs et sa rangée d'affiches. */
data class LibraryRow(
    val provider: ProviderEntity,
    val status: MediaLibraryLogic.Status,
    val movies: Int,
    val series: Int,
    /** Dernière copie du catalogue (ms epoch, 0 = jamais) : affichée quand le serveur est hors ligne. */
    val lastCopyAt: Long,
    val shelves: List<Shelf>,
)

/** Type de rangée d'un serveur ; MOVIES / SERIES / LIBRARY mènent à « Tout voir ». */
enum class ShelfKind { RESUME, RECENT, MOVIES, SERIES, LIBRARY }

/** Rangée courte (au plus [MediaLibraryLogic.SHELF_MAX] affiches). [title] ne sert que pour LIBRARY (nom de la bibliothèque du serveur). */
data class Shelf(val kind: ShelfKind, val title: String, val tiles: List<MediaLibraryLogic.Tile>, val seeAllSeries: Boolean?)

/** Vue « dossiers » d'un partage NAS : dossiers de premier niveau + fichiers reconnus automatiquement. */
data class NasDetail(
    val folders: List<Pair<String, Int>>,
    /** Nom de fichier → titre reconnu (« Titre (année) »). */
    val recognised: List<Pair<String, String>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryHubViewModel @Inject constructor(
    providers: ProviderRepository,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val history: HistoryRepository,
) : ViewModel() {
    /** Joignabilité par source (sonde TCP courte) ; absente tant que la sonde n'a pas répondu. */
    private val reach = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val probed = HashSet<Long>()

    private val sources = providers.observeProviders()
        .map { list -> list.filter { MediaServer.isMediaKind(it.kind) || Nas.isNasKind(it.kind) } }

    val rows: StateFlow<List<LibraryRow>?> = sources.flatMapLatest { list ->
        list.forEach(::probeOnce)
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { rowFlow(it) }) { it.toList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun rowFlow(p: ProviderEntity) = combine(
        movieDao.observeCount(p.id), seriesDao.observeCount(p.id), history.continueWatching(p.id, 12),
        movieDao.observeLatest(p.id, 14), seriesDao.observeLatest(p.id, 14), reach,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val movies = values[0] as Int
        val series = values[1] as Int
        val progress = values[2] as List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>
        val latestM = values[3] as List<com.ultratv.tv.nativeapp.data.db.MovieEntity>
        val latestS = values[4] as List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>
        val reachable = (values[5] as Map<Long, Boolean>)[p.id]
        val last = maxOf(p.lastVodSyncAt, p.lastSeriesSyncAt)
        LibraryRow(p, MediaLibraryLogic.status(p.kind, p.baseUrl, reachable, last), movies, series, last, shelvesOf(p, progress, latestM, latestS, movies, series))
    }

    /**
     * Rangées courtes d'un serveur : Reprendre, Ajoutés récemment, Films, Séries, puis une par bibliothèque (section) du serveur,
     * 20 affiches au plus chacune. Aucune liste complète n'est lue : tout passe par des requêtes LIMIT (Room, hors thread principal).
     */
    private suspend fun shelvesOf(
        p: ProviderEntity, progress: List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>,
        latestM: List<com.ultratv.tv.nativeapp.data.db.MovieEntity>, latestS: List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>,
        movies: Int, series: Int,
    ): List<Shelf> = withContext(Dispatchers.IO) {
        val max = MediaLibraryLogic.SHELF_MAX
        fun m(l: List<com.ultratv.tv.nativeapp.data.db.MovieEntity>) = l.map { MediaLibraryLogic.Tile(false, it.id, it.title, it.poster, 0f) }
        fun s(l: List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>) = l.map { MediaLibraryLogic.Tile(true, it.id, it.title, it.poster, 0f) }
        val started = progress.mapNotNull { h ->
            val pr = MediaLibraryLogic.progress(h.positionMs, h.durationMs)
            if (h.kind == "MOVIE") movieDao.byRemoteId(p.id, h.remoteId)?.let { MediaLibraryLogic.Tile(false, it.id, it.title, it.poster, pr) }
            else h.parentRemoteId?.let { seriesDao.byRemoteId(p.id, it) }?.let { MediaLibraryLogic.Tile(true, it.id, it.title, it.poster, pr) }
        }
        val recent = MediaLibraryLogic.pickTiles(emptyList(), (m(latestM) + s(latestS)).shuffledStable(), max)
        val libs = buildList {
            for (kind in listOf("MOVIE", "SERIES")) for (c in categoryDao.forProviderKind(p.id, kind).filter { it.enabled }.take(MediaLibraryLogic.LIBRARY_SHELVES)) {
                val tiles = if (kind == "MOVIE") m(movieDao.latestInCategory(p.id, c.remoteId, max)) else s(seriesDao.latestInCategory(p.id, c.remoteId, max))
                if (tiles.isNotEmpty()) add(Shelf(ShelfKind.LIBRARY, c.name, tiles, kind == "SERIES"))
            }
        }
        MediaLibraryLogic.shelfPlan(
            started.take(max), recent,
            if (movies > 0) m(latestM).take(max) else emptyList(), if (series > 0) s(latestS).take(max) else emptyList(), libs,
        )
    }

    // Mélange films et séries sans hasard (alterne) pour que la rangée montre les deux.
    private fun List<MediaLibraryLogic.Tile>.shuffledStable(): List<MediaLibraryLogic.Tile> {
        val (s, m) = partition { it.isSeries }
        return buildList { for (i in 0 until maxOf(s.size, m.size)) { m.getOrNull(i)?.let(::add); s.getOrNull(i)?.let(::add) } }
    }

    /** Sonde TCP (1,5 s) du serveur : en arrière-plan, une seule fois par source et par ouverture de l'écran. */
    private fun probeOnce(p: ProviderEntity) {
        if (!probed.add(p.id)) return
        val target = MediaLibraryLogic.probeTarget(p.baseUrl) ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { Socket().use { it.connect(InetSocketAddress(target.first, target.second), 1_500) }; true }.getOrDefault(false)
            }
            reach.value = reach.value + (p.id to ok)
        }
    }

    /** Dossiers (catégories) d'un partage NAS avec leur nombre de titres, et fichiers reconnus (nom de fichier → titre). */
    suspend fun nasDetail(pid: Long): NasDetail = withContext(Dispatchers.IO) {
        val folders = buildList {
            for (kind in listOf("MOVIE", "SERIES")) for (c in categoryDao.forProviderKind(pid, kind)) {
                add(c.name to if (kind == "MOVIE") movieDao.countInCategory(pid, c.remoteId) else seriesDao.countInCategory(pid, c.remoteId))
            }
        }
        val recognised = movieDao.observeLatest(pid, 8).first()
            .mapNotNull { m -> MediaLibraryLogic.nasFileName(m.streamUrl)?.let { it to MediaLibraryLogic.recognisedTitle(m.title, m.year) } }
        NasDetail(folders, recognised)
    }
}

package com.ultratv.tv.nativeapp.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.net.NetErrors
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.ui.common.UiLog
import com.ultratv.tv.nativeapp.ui.common.uiAttempt
import com.ultratv.tv.nativeapp.ui.detail.DetailLoad
import com.ultratv.tv.nativeapp.ui.detail.DetailLock
import com.ultratv.tv.nativeapp.ui.detail.detailLockOf
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackContext,
    private val history: com.ultratv.tv.nativeapp.data.repo.HistoryRepository,
    private val tmdb: com.ultratv.tv.nativeapp.data.tmdb.TmdbRepository,
    private val trakt: com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository,
    private val ratingsClient: com.ultratv.tv.nativeapp.data.tmdb.RatingsClient,
    private val fanart: com.ultratv.tv.nativeapp.data.extmeta.FanartRepository,
    private val tvMaze: com.ultratv.tv.nativeapp.data.extmeta.TvMazeRepository,
    private val tmdbSeasons: com.ultratv.tv.nativeapp.data.tmdb.TmdbSeasonRepository,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
) : ViewModel() {

    /** Logo officiel (Fanart.tv, par identifiant TheTVDB) ; null tant qu'inconnu ou absent. */
    private val _logo = MutableStateFlow<String?>(null)
    val logo: StateFlow<String?> = _logo

    /** Épisode précédent / prochain (TVmaze) ; null tant qu'inconnu, hors appairage ou série inconnue. */
    private val _episodeLine = MutableStateFlow<com.ultratv.tv.nativeapp.data.extmeta.EpisodeLine?>(null)
    val episodeLine: StateFlow<com.ultratv.tv.nativeapp.data.extmeta.EpisodeLine?> = _episodeLine

    /** Notes IMDb / Rotten Tomatoes (Worker → OMDb) ; null tant qu'inconnues. */
    private val _ratings = kotlinx.coroutines.flow.MutableStateFlow<com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings?>(null)
    val ratings: StateFlow<com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings?> = _ratings

    /** Bande-annonce TMDB (URL YouTube) ; null si introuvable ou appareil non appairé. */
    private val _trailer = MutableStateFlow<String?>(null)
    val trailer: StateFlow<String?> = _trailer

    /** Épisodes de cette série vus sur Trakt (« SxE »), vide si non lié / série absente de l'historique Trakt. */
    val traktWatchedEpisodes: StateFlow<Set<String>> by lazy {
        combine(_series, trakt.library) { s, lib -> if (s == null) emptySet() else lib.watchedEpisodes(s.title, s.year) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    }

    /** Historique des épisodes de cette série, le plus récent d'abord (progression + « Reprendre »). */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val watched: StateFlow<List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>> by lazy {
        _series.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else history.episodesOf(s.providerId, s.remoteId) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** Même logique que MovieDetailViewModel.play. */
    fun playEpisode(
        seriesName: String, seriesRemoteId: String, providerId: Long, episode: EpisodeEntity,
        onReady: (url: String, title: String) -> Unit,
    ) {
        val tag = com.ultratv.tv.nativeapp.i18n.LatinDigits.episodeTag(episode.season, episode.episode)
        // Titre lisible (lecteur, « Reprendre la lecture ») : nom nettoyé de la série, pas « 4K-NF - … (2023) (KR) », et
        // titre d'épisode sans la répétition de la série et du marqueur.
        val seriesTitle = _series.value?.title?.takeIf { it.isNotBlank() } ?: seriesName
        val title = listOf(seriesTitle, tag, cleanEpisodeTitle(episode.title, seriesName, seriesTitle)).filter { it.isNotBlank() }.joinToString(" · ")
        playback.set(PlaybackContext.Item(
            providerId = providerId, kind = "EPISODE", remoteId = episode.remoteId,
            // Affiche : celle de la série (l'image d'épisode manque souvent) — sans elle, carte vide à l'accueil et
            // absence dans « Continuer à regarder » de Google TV, qui exige une image.
            title = title, poster = episode.image ?: _series.value?.let { it.poster ?: it.backdrop }, streamUrl = episode.streamUrl,
            parentRemoteId = seriesRemoteId,
        ))
        onReady(episode.streamUrl, title)
    }

    private val _series = MutableStateFlow<SeriesEntity?>(null)
    val series: StateFlow<SeriesEntity?> = _series.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Épisodes tels que la source les donne (Room). */
    private val _rawEpisodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())

    /** Saisons TMDB déjà chargées (numéro → épisodes), pour compléter titres génériques, résumés et vignettes. */
    private val _tmdbSeasons = MutableStateFlow<Map<Int, List<com.ultratv.tv.nativeapp.data.tmdb.TmdbEpisode>>>(emptyMap())

    /** Identifiant TMDB de la série, une fois la fiche enrichie résolue (null hors appairage ou série inconnue). */
    private val _tmdbId = MutableStateFlow<Int?>(null)

    /** Saison affichée (demandée par l'écran) : seule elle, et la suivante en arrière-plan, est chargée depuis TMDB. */
    private val _shownSeason = MutableStateFlow<Int?>(null)

    /** Épisodes affichés : ceux de la source, complétés par TMDB à l'affichage (aucune écriture en base). */
    private val _episodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    val episodes: StateFlow<List<EpisodeEntity>> = _episodes.asStateFlow()

    /** L'écran affiche la saison [n] : charge ses titres TMDB (et ceux de la suivante, en arrière-plan). */
    fun showSeason(n: Int?) { _shownSeason.value = n }

    init {
        viewModelScope.launch {
            combine(_rawEpisodes, _tmdbSeasons, _series) { eps, seasons, s ->
                com.ultratv.tv.nativeapp.data.tmdb.mergeEpisodes(eps, seasons, s?.name, s?.title)
            }.collect { _episodes.value = it }
        }
        viewModelScope.launch {
            combine(_tmdbId, _shownSeason) { id, n -> id to n }.distinctUntilChanged().collect { (id, n) ->
                if (id == null || n == null) return@collect
                fetchSeason(id, n)
                // Suivante en arrière-plan, seulement si la source la propose.
                if (_rawEpisodes.value.any { it.season == n + 1 }) launch { fetchSeason(id, n + 1) }
            }
        }
    }

    private suspend fun fetchSeason(tmdbId: Int, n: Int) {
        if (_tmdbSeasons.value.containsKey(n)) return
        val eps = tmdbSeasons.season(tmdbId, n) ?: return
        if (_tmdbId.value == tmdbId) _tmdbSeasons.value = _tmdbSeasons.value + (n to eps)
    }

    /** État de la fiche : chargement, prête, série retirée du catalogue, ou lecture locale en échec (cause classée). */
    private val _load = MutableStateFlow<DetailLoad>(DetailLoad.Loading)
    val load: StateFlow<DetailLoad> = _load.asStateFlow()

    /**
     * Cause de l'échec du chargement des épisodes (fournisseur injoignable, réponse illisible…) ; null si tout va bien. L'écran
     * ne l'affiche que si la base n'a aucun épisode : des épisodes déjà connus restent affichés.
     */
    private val _episodesError = MutableStateFlow<SyncErrorKind?>(null)
    val episodesError: StateFlow<SyncErrorKind?> = _episodesError.asStateFlow()

    /** Contrôle parental : tant que ce n'est pas [DetailLock.NONE], ni épisodes, ni métadonnées, ni image ne sont chargés ni affichés. */
    private val _lock = MutableStateFlow(DetailLock.NONE)
    val lock: StateFlow<DetailLock> = _lock.asStateFlow()
    /** Déverrouillage valable pour la visite (cette instance) : gardé en mémoire, jamais persisté. */
    private var unlocked = false

    /** Code accepté : la fiche se charge. Sans effet sur une catégorie masquée par le profil. */
    fun unlock() {
        if (_lock.value != DetailLock.PIN) return
        unlocked = true
        _lock.value = DetailLock.NONE
        val s0 = _series.value ?: return
        loadJob?.cancel(); episodesJob?.cancel(); collectorJob?.cancel()
        loadJob = viewModelScope.launch { loadRest(lastId, s0) }
    }

    private var lastId = 0L
    private var loadJob: kotlinx.coroutines.Job? = null
    private var episodesJob: kotlinx.coroutines.Job? = null
    private var collectorJob: kotlinx.coroutines.Job? = null

    /** Sources complémentaires déjà signalées pendant cette visite (une ligne de journal par source, jamais une par appel). */
    private val noted = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Compléments de la fiche (TMDB, notes, Fanart, TVmaze) : sans eux la fiche reste telle quelle ; l'échec laisse une ligne par source. */
    private suspend inline fun <T> enrich(source: String, block: () -> T): T? =
        try {
            block()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (noted.add(source)) UiLog.failure("series", source, t)
            null
        }

    /** Charge les épisodes de la source ; l'échec est classé dans [episodesError] (une ligne de journal), le succès l'efface. */
    private suspend fun fetchEpisodes(id: Long) {
        val r = uiAttempt("series", "episodes") { catalog.loadEpisodes(id) }
        _episodesError.value = r.exceptionOrNull()?.let { NetErrors.classify(it) }
    }

    /** Relance le chargement des épisodes (bouton « Réessayer » de la colonne des épisodes). */
    fun retryEpisodes() {
        val id = lastId
        _episodesError.value = null
        _loading.value = true
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            try {
                fetchEpisodes(id)
                // loadEpisodes complète plot / genre / année / distribution
                uiAttempt("series", "relecture") { catalog.seriesById(id) }.getOrNull()?.let { _series.value = it }
            } finally { _loading.value = false }
        }
    }

    /** Relit la série après un échec (bouton « Réessayer ») ou une synchro passée entre-temps. */
    fun retry() = load(lastId)

    fun load(id: Long) {
        // Retour du lecteur (même série déjà affichée) : la fiche reste affichée pendant la relecture, sans silhouette ni focus perdu.
        val alreadyShown = id == lastId && _load.value == DetailLoad.Ready
        // Autre titre : le déverrouillage de la visite précédente ne s'applique pas.
        if (id != lastId) unlocked = false
        lastId = id
        noted.clear()
        _episodesError.value = null
        if (!alreadyShown) _load.value = DetailLoad.Loading
        // Un chargement en cours est annulé (jamais deux chargements ni deux collecteurs d'épisodes concurrents).
        loadJob?.cancel(); episodesJob?.cancel(); collectorJob?.cancel()
        loadJob = viewModelScope.launch {
            val read = uiAttempt("series", "chargement") { catalog.seriesById(id) }
            val s0 = read.getOrNull()
            if (read.isFailure) { _load.value = DetailLoad.Failed(SyncErrorKind.OTHER); return@launch }
            _series.value = s0
            if (s0 == null) {
                // Série retirée de la source depuis l'accueil : jamais l'adresse ni l'identifiant de la source.
                UiLog.emit("ui.series", "introuvable id=$id")
                _load.value = DetailLoad.NotFound
                return@launch
            }
            // Verrou lu avant l'affichage : une lecture impossible laisse la fiche verrouillée (jamais ouverte par défaut).
            val lk = uiAttempt("series", "verrou") {
                // Clés masquées illisibles dans le délai : on ne devine pas, la fiche reste verrouillée.
                val hiddenKeys = kotlinx.coroutines.withTimeoutOrNull(3_000) { profiles.detailBlockedCategoryKeys.first() } ?: return@uiAttempt DetailLock.PIN
                detailLockOf("SERIES", s0.providerId, s0.categoryId, catalog.lockedCategoryIds(s0.providerId, "SERIES"), hiddenKeys, unlocked)
            }.getOrDefault(if (unlocked) DetailLock.NONE else DetailLock.PIN)
            _lock.value = lk
            _load.value = DetailLoad.Ready
            if (lk != DetailLock.NONE) return@launch
            loadRest(id, s0)
        }
    }

    /** Épisodes, métadonnées et compléments : seulement fiche non protégée. */
    private suspend fun loadRest(id: Long, s0: SeriesEntity): Unit = kotlinx.coroutines.coroutineScope {
        // Épisodes de la base : observés seulement quand la série existe ; une exception Room est journalisée, pas fatale.
        collectorJob = launch {
            try {
                catalog.episodes(id).collect { _rawEpisodes.value = it }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (t: Throwable) {
                UiLog.failure("series", "episodes-base", t)
            }
        }
        _loading.value = true
        fetchEpisodes(id)
        // loadEpisodes complète plot / genre / année / distribution
        val s = uiAttempt("series", "relecture") { catalog.seriesById(id) }.getOrNull() ?: s0
        _series.value = s
        _loading.value = false
        // Fiche enrichie TMDB (désactivée si l'appareil n'est pas appairé) : comble ce que la source ne fournit pas.
        enrich("tmdb") { tmdb.forSeries(s) }?.let {
            _series.value = com.ultratv.tv.nativeapp.data.tmdb.mergeSeries(s, it)
            it.trailerKey?.let { k -> _trailer.value = com.ultratv.tv.nativeapp.data.tmdb.TmdbTrailer.webUrl(k) }
            it.tmdbId?.let { tid -> _ratings.value = enrich("notes") { ratingsClient.forTmdb(com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV, tid) } }
            it.tmdbId?.let { tid -> loadExtras(tid) }
            _tmdbId.value = it.tmdbId
        }
    }

    /**
     * Compléments de la fiche (appareil appairé) : identifiants IMDb / TheTVDB (TMDB external_ids), puis épisode
     * précédent / prochain (TVmaze, IMDb d'abord) et logo (Fanart.tv, TheTVDB). Tout échec laisse la fiche telle quelle
     * (une ligne de journal par source).
     */
    private fun loadExtras(tmdbId: Int) {
        viewModelScope.launch {
            val ids = enrich("tmdb") { tmdb.externalIds(com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV, tmdbId) } ?: return@launch
            launch { _episodeLine.value = com.ultratv.tv.nativeapp.data.extmeta.TvMazeLogic.lineOf(enrich("tvmaze") { tvMaze.show(ids) }) }
            ids.tvdbId?.let { tvdb -> _logo.value = enrich("logo") { fanart.tv(tvdb, tmdb.langCode()) }?.logo }
        }
    }
}

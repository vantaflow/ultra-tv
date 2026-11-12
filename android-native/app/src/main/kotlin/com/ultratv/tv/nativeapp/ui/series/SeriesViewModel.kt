package com.ultratv.tv.nativeapp.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
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
    private val provider: ProviderRepository,
    private val history: com.ultratv.tv.nativeapp.data.repo.HistoryRepository,
    private val tmdb: com.ultratv.tv.nativeapp.data.tmdb.TmdbRepository,
    private val trakt: com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository,
    private val ratingsClient: com.ultratv.tv.nativeapp.data.tmdb.RatingsClient,
    private val fanart: com.ultratv.tv.nativeapp.data.extmeta.FanartRepository,
    private val tvMaze: com.ultratv.tv.nativeapp.data.extmeta.TvMazeRepository,
    private val tmdbSeasons: com.ultratv.tv.nativeapp.data.tmdb.TmdbSeasonRepository,
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
        val tag = "S${"%02d".format(episode.season)}E${"%02d".format(episode.episode)}"
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

    fun load(id: Long) {
        viewModelScope.launch {
            _series.value = catalog.seriesById(id)
            _loading.value = true
        }
        viewModelScope.launch {
            runCatching { catalog.loadEpisodes(id) }
            val s = catalog.seriesById(id)             // loadEpisodes complète plot / genre / année / distribution
            _series.value = s
            _loading.value = false
            // Fiche enrichie TMDB (désactivée si l'appareil n'est pas appairé) : comble ce que la source ne fournit pas.
            if (s != null) runCatching { tmdb.forSeries(s) }.getOrNull()?.let {
                _series.value = com.ultratv.tv.nativeapp.data.tmdb.mergeSeries(s, it)
                it.trailerKey?.let { k -> _trailer.value = com.ultratv.tv.nativeapp.data.tmdb.TmdbTrailer.webUrl(k) }
                it.tmdbId?.let { id -> _ratings.value = runCatching { ratingsClient.forTmdb(com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV, id) }.getOrNull() }
                it.tmdbId?.let { id -> loadExtras(id) }
                _tmdbId.value = it.tmdbId
            }
        }
        viewModelScope.launch {
            catalog.episodes(id).collect { _rawEpisodes.value = it }
        }
    }

    /**
     * Compléments de la fiche (appareil appairé) : identifiants IMDb / TheTVDB (TMDB external_ids), puis épisode
     * précédent / prochain (TVmaze, IMDb d'abord) et logo (Fanart.tv, TheTVDB). Tout échec laisse la fiche telle quelle.
     */
    private fun loadExtras(tmdbId: Int) {
        viewModelScope.launch {
            val ids = runCatching { tmdb.externalIds(com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV, tmdbId) }.getOrNull() ?: return@launch
            launch { _episodeLine.value = com.ultratv.tv.nativeapp.data.extmeta.TvMazeLogic.lineOf(runCatching { tvMaze.show(ids) }.getOrNull()) }
            ids.tvdbId?.let { tvdb -> _logo.value = runCatching { fanart.tv(tvdb, tmdb.langCode()) }.getOrNull()?.logo }
        }
    }
}

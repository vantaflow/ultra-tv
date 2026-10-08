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
) : ViewModel() {

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
        val title = "$seriesName · $tag · ${episode.title}"
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

    private val _episodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    val episodes: StateFlow<List<EpisodeEntity>> = _episodes.asStateFlow()

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
            if (s != null) runCatching { tmdb.forSeries(s) }.getOrNull()?.let { _series.value = com.ultratv.tv.nativeapp.data.tmdb.mergeSeries(s, it) }
        }
        viewModelScope.launch {
            catalog.episodes(id).collect { _episodes.value = it }
        }
    }
}

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
) : ViewModel() {

    /** Same logic as MovieDetailViewModel.play: resolve stalker:// first so the
     *  player gets a directly-playable URL. */
    fun playEpisode(
        seriesName: String, seriesRemoteId: String, providerId: Long, episode: EpisodeEntity,
        onReady: (url: String, title: String) -> Unit,
    ) {
        val tag = "S${"%02d".format(episode.season)}E${"%02d".format(episode.episode)}"
        val title = "$seriesName · $tag · ${episode.title}"
        fun register(url: String) {
            playback.set(PlaybackContext.Item(
                providerId = providerId, kind = "EPISODE", remoteId = episode.remoteId,
                title = title, poster = null, streamUrl = url,
                parentRemoteId = seriesRemoteId,
            ))
        }
        if (!episode.streamUrl.startsWith("stalker://")) {
            register(episode.streamUrl); onReady(episode.streamUrl, title); return
        }
        viewModelScope.launch {
            val resolved = provider.resolveStalkerUrl(providerId, episode.streamUrl)
            register(resolved); onReady(resolved, title)
        }
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
            _loading.value = false
        }
        viewModelScope.launch {
            catalog.episodes(id).collect { _episodes.value = it }
        }
    }
}

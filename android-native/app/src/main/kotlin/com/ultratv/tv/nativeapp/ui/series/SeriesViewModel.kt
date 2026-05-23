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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesListViewModel @Inject constructor(
    private val providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
    private val hiddenStore: HiddenCategoriesStore,
    private val seriesDao: com.ultratv.tv.nativeapp.data.db.SeriesDao,
) : ViewModel() {

    private val _sel = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _sel.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val pid = providers.value.firstOrNull { it.active }?.id
                ?: providers.value.firstOrNull()?.id
                ?: return@launch
            _refreshing.value = true
            try { providerRepo.syncAll(pid) } finally { _refreshing.value = false }
        }
    }

    private val providers = providerRepo.observeProviders()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = combine(
        providers, hiddenStore.hidden,
    ) { ps, hidden -> ps to hidden }
        .flatMapLatest { (ps, hidden) ->
            val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@flatMapLatest flowOf(emptyList())
            catalog.categories(pid, "SERIES").map { list ->
                list.filter { hiddenStore.keyFor("SERIES", pid, it.remoteId) !in hidden }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<SeriesEntity>> = combine(
        providers, _sel, hiddenStore.hidden,
    ) { ps, cat, hidden -> Triple(ps, cat, hidden) }
        .flatMapLatest { (ps, cat, hidden) ->
            val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@flatMapLatest flowOf(emptyList())
            val all: Flow<List<SeriesEntity>> = catalog.seriesList(pid)
            all.map { list ->
                list.filter { s ->
                    val cid = s.categoryId
                    if (cid != null && hiddenStore.keyFor("SERIES", pid, cid) in hidden) return@filter false
                    if (cat == null) true else s.categoryId == cat
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(remoteId: String?) { _sel.value = remoteId }

    val rails: StateFlow<List<SeriesRail>> = combine(
        providers, hiddenStore.hidden,
    ) { ps, hidden -> ps to hidden }
        .flatMapLatest { (ps, hidden) ->
            val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@flatMapLatest flowOf(emptyList())
            combine(catalog.categories(pid, "SERIES"), catalog.seriesList(pid)) { cats, series ->
                val visibleCats = cats.filter { hiddenStore.keyFor("SERIES", pid, it.remoteId) !in hidden }
                val groups = series.groupBy { it.categoryId }
                val out = mutableListOf<SeriesRail>()
                visibleCats.forEach { c ->
                    val items = groups[c.remoteId].orEmpty().take(25)
                    if (items.isNotEmpty()) out += SeriesRail(c, items)
                }
                val others = series.filter { it.categoryId == null || it.categoryId !in visibleCats.map { c -> c.remoteId }.toSet() }
                if (others.isNotEmpty()) out += SeriesRail(null, others.take(25))
                out
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val featured: StateFlow<SeriesEntity?> = items
        .map { list -> list.maxByOrNull { (it.year ?: 0) * 100L + (it.name.length % 100) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pagedSeries: kotlinx.coroutines.flow.Flow<PagingData<SeriesEntity>> = combine(
        providers, _sel,
    ) { ps, cat -> ps to cat }.flatMapLatest { (ps, cat) ->
        val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id
            ?: return@flatMapLatest kotlinx.coroutines.flow.emptyFlow()
        Pager(
            config = PagingConfig(pageSize = 60, prefetchDistance = 60, initialLoadSize = 120, enablePlaceholders = false),
            pagingSourceFactory = {
                if (cat == null) seriesDao.pagedAll(pid)
                else seriesDao.pagedForCategory(pid, cat)
            },
        ).flow
    }.cachedIn(viewModelScope)
}

data class SeriesRail(val category: CategoryEntity?, val items: List<SeriesEntity>)

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

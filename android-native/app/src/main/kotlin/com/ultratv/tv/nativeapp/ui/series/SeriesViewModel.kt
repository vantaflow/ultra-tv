package com.ultratv.tv.nativeapp.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesListViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val _sel = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _sel.asStateFlow()

    private val providers = providerRepo.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = providers.flatMapLatest { ps ->
        val pid = ps.firstOrNull()?.id ?: return@flatMapLatest flowOf(emptyList())
        catalog.categories(pid, "SERIES")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<SeriesEntity>> = combine(providers, _sel) { ps, cat -> ps to cat }
        .flatMapLatest { (ps, cat) ->
            val pid = ps.firstOrNull()?.id ?: return@flatMapLatest flowOf(emptyList())
            val all: Flow<List<SeriesEntity>> = catalog.seriesList(pid)
            if (cat == null) all else all.map { list -> list.filter { it.categoryId == cat } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(remoteId: String?) { _sel.value = remoteId }
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
) : ViewModel() {
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
        // Network refresh runs independently — UI shows DB rows immediately
        // and updates when the refresh completes.
        viewModelScope.launch {
            runCatching { catalog.loadEpisodes(id) }
            _loading.value = false
        }
        viewModelScope.launch {
            catalog.episodes(id).collect { _episodes.value = it }
        }
    }
}

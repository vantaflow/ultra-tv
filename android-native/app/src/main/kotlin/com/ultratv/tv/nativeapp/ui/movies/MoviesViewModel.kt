package com.ultratv.tv.nativeapp.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoviesViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
    private val hiddenStore: HiddenCategoriesStore,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val providers = providerRepo.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = combine(
        providers, hiddenStore.hidden,
    ) { ps, hidden -> ps to hidden }
        .flatMapLatest { (ps, hidden) ->
            val pid = ps.firstOrNull()?.id ?: return@flatMapLatest flowOf(emptyList())
            catalog.categories(pid, "MOVIE").map { list ->
                list.filter { hiddenStore.keyFor("MOVIE", pid, it.remoteId) !in hidden }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val movies: StateFlow<List<MovieEntity>> = combine(
        providers, _selectedCategory, hiddenStore.hidden,
    ) { ps, cat, hidden -> Triple(ps, cat, hidden) }
        .flatMapLatest { (ps, cat, hidden) ->
            val pid = ps.firstOrNull()?.id ?: return@flatMapLatest flowOf(emptyList())
            val all: Flow<List<MovieEntity>> = catalog.movies(pid)
            all.map { list ->
                list.filter { m ->
                    // Drop items whose category was marked hidden by the user.
                    val cid = m.categoryId
                    if (cid != null && hiddenStore.keyFor("MOVIE", pid, cid) in hidden) return@filter false
                    if (cat == null) true else m.categoryId == cat
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(remoteId: String?) { _selectedCategory.value = remoteId }
}

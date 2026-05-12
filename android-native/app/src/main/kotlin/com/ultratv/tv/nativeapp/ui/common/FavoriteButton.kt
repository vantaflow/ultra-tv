package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoriteToggleViewModel @Inject constructor(
    private val provider: ProviderRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    // (kind, remoteId) of the currently shown item.
    private val key = MutableStateFlow<Pair<String, String>?>(null)

    val isFav: StateFlow<Boolean> = key
        .flatMapLatest { k ->
            if (k == null) return@flatMapLatest flowOf(false)
            val pid = provider.firstActive()?.id ?: return@flatMapLatest flowOf(false)
            catalog.isFavorite(pid, k.first, k.second)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun set(kind: String, remoteId: String) { key.value = kind to remoteId }

    fun toggle() {
        val k = key.value ?: return
        viewModelScope.launch {
            val pid = provider.firstActive()?.id ?: return@launch
            val current = catalog.isFavorite(pid, k.first, k.second).first()
            catalog.setFavorite(pid, k.first, k.second, !current)
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun FavoriteButton(
    kind: String,
    remoteId: String,
    vm: FavoriteToggleViewModel = hiltViewModel(),
) {
    LaunchedEffect(kind, remoteId) { vm.set(kind, remoteId) }
    val on by vm.isFav.collectAsState()
    Button(onClick = { vm.toggle() }) {
        Text(if (on) "★ Favorited" else "☆ Add to favorites")
    }
}

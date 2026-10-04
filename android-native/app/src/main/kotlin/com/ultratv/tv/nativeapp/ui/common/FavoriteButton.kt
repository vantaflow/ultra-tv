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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Bouton rond (72 px) à cœur : plein et accent quand l'élément est en favori (maquettes FilmDetail / SerieDetail). */
@Composable
fun FavoriteButton(
    kind: String,
    remoteId: String,
    vm: FavoriteToggleViewModel = hiltViewModel(),
) {
    LaunchedEffect(kind, remoteId) { vm.set(kind, remoteId) }
    val on by vm.isFav.collectAsState()
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    com.ultratv.tv.nativeapp.ui.design.FocusSurface(
        onClick = { vm.toggle() },
        shape = androidx.compose.foundation.shape.CircleShape,
        bg = com.ultratv.tv.nativeapp.ui.design.Ux.Surface2,
        modifier = androidx.compose.ui.Modifier
            .size(72.design)
            .semantics { contentDescription = if (on) D.removeFavorite else D.addFavorite },
    ) { f ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            com.ultratv.tv.nativeapp.ui.design.DIcon(
                com.ultratv.tv.nativeapp.ui.design.Icons.Heart, 30.design,
                if (f) com.ultratv.tv.nativeapp.ui.design.Ux.TextOnLight else if (on) com.ultratv.tv.nativeapp.ui.design.Ux.Accent else com.ultratv.tv.nativeapp.ui.design.Ux.Text,
                fill = on,
            )
        }
    }
}


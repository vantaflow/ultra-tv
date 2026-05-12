package com.ultratv.tv.nativeapp.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val provider: ProviderRepository,
    catalog: CatalogRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    val channels: StateFlow<List<ChannelEntity>> =
        provider.observeProviders()
            .flatMapLatest { ps ->
                val pid = ps.firstOrNull { it.active }?.id ?: ps.firstOrNull()?.id
                if (pid == null) flowOf(emptyList()) else catalog.channels(pid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    /**
     * Resolves the playable URL for [channel] and invokes [onReady] with it.
     * For most providers this is a no-op (returns stream URL as-is); for
     * Stalker it issues a `create_link` call to mint a fresh per-session URL.
     */
    fun resolveAndPlay(channel: ChannelEntity, onReady: (url: String, title: String) -> Unit) {
        if (!channel.streamUrl.startsWith("stalker://")) {
            onReady(channel.streamUrl, channel.name)
            return
        }
        viewModelScope.launch {
            _resolving.value = true
            try {
                val resolved = provider.resolvePlayUrl(channel.id, channel.streamUrl)
                onReady(resolved, channel.name)
            } finally {
                _resolving.value = false
            }
        }
    }
}

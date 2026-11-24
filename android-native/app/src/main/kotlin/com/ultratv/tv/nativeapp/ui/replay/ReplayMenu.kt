package com.ultratv.tv.nativeapp.ui.replay

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Entrée « Replay » des menus (rail TV, rail tactile, « Plus ») : visible seulement si la source active a au moins une chaîne
 * avec archive. Faux par défaut (et dans les tests).
 */
val LocalReplayEnabled = staticCompositionLocalOf { false }

/** Icône de la rubrique Replay : flèche de retour arrière autour d'un triangle de lecture. */
object ReplayIcons {
    const val Replay = "M3 12a9 9 0 1 0 2.6-6.4M3 4v5h5M10 9v6l5-3z"
}

/** Calcule [LocalReplayEnabled] : relu au changement de source active et à la fin de chaque synchro (jamais par lot inséré). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReplayMenuViewModel @Inject constructor(
    provider: ProviderRepository,
    syncBus: SyncStatusBus,
    private val channelDao: ChannelDao,
) : ViewModel() {
    private val pid = provider.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()
    // Relu au début et à la fin de chaque synchro (jamais à chaque lot inséré).
    private val idle = syncBus.status.map { it == null }.distinctUntilChanged()

    val available: StateFlow<Boolean> = combine(pid, idle) { id, _ -> id }
        .mapLatest { id -> id != null && runCatching { channelDao.hasArchive(id) }.getOrDefault(false) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

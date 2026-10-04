package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Relance la synchro de la source active (tirer pour rafraîchir). L'indicateur suit la synchro réelle. */
@HiltViewModel
class RefreshViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val sync: SyncCoordinator,
    bus: SyncStatusBus,
) : ViewModel() {
    val syncing: StateFlow<Boolean> = bus.status.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refresh() {
        viewModelScope.launch {
            val all = providers.observeProviders().first()
            val id = all.firstOrNull { it.active }?.id ?: all.firstOrNull()?.id ?: return@launch
            sync.request(id, force = true)
        }
    }
}

/**
 * Tirer pour rafraîchir (tactile seulement). Sur TV c'est un simple conteneur : aucun comportement ajouté.
 * L'indicateur reste au moins ~1 s, puis suit l'état de la synchro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchRefresh(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!LocalTouch.current) { Box(modifier) { content() }; return }
    val vm: RefreshViewModel = hiltViewModel()
    val syncing by vm.syncing.collectAsState()
    var hold by remember { mutableStateOf(false) }
    LaunchedEffect(hold) { if (hold) { delay(1_200); hold = false } }
    PullToRefreshBox(isRefreshing = hold || syncing, onRefresh = { hold = true; vm.refresh() }, modifier = modifier) { content() }
}

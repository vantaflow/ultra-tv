package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val bus: SyncStatusBus,
    private val sync: com.ultratv.tv.nativeapp.data.sync.SyncCoordinator,
) : ViewModel() {
    val status = bus.status
    /** Pastille du rail : au plus 2 mises à jour par seconde. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val pill: kotlinx.coroutines.flow.StateFlow<SyncStatusBus.Status?> = bus.status.let { f -> kotlinx.coroutines.flow.flow { f.sample(500).collect { emit(it) } } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)
    val failure = bus.failure
    fun dismissFailure() = bus.clearFailure()
    fun retry(providerId: Long) {
        bus.clearFailure(providerId)
        sync.request(providerId, force = true)
    }
}

/**
 * Slim banner pinned at the top of the app while a sync runs. Shows the
 * provider name, current step, and a linear progress bar. Hidden when idle.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun SyncStatusBanner(onFixSource: () -> Unit = {}, vm: SyncStatusViewModel = hiltViewModel()) {
    val status by vm.status.collectAsState()
    val failure by vm.failure.collectAsState()
    // L'application reste utilisable (base locale, favoris, réglages) : l'échec n'est
    // qu'une bannière avec « Corriger la source » / « Réessayer », jamais un blocage.
    val f = failure
    if (f != null && status == null) {
        val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current.sync
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF3A1014))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⚠", fontSize = 14.sp)
            Text(
                "${f.provider} · ${S.messageFor(f.kind)}",
                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 2,
            )
            androidx.tv.material3.Button(onClick = { vm.retry(f.providerId) }) { Text(S.retry, fontSize = 12.sp) }
            androidx.tv.material3.Button(onClick = { vm.dismissFailure(); onFixSource() }) { Text(S.fixSource, fontSize = 12.sp) }
        }
    }
}

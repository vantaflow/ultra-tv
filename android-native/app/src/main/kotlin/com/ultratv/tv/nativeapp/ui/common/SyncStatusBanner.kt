package com.ultratv.tv.nativeapp.ui.common

import com.ultratv.tv.nativeapp.ui.design.Ux

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
import androidx.compose.runtime.setValue
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
import com.ultratv.tv.nativeapp.ui.design.spx
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
    OfflineBar()
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
                .background(Ux.SurfaceDeep)
                .padding(horizontal = 24.design, vertical = 10.design),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            com.ultratv.tv.nativeapp.ui.design.DIcon(com.ultratv.tv.nativeapp.ui.design.StateIcons.Warning, 28.design, Ux.Accent)
            Text(
                "${f.provider} · ${S.messageFor(f.kind)}",
                color = Ux.Text, fontSize = 22.spx, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope,
                modifier = Modifier.weight(1f), maxLines = 2,
            )
            com.ultratv.tv.nativeapp.ui.design.PillButton(S.retry, onClick = { vm.retry(f.providerId) }, heightPx = 52, hPadPx = 26, fontPx = 20)
            com.ultratv.tv.nativeapp.ui.design.PillButton(S.fixSource, onClick = { vm.dismissFailure(); onFixSource() }, heightPx = 52, hPadPx = 26, fontPx = 20, bg = Ux.Cta)
        }
    }
}


/** État « hors ligne » (maquette Etats) : barre fine tant qu'aucun réseau n'est disponible ; le contenu enregistré reste utilisable. */
@Composable
fun OfflineBar() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var online by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { online = !DebugConnectivity.forceOffline }
            override fun onLost(network: android.net.Network) { online = false }
        }
        online = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }.getOrDefault(true)
        if (DebugConnectivity.forceOffline) online = false
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
    if (online) return
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    Row(
        Modifier.fillMaxWidth().background(Ux.SurfaceDeep).padding(horizontal = 24.design, vertical = 10.design),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design),
    ) {
        com.ultratv.tv.nativeapp.ui.design.DIcon(com.ultratv.tv.nativeapp.ui.design.StateIcons.Offline, 28.design, Ux.Text2)
        Text(D.offlineBanner, color = Ux.Text, fontSize = 22.spx, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, maxLines = 1)
    }
}

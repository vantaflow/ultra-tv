package com.ultratv.tv.nativeapp.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.StartupNav
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.StateCard
import com.ultratv.tv.nativeapp.ui.design.StateIcons
import com.ultratv.tv.nativeapp.ui.design.Ux
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Build debug seulement : force l'état « hors ligne » (`--ez debug_offline true`) — l'émulateur ne sait pas couper son Ethernet. */
object DebugConnectivity { @Volatile var forceOffline = false }

/** Ce qui empêche d'obtenir des données : pas de réseau, ou la dernière synchro de la source a échoué. */
@HiltViewModel
class DataStateViewModel @Inject constructor(
    @ApplicationContext ctx: Context,
    bus: SyncStatusBus,
    private val sync: SyncCoordinator,
) : ViewModel() {
    val online: StateFlow<Boolean> = callbackFlow {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        // Le rappel ne signale que les CHANGEMENTS : l'état au démarrage se lit directement.
        if (DebugConnectivity.forceOffline) { trySend(false); awaitClose { }; return@callbackFlow }
        trySend(runCatching { cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }.getOrDefault(true))
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        awaitClose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val failure = bus.failure
    private val bus = bus
    fun retry(providerId: Long?) { providerId?.let { bus.clearFailure(it); sync.request(it, force = true) } }
}

/**
 * États PLEINE CARTE (maquette Etats) : « hors ligne » et « erreur de source » quand l'écran n'a AUCUNE donnée locale.
 * Quand des données existent, seule la bannière fine reste affichée (voir SyncStatusBanner). Retourne true s'il a
 * affiché une carte ; sinon l'appelant montre son message « vide » habituel.
 */
@Composable
fun NoDataStateCard(modifier: Modifier = Modifier, vm: DataStateViewModel = hiltViewModel()): Boolean {
    val online by vm.online.collectAsState()
    val failure by vm.failure.collectAsState()
    val D = LocalDs.current
    val S = LocalStrings.current.sync
    val f = failure
    if (online && f == null) return false
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        if (!online) {
            StateCard(
                D.offlineTitle, D.offlineBody, StateIcons.Offline, D.retry,
                onPrimary = { vm.retry(f?.providerId) }, badge = androidx.compose.ui.graphics.Color(0xFF52525B),
                secondaryLabel = D.seeRecordings, onSecondary = { StartupNav.pendingRoute.value = "recordings" },
                modifier = Modifier.responsiveWidth(820),
            )
        } else if (f != null) {
            StateCard(
                S.messageFor(f.kind), D.sourceErrorBody(f.provider), StateIcons.Warning, D.fixSource,
                onPrimary = { StartupNav.pendingRoute.value = Routes.SETTINGS }, badge = Ux.Accent,
                secondaryLabel = D.retry, onSecondary = { vm.retry(f.providerId) },
                modifier = Modifier.responsiveWidth(820),
            )
        }
    }
    return true
}

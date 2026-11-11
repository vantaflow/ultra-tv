package com.ultratv.tv.nativeapp.data.remote

import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RemoteUi {
    /** Désactivée, ou appli en arrière-plan. */
    data object Off : RemoteUi
    data object NoNetwork : RemoteUi
    data object Failed : RemoteUi
    data class On(val address: String, val url: String) : RemoteUi
}

/**
 * Cycle de vie du serveur : il tourne seulement si l'utilisateur l'a activé ET que l'appli est au premier plan
 * (MainActivity.onStart / onStop). Le jeton vit dans [RemoteStore] ; le régénérer coupe les anciens liens.
 */
@Singleton
class RemoteController @Inject constructor(
    private val store: RemoteStore,
    private val api: RemoteApiImpl,
    private val prefs: UserPreferencesStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var server: RemoteServer? = null
    private var serverIp: String? = null
    @Volatile private var token: String = ""
    @Volatile private var foreground = false

    private val _ui = MutableStateFlow<RemoteUi>(RemoteUi.Off)
    val ui: StateFlow<RemoteUi> = _ui
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    private val router = RemoteRouter({ token }, api) {
        RemoteNet.pageLang(runCatching { prefs.flow.first().language }.getOrDefault("system"), Locale.getDefault().language)
    }

    fun onForeground(v: Boolean) { foreground = v; refresh() }
    fun setEnabled(v: Boolean) { scope.launch { store.setEnabled(v); refresh() } }
    fun regenerate() { scope.launch { lock.withLock { token = store.regenerate() }; restart() } }

    /** Réévalue tout (réseau changé, écran ouvert…). */
    fun refresh() { scope.launch { sync(false) } }
    private suspend fun restart() = sync(true)

    private suspend fun sync(force: Boolean) = lock.withLock {
        val on = store.enabled.first()
        _enabled.value = on
        if (!on || !foreground) { stop(); _ui.value = RemoteUi.Off; return@withLock }
        if (token.isEmpty()) token = store.token()
        val ip = RemoteNet.pickLocalAddress(RemoteNet.interfaceAddresses())
        if (ip == null) { stop(); _ui.value = RemoteUi.NoNetwork; return@withLock }
        if (force || server == null || serverIp != ip.hostAddress) {
            stop()
            val s = runCatching { RemoteServer(router, ip) }.getOrNull()
            if (s == null) { _ui.value = RemoteUi.Failed; return@withLock }
            s.start(); server = s; serverIp = ip.hostAddress
        }
        val port = server?.port ?: RemoteServer.DEFAULT_PORT
        _ui.value = RemoteUi.On("http://${ip.hostAddress}:$port/r", RemoteNet.url(ip.hostAddress ?: "", port, token))
    }

    private fun stop() { server?.close(); server = null; serverIp = null }
}

package com.ultratv.tv.nativeapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de l'écran d'appairage affiché sur la TV. */
sealed interface PairingUi {
    data object Idle : PairingUi
    data object Requesting : PairingUi
    data class ShowCode(val code: String, val workerBase: String, val deviceLabel: String = "") : PairingUi
    data class Failed(val message: String) : PairingUi
}

/** Fenêtre « Depuis le téléphone » (envoi de source depuis le web, sans compte). */
sealed interface WebSendUi {
    data object Idle : WebSendUi
    data object Requesting : WebSendUi
    data class ShowCode(val code: String, val sendUrl: String, val address: String, val expiresInSec: Int) : WebSendUi
    data object Failed : WebSendUi
}

data class SettingsState(
    val providers: List<ProviderEntity> = emptyList(),
    val syncing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: ProviderRepository,
    private val remoteConfig: com.ultratv.tv.nativeapp.data.config.RemoteConfigImporter,
    private val deviceMac: com.ultratv.tv.nativeapp.data.config.DeviceMac,
    private val prefs: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
    private val backupRepo: com.ultratv.tv.nativeapp.data.repo.BackupRepository,
    private val cloudPairing: com.ultratv.tv.nativeapp.data.config.CloudPairing,
    private val deviceTokens: com.ultratv.tv.nativeapp.data.config.DeviceTokenStore,
    private val sync: com.ultratv.tv.nativeapp.data.sync.SyncCoordinator,
    private val cloudSync: com.ultratv.tv.nativeapp.data.config.CloudSyncManager,
    private val syncBus: com.ultratv.tv.nativeapp.data.repo.SyncStatusBus,
    private val webSend: com.ultratv.tv.nativeapp.data.config.WebSend,
    private val installIds: com.ultratv.tv.nativeapp.data.config.InstallIdStore,
    private val guideChecker: com.ultratv.tv.nativeapp.data.xmltv.GuideChecker,
    private val limits: com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore,
) : ViewModel() {

    // ---- « Guide de cette source » (adresse EPG propre à une source) ----

    fun customGuide(providerId: Long) = limits.customEpg(providerId)
    suspend fun verifyGuide(url: String) = guideChecker.check(url)

    /** Enregistre (ou, [url] vide, retire) le guide de la source, puis recharge son guide. */
    fun setCustomGuide(providerId: Long, url: String, mode: com.ultratv.tv.nativeapp.data.xmltv.GuideMode) {
        limits.setCustomEpg(providerId, if (url.isBlank()) null else com.ultratv.tv.nativeapp.data.xmltv.CustomGuide(url.trim(), mode))
        repo.retryEpg(providerId)
    }

    // ---- Synchro cloud multi-appareils ----

    val cloud: StateFlow<com.ultratv.tv.nativeapp.data.config.CloudSyncState> = cloudSync.state

    /** Source locale qui vient d'être ajoutée sur un appareil appairé : on propose de la partager (jamais automatiquement). */
    private val _offerShare = MutableStateFlow<Long?>(null)
    val offerShare: StateFlow<Long?> = _offerShare.asStateFlow()
    fun dismissOffer() { _offerShare.value = null }
    private fun offerIfPaired(id: Long) { if (deviceTokens.isPaired) _offerShare.value = id }

    suspend fun providerById(id: Long) = repo.byId(id)
    fun cloudIdOf(localId: Long): String? = cloudSync.cloudIdOf(localId)
    fun sharedWith(localId: Long): Int = cloudSync.sharedWith(localId)
    suspend fun connectionWarning(): Boolean = cloudSync.connectionWarning()

    /** Partage ([shareWith] = null : tous les appareils). */
    fun share(localId: Long, shareWith: List<String>?) {
        viewModelScope.launch {
            val ok = cloudSync.share(localId, shareWith)
            if (ok) cloudSync.sync(force = true)
            _message.value = if (ok) "Shared ✓" else "Sharing failed"
        }
    }

    fun stopSharing(localId: Long) { viewModelScope.launch { cloudSync.stopSharing(localId) } }
    fun renameThisDevice(name: String) { viewModelScope.launch { if (cloudSync.renameThisDevice(name)) cloudSync.sync(force = true) } }
    fun confirmRemovals(ids: Set<Long>) { viewModelScope.launch { cloudSync.confirmRemovals(ids) } }
    fun keepLocal(ids: Set<Long>) { viewModelScope.launch { cloudSync.keepLocal(ids) } }

    /** Mirrors UserPrefs.localLogosFolderUri for the Settings UI to display. */
    val localLogosFolderUri: kotlinx.coroutines.flow.StateFlow<String> = prefs.flow
        .map { it.localLogosFolderUri }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), "")

    fun setLocalLogosFolderUri(uri: String) = viewModelScope.launch { prefs.setLocalLogosFolderUri(uri) }

    private val _backupText = MutableStateFlow<String?>(null)
    val backupText: StateFlow<String?> = _backupText.asStateFlow()

    fun prepareBackup(
        readyMsg: String = "Backup ready — pick a file to save it.",
        password: String? = null,
    ) {
        viewModelScope.launch {
            _backupText.value = backupRepo.export(password)
            com.ultratv.tv.nativeapp.ui.common.Toaster.ok(readyMsg)
        }
    }

    fun consumeBackup(): String? {
        val t = _backupText.value
        _backupText.value = null
        return t
    }

    fun restoreBackup(
        text: String,
        restoredTemplate: String = "Restored %1\$d provider(s), %2\$d fav, %3\$d watch entries",
        failedPrefix: String = "Restore failed: ",
        password: String? = null,
    ) {
        viewModelScope.launch {
            try {
                val r = backupRepo.import(text, password)
                com.ultratv.tv.nativeapp.ui.common.Toaster.ok(
                    restoredTemplate.format(r.providers, r.favorites, r.historyEntries)
                )
            } catch (t: Throwable) {
                com.ultratv.tv.nativeapp.ui.common.Toaster.err(failedPrefix + (t.message ?: ""))
            }
        }
    }

    val deviceMacAddress: String = deviceMac.mac

    /**
     * Effective Worker URL: user override (DataStore) takes precedence over the
     * build default (BuildConfig.WORKER_URL, itself overridable with
     * -PULTRA_WORKER_URL). Each user can self-host their own Worker.
     */
    val workerBaseUrl: StateFlow<String> = prefs.flow
        .map { it.workerBaseUrl.ifBlank { DEFAULT_WORKER_URL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_WORKER_URL)

    companion object {
        /** URL par défaut injectée au build ; ce n'est pas un secret (le Worker authentifie par jeton). */
        val DEFAULT_WORKER_URL: String = com.ultratv.tv.nativeapp.BuildConfig.WORKER_URL
    }

    /** HTTPS exigé (HTTP seulement en debug) : le jeton et les identifiants transitent par cette URL. */
    private fun validWorkerUrl(raw: String): String? =
        com.ultratv.tv.nativeapp.data.config.WorkerUrl.normalize(raw, allowCleartext = com.ultratv.tv.nativeapp.BuildConfig.DEBUG)

    fun saveWorkerBase(url: String) {
        val normalized = validWorkerUrl(url)
        if (normalized == null) {
            com.ultratv.tv.nativeapp.ui.common.Toaster.err("Worker URL must start with https://")
            return
        }
        viewModelScope.launch {
            if (normalized != validWorkerUrl(workerBaseUrl.value)) {
                // Un jeton n'est valable que pour le Worker qui l'a délivré.
                deviceTokens.clear(); cloudSync.forget(); _paired.value = false
            }
            prefs.setWorkerBase(normalized)
            com.ultratv.tv.nativeapp.RemoteLog.workerUrlOverride = normalized
        }
    }

    // ---- Appairage avec le tableau de bord ----

    private val _paired = MutableStateFlow(deviceTokens.isPaired)
    /** Vrai quand un jeton d'appareil est stocké (Keystore). */
    val paired: StateFlow<Boolean> = _paired.asStateFlow()

    private val _pairing = MutableStateFlow<PairingUi>(PairingUi.Idle)
    val pairing: StateFlow<PairingUi> = _pairing.asStateFlow()
    private var pairingJob: kotlinx.coroutines.Job? = null

    /** Demande un code, l'affiche, attend la saisie dans le tableau de bord, puis synchronise. */
    fun startPairing() {
        val base = validWorkerUrl(workerBaseUrl.value)
        if (base == null) {
            _message.value = "Set a valid https:// Worker URL first."
            return
        }
        pairingJob?.cancel()
        _pairing.value = PairingUi.Requesting
        pairingJob = viewModelScope.launch {
            cloudPairing.run(base, android.os.Build.MODEL ?: deviceMac.mac).collect { ev ->
                when (ev) {
                    is com.ultratv.tv.nativeapp.data.config.PairingEvent.CodeReady ->
                        _pairing.value = PairingUi.ShowCode(ev.code, base, com.ultratv.tv.nativeapp.data.config.PairingLabel.of(deviceMac.mac))
                    com.ultratv.tv.nativeapp.data.config.PairingEvent.Paired -> {
                        _paired.value = true
                        _pairing.value = PairingUi.Idle
                        syncFromCloud()
                    }
                    com.ultratv.tv.nativeapp.data.config.PairingEvent.Expired ->
                        _pairing.value = PairingUi.Failed("The code expired. Try again.")
                    is com.ultratv.tv.nativeapp.data.config.PairingEvent.Failed ->
                        _pairing.value = PairingUi.Failed(ev.message)
                }
            }
        }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        _pairing.value = PairingUi.Idle
    }

    // ---- Envoi d'une source depuis un téléphone / un ordinateur (sans compte) ----

    private val _webSendUi = MutableStateFlow<WebSendUi>(WebSendUi.Idle)
    val webSendUi: StateFlow<WebSendUi> = _webSendUi.asStateFlow()
    private var webSendJob: kotlinx.coroutines.Job? = null

    /**
     * Demande un code, l'affiche (avec son QR), attend l'envoi du téléphone puis ajoute la source EXACTEMENT comme les
     * fenêtres Xtream / M3U / Stalker (même ajout, même synchronisation). [receivedText] / [stalkerFailedText] : libellés
     * localisés fournis par l'écran. L'identifiant d'installation n'est transmis que si la télémétrie est activée.
     */
    fun startWebSend(receivedText: String, stalkerFailedText: String, guideMessage: (com.ultratv.tv.nativeapp.data.xmltv.GuideCheck) -> String = { "" }) {
        val base = validWorkerUrl(workerBaseUrl.value)
        if (base == null) {
            _message.value = "Set a valid https:// Worker URL first."
            return
        }
        webSendJob?.cancel()
        _webSendUi.value = WebSendUi.Requesting
        webSendJob = viewModelScope.launch {
            val install = if (prefs.flow.first().telemetryEnabled) installIds.get() else null
            webSend.run(base, install).collect { ev ->
                when (ev) {
                    is com.ultratv.tv.nativeapp.data.config.WebSendEvent.CodeReady -> _webSendUi.value = WebSendUi.ShowCode(
                        ev.code,
                        com.ultratv.tv.nativeapp.data.config.WebSendLogic.sendUrl(base, ev.code),
                        com.ultratv.tv.nativeapp.data.config.WebSendLogic.shortAddress(base),
                        ev.expiresInSec,
                    )
                    is com.ultratv.tv.nativeapp.data.config.WebSendEvent.Received -> {
                        _webSendUi.value = WebSendUi.Idle
                        com.ultratv.tv.nativeapp.ui.common.Toaster.ok(receivedText)
                        addWebSource(ev.source, stalkerFailedText, guideMessage)
                    }
                    com.ultratv.tv.nativeapp.data.config.WebSendEvent.Expired,
                    is com.ultratv.tv.nativeapp.data.config.WebSendEvent.Failed -> _webSendUi.value = WebSendUi.Failed
                }
            }
        }
    }

    fun cancelWebSend() {
        webSendJob?.cancel()
        _webSendUi.value = WebSendUi.Idle
    }

    private fun addWebSource(s: com.ultratv.tv.nativeapp.data.config.WebSource, stalkerFailedText: String, guideMessage: (com.ultratv.tv.nativeapp.data.xmltv.GuideCheck) -> String) {
        when (s.kind) {
            // Adresse de guide envoyée depuis le téléphone : vérifiée comme à la télécommande, puis enregistrée comme guide complémentaire.
            "GUIDE" -> viewModelScope.launch {
                val r = guideChecker.check(s.url)
                if (r == com.ultratv.tv.nativeapp.data.xmltv.GuideCheck.Ok) {
                    prefs.setExtraEpg(s.url)
                    com.ultratv.tv.nativeapp.ui.common.Toaster.ok(guideMessage(r))
                } else com.ultratv.tv.nativeapp.ui.common.Toaster.err(guideMessage(r))
            }
            "XTREAM" -> addAndSync(s.name, s.url, s.username, s.password)
            "M3U" -> addM3uAndSync(s.name, s.url)
            "STALKER" -> addStalkerAndSync(s.name, s.url, s.mac, s.username, s.password, stalkerFailedText)
        }
    }

    /** Oublie le jeton local. Pour le révoquer côté serveur, supprimer l'appareil dans le tableau de bord. */
    fun unpair() {
        deviceTokens.clear()
        cloudSync.forget()
        _paired.value = false
        _message.value = "Device unpaired. Revoke it in the dashboard too if you lost it."
    }

    /** Récupère la configuration de cet appareil avec son jeton ; sans jeton, lance l'appairage. */
    fun syncFromCloud() {
        if (!deviceTokens.isPaired) { startPairing(); return }
        viewModelScope.launch {
            _syncing.value = true
            _message.value = "Asking the dashboard for this device's config…"
            try {
                validWorkerUrl(workerBaseUrl.value) ?: error("Invalid Worker URL (https:// required)")
                when (cloudSync.sync(force = true)) {
                    com.ultratv.tv.nativeapp.data.config.CloudSyncManager.Result.NotPaired -> { _paired.value = false; _message.value = "⚠ This device was revoked or unpaired. Pair it again." }
                    com.ultratv.tv.nativeapp.data.config.CloudSyncManager.Result.Failed -> _message.value = ""  // l'échec est présenté par la bannière globale
                    com.ultratv.tv.nativeapp.data.config.CloudSyncManager.Result.Done -> {
                        val st = cloudSync.state.value
                        _message.value = "Synchronized with the cloud ✓ (+${st.added} ~${st.updated} -${st.removed})"
                    }
                }
            } catch (e: com.ultratv.tv.nativeapp.data.config.TokenRejectedException) {
                _paired.value = false
                _message.value = "⚠ This device was revoked or unpaired. Pair it again."
            } catch (e: com.ultratv.tv.nativeapp.data.config.RateLimitedException) {
                _message.value = "Too many requests — retry in ${e.retryAfterSec}s."
            } catch (t: Throwable) {
                _message.value = ""  // l'échec est présenté, traduit, par la bannière globale (SyncStatusBanner)
            } finally {
                _syncing.value = false
            }
        }
    }

    fun importFromRemoteConfig(url: String) {
        viewModelScope.launch {
            _syncing.value = true
            _message.value = "Fetching config from $url…"
            try {
                val res = remoteConfig.importFromUrl(url) { _message.value = it }
                if (res.imported > 0 && repo.firstActive() == null) {
                    repo.observeProviders().first().firstOrNull()?.id?.let { repo.setDefault(it) }
                }
                val errs = if (res.errors.isEmpty()) "" else "  ·  ${res.errors.size} error(s): ${res.errors.first()}"
                _message.value = "Imported ${res.imported} provider(s)$errs"
            } catch (t: Throwable) {
                _message.value = ""  // l'échec est présenté, traduit, par la bannière globale (SyncStatusBanner)
            } finally {
                _syncing.value = false
            }
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    private val _syncing = MutableStateFlow(false)

    val providers: StateFlow<List<ProviderEntity>> =
        repo.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val message: StateFlow<String?> = _message.asStateFlow()
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Promotes the new provider to default iff nothing else is active yet. */
    private suspend fun makeDefaultIfNone(newId: Long) {
        if (repo.firstActive() == null) repo.setDefault(newId)
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            repo.setDefault(id)
            _message.value = "Default provider changed."
        }
    }

    /** Ajoute une source Xtream SANS lancer la synchro (l'assistant propose d'abord le choix des langues). */
    fun addXtreamOnly(name: String, baseUrl: String, username: String, password: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.addXtream(name, baseUrl, username, password)
            makeDefaultIfNone(id)
            offerIfPaired(id)
            onDone(id)
        }
    }

    fun startSync(providerId: Long) { sync.request(providerId, force = true) }

    fun addAndSync(name: String, baseUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _syncing.value = true
            _message.value = "Adding provider…"
            try {
                val id = repo.addXtream(name, baseUrl, username, password)
                makeDefaultIfNone(id)
                offerIfPaired(id)
                // Synchro confiée à WorkManager : la progression s'affiche via le SyncStatusBus.
                sync.request(id, force = true)
                _message.value = "Syncing…"
            } catch (t: Throwable) {
                _message.value = ""  // l'échec est présenté, traduit, par la bannière globale (SyncStatusBanner)
            } finally {
                _syncing.value = false
            }
        }
    }

    /**
     * Portail Stalker : la connexion est vérifiée AVANT l'enregistrement (point d'entrée sondé puis mémorisé) ; un portail
     * qui refuse la MAC n'est pas ajouté et [failedText] est affiché.
     */
    fun addStalkerAndSync(name: String, portal: String, mac: String, login: String, password: String, failedText: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _syncing.value = true
            StalkerTelemetry.addAttempt()
            try {
                // addStalker sonde le portail (point d'entrée + poignée de main) avant d'enregistrer la source.
                val id = repo.addStalker(name, portal, mac, login, password, probe = true)
                StalkerTelemetry.probeOk()
                makeDefaultIfNone(id)
                offerIfPaired(id)
                sync.request(id, force = true)
                watchStalkerSync(id)
                onDone(true)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                StalkerTelemetry.probeFailed(t)
                com.ultratv.tv.nativeapp.ui.common.Toaster.err(failedText)
                onDone(false)
            } finally {
                _syncing.value = false
            }
        }
    }

    /** Télémétrie de la première synchro d'une source Stalker : début, fin, type d'erreur (jamais d'identifiants). */
    private fun watchStalkerSync(id: Long) {
        viewModelScope.launch {
            val started = kotlinx.coroutines.withTimeoutOrNull(120_000) { syncBus.status.first { it != null } }
            if (started == null) { StalkerTelemetry.syncUnknown(); return@launch }
            StalkerTelemetry.syncStarted()
            val ended = kotlinx.coroutines.withTimeoutOrNull(30 * 60_000L) { syncBus.status.first { it == null } }
            if (ended == null) { StalkerTelemetry.syncUnknown(); return@launch }
            StalkerTelemetry.syncEnded(syncBus.failure.value?.takeIf { it.providerId == id }?.kind?.name)
        }
    }

    fun addM3uLocal(name: String, label: String, text: String) {
        viewModelScope.launch {
            _syncing.value = true
            _message.value = "Importing local M3U…"
            try {
                val id = repo.addM3uFromText(name, label, text)
                makeDefaultIfNone(id)
                _message.value = "Imported — restart the Live tab to see channels."
            } catch (t: Throwable) {
                _message.value = ""  // l'échec est présenté, traduit, par la bannière globale (SyncStatusBanner)
            } finally {
                _syncing.value = false
            }
        }
    }

    fun addM3uAndSync(name: String, url: String) {
        viewModelScope.launch {
            _syncing.value = true
            _message.value = "Adding M3U provider…"
            try {
                val id = repo.addM3u(name, url)
                makeDefaultIfNone(id)
                offerIfPaired(id)
                sync.request(id, force = true)
                _message.value = "Syncing…"
            } catch (t: Throwable) {
                _message.value = ""  // l'échec est présenté, traduit, par la bannière globale (SyncStatusBanner)
            } finally {
                _syncing.value = false
            }
        }
    }

    fun resync(providerId: Long) {
        viewModelScope.launch {
            _syncing.value = true
            try {
                sync.request(providerId, force = true)
                _message.value = "Syncing…"
            } catch (t: Throwable) {
                _message.value = ""  // l'échec est présenté, traduit, par la bannière globale (SyncStatusBanner)
            } finally {
                _syncing.value = false
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            _message.value = "Provider deleted"
        }
    }
}

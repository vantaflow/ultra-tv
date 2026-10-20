package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.net.dns.AppDns
import com.ultratv.tv.nativeapp.data.net.dns.DnsMode
import com.ultratv.tv.nativeapp.data.net.dns.DnsSettings
import com.ultratv.tv.nativeapp.data.net.dns.DohPreset
import com.ultratv.tv.nativeapp.data.net.dns.IpLiteral
import com.ultratv.tv.nativeapp.data.net.dns.SwitchableDns
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.GroupLabel
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Réglage DNS : lecture / écriture de [AppDns] et bouton « Tester » (hôte du fournisseur actif). */
@HiltViewModel
class DnsSettingsViewModel @Inject constructor(private val providers: ProviderRepository) : ViewModel() {
    val settings: StateFlow<DnsSettings> = AppDns.settings

    sealed interface TestState {
        data object Idle : TestState
        data object Running : TestState
        data class Done(val probe: SwitchableDns.Probe) : TestState
    }

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()
    private var testJob: Job? = null

    fun update(transform: (DnsSettings) -> DnsSettings) {
        AppDns.update(transform(settings.value))
        _test.value = TestState.Idle
    }

    fun runTest() {
        if (testJob?.isActive == true) return
        _test.value = TestState.Running
        testJob = viewModelScope.launch {
            val host = runCatching { providers.firstActive()?.baseUrl }.getOrNull()?.let { probeHostOf(it) } ?: DEFAULT_PROBE_HOST
            _test.value = TestState.Done(withContext(Dispatchers.IO) { AppDns.probe(host) })
        }
    }

    companion object {
        const val DEFAULT_PROBE_HOST = "example.com"

        /** Hôte (sans schéma, port ni chemin, qui peut contenir des identifiants) ; null pour une IP ou une source locale. */
        fun probeHostOf(baseUrl: String): String? {
            val uri = runCatching { java.net.URI(baseUrl.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
            val host = uri.host?.trim()?.removeSurrounding("[", "]")
            return host?.takeIf { it.isNotEmpty() && !IpLiteral.isValid(it) }
        }
    }
}

/** Libellé court du mode courant (ligne « Serveur DNS »). */
internal fun dnsModeLabel(L: DnsStrings, s: DnsSettings): String = when (s.mode) {
    DnsMode.SYSTEM -> L.system
    DnsMode.GOOGLE -> "Google · 8.8.8.8"
    DnsMode.CLOUDFLARE -> "Cloudflare · 1.1.1.1"
    DnsMode.QUAD9 -> "Quad9 · 9.9.9.9"
    DnsMode.DOH -> "${L.doh} · ${dohPresetLabel(L, s.dohPreset)}"
    DnsMode.CUSTOM -> "${L.custom} · ${listOf(s.primary, s.secondary).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { L.notSet }}"
}

internal fun dohPresetLabel(L: DnsStrings, p: DohPreset): String = when (p) {
    DohPreset.CLOUDFLARE -> "Cloudflare"
    DohPreset.GOOGLE -> "Google"
    DohPreset.QUAD9 -> "Quad9"
    DohPreset.CUSTOM -> L.custom
}

/** Rubrique « Réseau » des Réglages › Lecture : choix du DNS (pas de champ de saisie dans le volet, uniquement dans des dialogues). */
@Composable
internal fun DnsSection(vm: DnsSettingsViewModel = hiltViewModel()) {
    val D = LocalDs.current
    val L = remember(D.lang) { DnsStrings(D.lang) }
    val s by vm.settings.collectAsState()
    val test by vm.test.collectAsState()
    var choice by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(L.group)
        PrefRow(L.server, dnsModeLabel(L, s), hint = L.vlcNote) { choice = "mode" }
        if (s.mode == DnsMode.DOH) {
            PrefRow(L.dohProvider, dohPresetLabel(L, s.dohPreset)) { choice = "doh" }
            if (s.dohPreset == DohPreset.CUSTOM) PrefRow(L.dohUrl, s.dohUrl.ifBlank { L.notSet }) { choice = "dohUrl" }
        }
        if (s.mode == DnsMode.CUSTOM) {
            PrefRow(L.servers, listOf(s.primary, s.secondary).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { L.notSet }) { choice = "ips" }
        }
        val testValue = when (val t = test) {
            DnsSettingsViewModel.TestState.Idle -> ""
            DnsSettingsViewModel.TestState.Running -> L.testing
            is DnsSettingsViewModel.TestState.Done -> {
                val p = t.probe
                val ip = p.addresses.firstOrNull()?.hostAddress
                when {
                    ip == null -> L.testFailed
                    p.fellBack && p.error != null -> L.testFallback(ip, p.error)
                    else -> L.testOk(ip, p.millis)
                }
            }
        }
        val testHint = (test as? DnsSettingsViewModel.TestState.Done)?.probe?.host ?: L.testHint
        PrefRow(L.test, testValue, hint = testHint) { vm.runTest() }
        Text(L.scopeNote, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, modifier = Modifier.padding(horizontal = 8.design))
    }

    when (choice) {
        "mode" -> ChoiceDialog(
            L.server,
            listOf(
                DnsMode.SYSTEM to L.system,
                DnsMode.GOOGLE to "Google (8.8.8.8 · 8.8.4.4)",
                DnsMode.CLOUDFLARE to "Cloudflare (1.1.1.1 · 1.0.0.1)",
                DnsMode.QUAD9 to "Quad9 (9.9.9.9 · 149.112.112.112)",
                DnsMode.DOH to L.doh,
                DnsMode.CUSTOM to L.custom,
            ),
            s.mode,
            { m ->
                vm.update { it.copy(mode = m) }
                // Personnalisé sans adresse / DoH personnalisé sans URL : on enchaîne directement sur la saisie.
                choice = when {
                    m == DnsMode.CUSTOM && s.primary.isBlank() -> "ips"
                    m == DnsMode.DOH && s.dohPreset == DohPreset.CUSTOM && s.dohUrl.isBlank() -> "dohUrl"
                    else -> ""
                }
            },
            { choice = "" },
        )
        "doh" -> ChoiceDialog(
            L.dohProvider,
            DohPreset.entries.map { it to (if (it == DohPreset.CUSTOM) L.custom else "${dohPresetLabel(L, it)} · ${it.url.removePrefix("https://")}") },
            s.dohPreset,
            { p -> vm.update { it.copy(dohPreset = p) }; choice = if (p == DohPreset.CUSTOM && s.dohUrl.isBlank()) "dohUrl" else "" },
            { choice = "" },
        )
        "dohUrl" -> DohUrlDialog(L, s.dohUrl, onSave = { url -> vm.update { it.copy(dohUrl = url) }; choice = "" }, onDismiss = { choice = "" })
        "ips" -> DnsServersDialog(L, s.primary, s.secondary, onSave = { a, b -> vm.update { it.copy(primary = a, secondary = b) }; choice = "" }, onDismiss = { choice = "" })
    }
}

@Composable
private fun DohUrlDialog(L: DnsStrings, current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(current.ifBlank { "https://" }) }
    val ok = DnsSettings.validDohUrl(url) != null
    AddProviderDialog(
        title = L.dohUrl, subtitle = L.dohUrlSub, icon = Icons.Link,
        onDismiss = onDismiss, onSubmit = { onSave(url.trim()) }, canSubmit = ok,
        submitLabel = com.ultratv.tv.nativeapp.i18n.LocalStrings.current.save,
    ) {
        FormField(L.dohUrl, url, { url = it }, keyboardType = KeyboardType.Uri, placeholder = "https://", autoFocus = true, last = true, required = true,
            error = if (url.isBlank() || url.trim() == "https://" || ok) null else L.invalidDohUrl, testTag = "field-doh-url")
    }
}

@Composable
private fun DnsServersDialog(L: DnsStrings, primary: String, secondary: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(primary) }
    var b by remember { mutableStateOf(secondary) }
    val okA = IpLiteral.isValid(a)
    val okB = b.isBlank() || IpLiteral.isValid(b)
    AddProviderDialog(
        title = L.servers, subtitle = L.serversSub, icon = Icons.Link,
        onDismiss = onDismiss, onSubmit = { onSave(a.trim(), b.trim()) }, canSubmit = okA && okB,
        submitLabel = com.ultratv.tv.nativeapp.i18n.LocalStrings.current.save,
    ) {
        FormField(L.primary, a, { a = it }, keyboardType = KeyboardType.Uri, placeholder = "9.9.9.9", autoFocus = true, required = true,
            error = if (a.isBlank() || okA) null else L.invalidIp, testTag = "field-dns-primary")
        FormField(L.secondary, b, { b = it }, keyboardType = KeyboardType.Uri, placeholder = "2620:fe::fe", last = true,
            error = if (okB) null else L.invalidIp, testTag = "field-dns-secondary")
    }
}

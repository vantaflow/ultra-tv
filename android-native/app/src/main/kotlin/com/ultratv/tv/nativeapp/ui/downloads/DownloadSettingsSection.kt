package com.ultratv.tv.nativeapp.ui.downloads

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ultratv.tv.nativeapp.data.downloads.DownloadPrefs
import com.ultratv.tv.nativeapp.data.downloads.DownloadRepository
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.GroupLabel
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadSettingsViewModel @Inject constructor(
    private val prefs: DownloadPrefs,
    private val repo: DownloadRepository,
) : ViewModel() {
    val state = prefs.state
    val externalAvailable: Boolean get() = prefs.externalAvailable()
    fun setWifiOnly(v: Boolean) = repo.setWifiOnly(v)
    fun setLocation(v: String) = prefs.setLocation(v)
}

/** Réglages › Lecture › Téléchargements : Wi-Fi seulement, emplacement (stockage propre à l'appli, jamais partagé). */
@Composable
fun DownloadSettingsSection(vm: DownloadSettingsViewModel = hiltViewModel()) {
    val s = rememberDownloadStrings()
    val p by vm.state.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    val external = vm.externalAvailable
    val locationLabel = if (p.location == DownloadPrefs.LOCATION_INTERNAL || !external) s.locationInternal else s.locationExternal
    Column(Modifier.padding(top = 18.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(s.navTitle)
        SwitchPrefRow(s.wifiOnly, p.wifiOnly, hint = s.wifiOnlyHint) { vm.setWifiOnly(it) }
        PrefRow(s.location, locationLabel, hint = s.locationHint) { if (external) choosing = true }
    }
    if (choosing) ChoiceDialog(
        s.location,
        listOf(DownloadPrefs.LOCATION_EXTERNAL to s.locationExternal, DownloadPrefs.LOCATION_INTERNAL to s.locationInternal),
        p.location,
        { vm.setLocation(it); choosing = false },
        { choosing = false },
    )
}

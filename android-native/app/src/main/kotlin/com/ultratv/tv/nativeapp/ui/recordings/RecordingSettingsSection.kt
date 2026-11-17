package com.ultratv.tv.nativeapp.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import com.ultratv.tv.nativeapp.data.recording.RecordingStorage
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.recLocDevice
import com.ultratv.tv.nativeapp.i18n.recLocInternal
import com.ultratv.tv.nativeapp.i18n.recLocNas
import com.ultratv.tv.nativeapp.i18n.recLocUsb
import com.ultratv.tv.nativeapp.i18n.recLocation
import com.ultratv.tv.nativeapp.i18n.recLocationHint
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecordingSettingsViewModel @Inject constructor(private val storage: RecordingStorage) : ViewModel() {
    val location = storage.location
    suspend fun options() = storage.options()
    fun setLocation(key: String) = storage.setLocation(key)
}

internal fun recOptionLabel(D: DesignStrings, o: RecordingStorage.Option): String = when (o.kind) {
    RecordingStorage.Kind.INTERNAL -> D.recLocInternal
    RecordingStorage.Kind.DEVICE -> D.recLocDevice
    RecordingStorage.Kind.USB -> D.recLocUsb(o.name)
    RecordingStorage.Kind.NAS -> D.recLocNas(o.name)
}

/** Réglages › Lecture : emplacement des enregistrements (mémoire interne, appareil, clé USB branchée, partage NAS). */
@Composable
fun RecordingSettingsSection(vm: RecordingSettingsViewModel = hiltViewModel()) {
    val D = LocalDs.current
    val loc by vm.location.collectAsState()
    var options by remember { mutableStateOf<List<RecordingStorage.Option>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }
    LaunchedEffect(choosing) { options = vm.options() }
    // Emplacement absent (clé débranchée, NAS retiré) : l'enregistrement se fait sur l'appareil, et c'est ce qui s'affiche.
    val current = options.firstOrNull { it.key == loc } ?: options.firstOrNull { it.kind == RecordingStorage.Kind.DEVICE } ?: options.firstOrNull()
    Column(Modifier.padding(top = 10.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
        com.ultratv.tv.nativeapp.ui.design.SettingsCard {
            PrefRow(D.recLocation, current?.let { recOptionLabel(D, it) } ?: D.recLocDevice, hint = D.recLocationHint) { choosing = true }
        }
    }
    if (choosing && options.isNotEmpty()) ChoiceDialog(
        D.recLocation,
        options.map { it.key to recOptionLabel(D, it) },
        current?.key ?: loc,
        { vm.setLocation(it); choosing = false },
        { choosing = false },
    )
}

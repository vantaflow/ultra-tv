package com.ultratv.tv.nativeapp.ui.parental

import com.ultratv.tv.nativeapp.ui.common.leaveOnVerticalDpad
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import javax.inject.Inject

@HiltViewModel
class ParentalViewModel @Inject constructor(
    private val store: ParentalStore,
) : ViewModel() {

    val pinSet: StateFlow<Boolean> = store.pinHash
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch { store.setPin(pin); onDone() }
    }

    suspend fun check(pin: String): Boolean = store.check(pin)
}

/** Lignes « Contrôle parental » des Réglages (style valeur + chevron). */
@Composable
fun ParentalSection(
    vm: ParentalViewModel = hiltViewModel(),
    onManageLockedChannels: () -> Unit = {},
) {
    val set by vm.pinSet.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.ultratv.tv.nativeapp.ui.design.PrefRow(S.parentalChangePin.takeIf { set } ?: S.parentalSetPin, if (set) S.parentalPinEnabled else S.parentalPinNotSet) { dialog = true }
        if (set) com.ultratv.tv.nativeapp.ui.design.PrefRow(S.parentalClearPin, "") { vm.setPin("") {} }
        com.ultratv.tv.nativeapp.ui.design.PrefRow(S.parentalManageLocked, "", onClick = onManageLockedChannels)
    }
    if (dialog) PinSetDialog(onCancel = { dialog = false }, onConfirm = { pin -> vm.setPin(pin) { dialog = false } })
}

/** Création du code : saisie puis confirmation sur le pavé de la maquette. */
@Composable
private fun PinSetDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    var first by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    PinPad(
        title = S.parentalSetTitle, subtitle = if (first == null) S.parentalPinHint else S.parentalConfirmHint,
        onComplete = { pin ->
            val f = first
            if (f == null) { first = pin; error = null; attempt++ }
            else if (f == pin) onConfirm(pin) else { first = null; error = S.parentalWrongPin; attempt++ }
        },
        onCancel = onCancel, error = error, resetKey = attempt,
    )
}

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
import com.ultratv.tv.nativeapp.ui.design.Text
import javax.inject.Inject

@HiltViewModel
class ParentalViewModel @Inject constructor(
    private val store: ParentalStore,
    profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
) : ViewModel() {
    /** Profil Enfants : le contrôle parental est forcé, son code ne peut pas être retiré. */
    val parentalForced: StateFlow<Boolean> = profiles.current
        .map { com.ultratv.tv.nativeapp.data.profile.KidsRules.forcesParentalControl(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)


    val pinSet: StateFlow<Boolean> = store.pinHash
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Comme [pinSet] mais null tant que le magasin n'a pas répondu : l'invite ne doit JAMAIS déverrouiller sur la valeur initiale. */
    val pinSetKnown: StateFlow<Boolean?> = store.pinHash
        .map<String?, Boolean?> { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Premier code (aucun code en place). Effacer ou remplacer passe par [clearWith] / [changeWith]. */
    fun setPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch { if (!store.isSet()) store.setPin(pin); onDone() }
    }

    suspend fun clearWith(current: String): Boolean = store.clearWith(current)
    suspend fun changeWith(current: String, newPin: String): Boolean = store.changeWith(current, newPin)

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
    // Effacer / changer : le code ACTUEL est demandé d'abord ; sans lui, rien ne change.
    var askClear by remember { mutableStateOf(false) }
    var askChange by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf<String?>(null) }
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    com.ultratv.tv.nativeapp.ui.design.SettingsCard {
        com.ultratv.tv.nativeapp.ui.design.PrefRow(S.parentalChangePin.takeIf { set } ?: S.parentalSetPin, if (set) S.parentalPinEnabled else S.parentalPinNotSet) { if (set) askChange = true else dialog = true }
        val forced by vm.parentalForced.collectAsState()
        if (set && !forced) com.ultratv.tv.nativeapp.ui.design.PrefRow(S.parentalClearPin, "") { askClear = true }
        com.ultratv.tv.nativeapp.ui.design.PrefRow(S.parentalManageLocked, "", onClick = onManageLockedChannels)
    }
    if (askClear) CurrentPinDialog(S.parentalClearPin, onCancel = { askClear = false }, verify = { pin -> vm.clearWith(pin) }, onOk = { askClear = false })
    if (askChange) CurrentPinDialog(S.parentalChangePin, onCancel = { askChange = false }, verify = { pin -> vm.check(pin) }, onOk = { pin -> verified = pin; askChange = false; dialog = true })
    if (dialog) PinSetDialog(onCancel = { dialog = false; verified = null }, onConfirm = { pin ->
        val cur = verified
        if (cur == null) vm.setPin(pin) { dialog = false }
        else scope.launch { vm.changeWith(cur, pin); verified = null; dialog = false }
    })
}

/** Saisie du code actuel sur le pavé, même gestion d'erreurs et d'essais que le déverrouillage ([ParentalStore.check]). */
@Composable
private fun CurrentPinDialog(title: String, onCancel: () -> Unit, verify: suspend (String) -> Boolean, onOk: (String) -> Unit) {
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    PinPad(
        title = title, subtitle = S.parentalPinHint,
        onComplete = { pin -> scope.launch { if (verify(pin)) onOk(pin) else { error = S.parentalWrongPin; attempt++ } } },
        onCancel = onCancel, error = error, resetKey = attempt,
    )
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

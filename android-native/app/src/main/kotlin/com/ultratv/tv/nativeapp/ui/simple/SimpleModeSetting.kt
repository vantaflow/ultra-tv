package com.ultratv.tv.nativeapp.ui.simple

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.SimpleStrings
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.parental.PinPad
import com.ultratv.tv.nativeapp.ui.profile.ProfileStrings
import com.ultratv.tv.nativeapp.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Pavé du code parental pour QUITTER le mode simple (même mécanique que [com.ultratv.tv.nativeapp.ui.profile.TechnicalGate]).
 * Appelé seulement quand un code existe ([SimpleHome.exitNeedsPin]).
 */
@Composable
fun ExitSimpleModePin(onUnlocked: () -> Unit, onCancel: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val S = SimpleStrings(LocalDs.current.lang)
    val P = ProfileStrings(LocalDs.current.lang)
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    PinPad(
        title = S.exitTitle, subtitle = "",
        onComplete = { pin -> scope.launch { if (vm.checkParentalPin(pin)) onUnlocked() else { error = P.wrongPin; attempt++ } } },
        onCancel = onCancel, error = error, resetKey = attempt,
    )
}

/**
 * Ligne « Mode simple » de Réglages › Affichage (profil courant). Activer est libre ; désactiver demande le code
 * parental s'il en existe un.
 */
@Composable
fun SimpleModeRow(enabled: Boolean, onChange: (Boolean) -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val S = SimpleStrings(LocalDs.current.lang)
    val pinSet by vm.parentalPinSet.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    SwitchPrefRow(S.settingName, enabled, hint = S.settingHint) { on ->
        if (!on && SimpleHome.exitNeedsPin(pinSet)) askPin = true else onChange(on)
    }
    if (askPin) ExitSimpleModePin(onUnlocked = { askPin = false; onChange(false) }, onCancel = { askPin = false })
}

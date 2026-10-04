package com.ultratv.tv.nativeapp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.profile.KidsRules
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PaneTitle
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.parental.PinPad
import kotlinx.coroutines.launch

/**
 * Réglages techniques (sources, synchro, lecteur, à propos) : libres hors profil Enfants ; sinon le code parental
 * est exigé (une fois par session de Réglages). Sans code parental défini, l'accès reste fermé (cf. [KidsRules]).
 */
@Composable
fun TechnicalGate(onDeny: () -> Unit, vm: ProfileViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val S = ProfileStrings(LocalDs.current.lang)
    val current by vm.current.collectAsState()
    val pinSet by vm.parentalPinSet.collectAsState()
    var verified by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    when {
        KidsRules.canOpenTechnicalSettings(current, pinSet, verified) -> content()
        !pinSet -> Column {
            PaneTitle(S.kidsLocked, S.kidsNeedPin)
        }
        else -> {
            Text(S.kidsLocked, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx)
            PinPad(
                title = S.kidsLocked, subtitle = "",
                onComplete = { pin -> scope.launch { if (vm.checkParentalPin(pin)) verified = true else { error = S.wrongPin; attempt++ } } },
                onCancel = onDeny, error = error, resetKey = attempt,
            )
        }
    }
}

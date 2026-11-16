package com.ultratv.tv.nativeapp.ui.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.screentime.BlockReason
import com.ultratv.tv.nativeapp.data.screentime.ScreenTimeController
import com.ultratv.tv.nativeapp.data.screentime.ScreenTimeState
import com.ultratv.tv.nativeapp.data.screentime.Verdict
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.parental.PinPad
import com.ultratv.tv.nativeapp.ui.profile.ProfileStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val controller: ScreenTimeController,
    private val parental: ParentalStore,
    private val profiles: ProfileRepository,
) : ViewModel() {
    val state: StateFlow<ScreenTimeState> = controller.state
    val current: StateFlow<ProfileEntity?> = profiles.current
    val pinSet: StateFlow<Boolean> = parental.pinHash.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    suspend fun checkPin(pin: String) = parental.check(pin)
    fun grantExtra() = controller.grantExtra()
    fun grantToday() = controller.grantToday()
    fun switchProfile() = profiles.requestSwitch()
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

/**
 * Écran de blocage (hors plage horaire ou limite quotidienne atteinte) posé au-dessus de toute l'application, et
 * avertissement 5 minutes avant la fin. [active] est faux pendant « Qui regarde ? » et l'assistant de premier lancement
 * (le blocage s'applique au profil choisi ensuite).
 */
@Composable
fun ScreenTimeOverlay(active: Boolean, vm: ScreenTimeViewModel = hiltViewModel()) {
    if (!active) return
    val st by vm.state.collectAsState()
    val v = st.verdict
    if (v is Verdict.Blocked) BlockScreen(v, vm) else if (v is Verdict.Allowed && v.warn) WarnBanner(v.secondsLeft)
}

@Composable
private fun WarnBanner(secondsLeft: Long) {
    val S = ScreenTimeStrings(LocalDs.current.lang)
    // Une seule fois par épisode d'avertissement : masqué après 8 s, réarmé quand on sort de la zone des 5 minutes.
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(8_000); shown = false }
    if (!shown) return
    Box(Modifier.fillMaxSize().padding(top = 48.design), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.clip(RoundedCornerShape(20.design)).background(Ux.SurfaceDeep).padding(horizontal = 36.design, vertical = 20.design)) {
            Text(S.warn(((secondsLeft + 59) / 60).toInt().coerceAtLeast(1)), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1)
        }
    }
}

@Composable
private fun BlockScreen(v: Verdict.Blocked, vm: ScreenTimeViewModel) {
    val lang = LocalDs.current.lang
    val S = ScreenTimeStrings(lang)
    val P = ProfileStrings(lang)
    val current by vm.current.collectAsState()
    val pinSet by vm.pinSet.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var choose by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalFocusScope(onBack = {}, modifier = Modifier.background(Ux.Bg)) {
        Column(Modifier.fillMaxSize().padding(96.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.design, Alignment.CenterVertically)) {
            Text(if (v.reason == BlockReason.OUTSIDE_WINDOW) S.blockOutside else S.blockLimit, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 56.spx, textAlign = TextAlign.Center, maxLines = 3)
            current?.let { Text(it.name, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 32.spx, maxLines = 1) }
            val next = v.nextAllowedMs?.let { S.blockNext(ScreenTimeText.next(it, ZoneId.systemDefault(), lang)) } ?: S.blockNone
            Text(next, color = Ux.Text3, fontFamily = Manrope, fontSize = 30.spx, textAlign = TextAlign.Center, maxLines = 2)
            // Sans code parental il n'y a pas de dérogation possible : seul le changement de profil reste proposé.
            if (pinSet) PillButton(S.unlock, { error = null; askPin = true }, heightPx = 72, hPadPx = 40, fontPx = 26, bg = Ux.Surface2)
            PillButton(P.switchProfile, { vm.switchProfile() }, heightPx = 64, hPadPx = 32, fontPx = 22, bg = Ux.Surface)
        }
    }
    if (askPin) PinPad(
        title = S.pinTitle, subtitle = "",
        onComplete = { pin -> scope.launch { if (vm.checkPin(pin)) { askPin = false; choose = true } else { error = P.wrongPin; attempt++ } } },
        onCancel = { askPin = false }, error = error, resetKey = attempt,
    )
    if (choose) ChoiceDialog(S.unlock, listOf("extra" to S.unlockExtra, "today" to S.unlockToday), null, onPick = { k ->
        if (k == "extra") vm.grantExtra() else vm.grantToday()
        choose = false
    }, onDismiss = { choose = false })
}

package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.remote.RemoteController
import com.ultratv.tv.nativeapp.data.remote.RemoteUi
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.remoteDisable
import com.ultratv.tv.nativeapp.i18n.remoteEnable
import com.ultratv.tv.nativeapp.i18n.remoteEyebrow
import com.ultratv.tv.nativeapp.i18n.remoteFailed
import com.ultratv.tv.nativeapp.i18n.remoteHeadline
import com.ultratv.tv.nativeapp.i18n.remoteNewKey
import com.ultratv.tv.nativeapp.i18n.remoteNewKeyHint
import com.ultratv.tv.nativeapp.i18n.remoteNoNetwork
import com.ultratv.tv.nativeapp.i18n.remoteOff
import com.ultratv.tv.nativeapp.i18n.remoteOnlyForeground
import com.ultratv.tv.nativeapp.i18n.remoteScanHint
import com.ultratv.tv.nativeapp.i18n.remoteStatusOn
import com.ultratv.tv.nativeapp.i18n.remoteStep1
import com.ultratv.tv.nativeapp.i18n.remoteStep2
import com.ultratv.tv.nativeapp.i18n.remoteStep3
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PhoneRemoteViewModel @Inject constructor(private val controller: RemoteController) : ViewModel() {
    val ui = controller.ui
    val enabled = controller.enabled
    fun refresh() = controller.refresh()
    fun setEnabled(v: Boolean) = controller.setEnabled(v)
    fun newKey() = controller.regenerate()
}

/**
 * « Télécommande sur le téléphone » : plein écran, comme « Depuis le téléphone ». À gauche les étapes et les boutons
 * (Activer / Désactiver, Nouvelle clé), à droite le QR code vers `http://<ip>:<port>/r?k=<jeton>` et l'adresse sans le jeton.
 * Retour (télécommande) ferme ; le focus initial est sur Activer / Désactiver.
 */
@Composable
fun PhoneRemoteDialog(vm: PhoneRemoteViewModel, onClose: () -> Unit) {
    val D = LocalDs.current
    val ui by vm.ui.collectAsState()
    val enabled by vm.enabled.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 96.design, vertical = 54.design)) {
            Row(Modifier.height(72.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                LogoMark(56)
                Text("ULTRA TV", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, letterSpacing = TextUnit(1.7f, TextUnitType.Sp), maxLines = 1)
            }
            Row(Modifier.weight(1f).fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(120.design)) {
                val on = ui as? RemoteUi.On
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(36.design)) {
                    Text(D.remoteEyebrow, color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = TextUnit(3f, TextUnitType.Sp), maxLines = 1)
                    Text(D.remoteHeadline, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, lineHeight = 56.spx, maxLines = 4)
                    Column(verticalArrangement = Arrangement.spacedBy(20.design)) {
                        Step(1, D.remoteStep1)
                        Step(2, D.remoteStep2(on?.address.orEmpty()))
                        Step(3, D.remoteStep3)
                    }
                    Text(D.remoteOnlyForeground, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                        val req = remember { FocusRequester() }
                        var f by remember { mutableStateOf(false) }
                        RequestInitialFocus(req, hasFocus = { f })
                        PillButton(if (enabled) D.remoteDisable else D.remoteEnable, onClick = { vm.setEnabled(!enabled) }, heightPx = 76, hPadPx = 40, fontPx = 26, weight = FontWeight.Bold, bg = Ux.Cta, modifier = Modifier.focusRequester(req).onFocusChanged { f = it.isFocused })
                        if (enabled) PillButton(D.remoteNewKey, onClick = { vm.newKey() }, heightPx = 76, hPadPx = 36, fontPx = 24)
                        PillButton(LocalStrings.current.cancel, onClick = onClose, heightPx = 76, hPadPx = 36, fontPx = 24)
                    }
                    if (enabled) Text(D.remoteNewKeyHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 2)
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(56.design),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.design),
                ) {
                    when {
                        on != null -> {
                            QrCode(on.url, 240.design)
                            Text(D.remoteScanHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                            // Adresse affichée SANS le jeton : il n'est que dans le QR code.
                            Text(on.address, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1, textAlign = TextAlign.Center)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                                Box(Modifier.size(14.design).clip(CircleShape).background(Ux.Accent))
                                Text(D.remoteStatusOn, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
                            }
                        }
                        !enabled -> Text(D.remoteOff, color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx, textAlign = TextAlign.Center)
                        ui is RemoteUi.NoNetwork -> Text(D.remoteNoNetwork, color = Ux.Err, fontFamily = Manrope, fontSize = 26.spx, textAlign = TextAlign.Center)
                        else -> Text(D.remoteFailed, color = Ux.Err, fontFamily = Manrope, fontSize = 26.spx, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.config.WebSendLogic
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.phoneAddressLabel
import com.ultratv.tv.nativeapp.i18n.phoneCodeLabel
import com.ultratv.tv.nativeapp.i18n.phoneEyebrow
import com.ultratv.tv.nativeapp.i18n.phoneFailed
import com.ultratv.tv.nativeapp.i18n.phoneRequesting
import com.ultratv.tv.nativeapp.i18n.phoneScanHint
import com.ultratv.tv.nativeapp.i18n.phoneStep1
import com.ultratv.tv.nativeapp.i18n.phoneStep2
import com.ultratv.tv.nativeapp.i18n.phoneStep3
import com.ultratv.tv.nativeapp.i18n.phoneTitle
import com.ultratv.tv.nativeapp.i18n.phoneWaiting
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*

/**
 * « Depuis le téléphone » : plein écran comme l'appairage cloud. À gauche les étapes, à droite le QR code (vers
 * `/envoyer?c=CODE`), l'adresse courte et le code ; la source est ajoutée dès que le téléphone l'a envoyée.
 * Retour (télécommande) ferme ; le focus initial est sur « Nouveau code ».
 */
@Composable
fun PhoneSendDialog(state: WebSendUi, onCancel: () -> Unit, onRetry: () -> Unit) {
    if (state is WebSendUi.Idle) return
    val D = LocalDs.current
    val shownAt = remember(state is WebSendUi.ShowCode) { android.os.SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(shownAt) }
    LaunchedEffect(state) { while (state is WebSendUi.ShowCode) { now = android.os.SystemClock.elapsedRealtime(); kotlinx.coroutines.delay(1000) } }
    ModalFocusScope(onBack = onCancel, modifier = Modifier.background(Ux.Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 96.design, vertical = 54.design)) {
            Row(Modifier.height(72.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                LogoMark(56)
                Text("ULTRA TV", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, letterSpacing = TextUnit(1.7f, TextUnitType.Sp), maxLines = 1)
            }
            Row(Modifier.weight(1f).fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(120.design)) {
                val show = state as? WebSendUi.ShowCode
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(40.design)) {
                    Text(D.phoneEyebrow, color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = TextUnit(3f, TextUnitType.Sp), maxLines = 1)
                    Text(D.phoneTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 56.spx, lineHeight = 60.spx, maxLines = 4)
                    Column(verticalArrangement = Arrangement.spacedBy(22.design)) {
                        Step(1, D.phoneStep1(show?.address.orEmpty()))
                        Step(2, D.phoneStep2)
                        Step(3, D.phoneStep3)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                        val req = remember { FocusRequester() }
                        var f by remember { mutableStateOf(false) }
                        RequestInitialFocus(req, hasFocus = { f })
                        PillButton(D.pairNewCode, onClick = onRetry, heightPx = 76, hPadPx = 40, fontPx = 26, weight = FontWeight.Bold, bg = Ux.Cta, modifier = Modifier.focusRequester(req).onFocusChanged { f = it.isFocused })
                        PillButton(LocalStrings.current.cancel, onClick = onCancel, heightPx = 76, hPadPx = 36, fontPx = 24)
                    }
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(56.design),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.design),
                ) {
                    when (state) {
                        WebSendUi.Requesting -> Text(D.phoneRequesting, color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx)
                        is WebSendUi.ShowCode -> {
                            QrCode(state.sendUrl, 240.design)
                            Text(D.phoneScanHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                            Text(D.phoneAddressLabel, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, letterSpacing = TextUnit(2.2f, TextUnitType.Sp), maxLines = 1)
                            Text(state.address, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1, textAlign = TextAlign.Center)
                            Text(D.phoneCodeLabel, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, letterSpacing = TextUnit(2.2f, TextUnitType.Sp), maxLines = 1)
                            CodeBoxes(WebSendLogic.groupCode(state.code))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                                Box(Modifier.size(14.design).clip(CircleShape).background(Ux.Accent))
                                Text(D.phoneWaiting(formatRemaining(state.expiresInSec * 1000L - (now - shownAt))), color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
                            }
                        }
                        // Jamais le message brut du service : il peut contenir une adresse.
                        WebSendUi.Failed -> Text(D.phoneFailed, color = Ux.Err, fontFamily = Manrope, fontSize = 26.spx, textAlign = TextAlign.Center)
                        WebSendUi.Idle -> Unit
                    }
                }
            }
        }
    }
}

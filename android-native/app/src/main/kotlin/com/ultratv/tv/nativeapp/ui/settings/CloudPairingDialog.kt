package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.ui.common.responsiveWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*

/** Durée de validité d'un code d'appairage (le serveur l'annonce à 10 minutes). */
private const val CODE_TTL_MS = 10 * 60_000L

/** « K7Q2M9XF » → « K7Q2-M9XF » ; un code déjà tiré est conservé. */
internal fun groupPairingCode(code: String): String =
    if (code.contains('-') || code.length < 6) code else code.substring(0, code.length / 2) + "-" + code.substring(code.length / 2)

/** « 9:42 » : temps restant, jamais négatif. */
internal fun formatRemaining(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
}

/** Appairage (maquette Appairage) : plein écran, étapes à gauche, code en cases à droite, expiration décomptée. */
@Composable
fun CloudPairingDialog(state: PairingUi, onCancel: () -> Unit, onRetry: () -> Unit) {
    if (state is PairingUi.Idle) return
    val D = LocalDs.current
    val shownAt = remember(state is PairingUi.ShowCode) { android.os.SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(shownAt) }
    LaunchedEffect(state) { while (state is PairingUi.ShowCode) { now = android.os.SystemClock.elapsedRealtime(); kotlinx.coroutines.delay(1000) } }
    ModalFocusScope(onBack = onCancel, modifier = Modifier.background(Ux.Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 96.design, vertical = 54.design)) {
            Row(Modifier.height(72.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                LogoMark(56)
                Text("ULTRA TV", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, letterSpacing = TextUnit(1.7f, TextUnitType.Sp), maxLines = 1)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(120.design)) {
                Column(Modifier.responsiveWidth(760), verticalArrangement = Arrangement.spacedBy(40.design)) {
                    Text(D.pairEyebrow, color = Ux.Accent, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = TextUnit(3f, TextUnitType.Sp), maxLines = 1)
                    Text(D.pairTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 64.spx, lineHeight = 67.spx, maxLines = 3)
                    val host = (state as? PairingUi.ShowCode)?.workerBase?.let { hostOnly(it).substringAfter("://") }.orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(22.design)) {
                        Step(1, D.pairStep1(host))
                        Step(2, D.pairStep2)
                        Step(3, D.pairStep3)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                        val req = remember { FocusRequester() }
                        var f by remember { androidx.compose.runtime.mutableStateOf(false) }
                        RequestInitialFocus(req, hasFocus = { f })
                        PillButton(D.pairNewCode, onClick = onRetry, heightPx = 76, hPadPx = 40, fontPx = 26, weight = FontWeight.Bold, bg = Ux.Cta, modifier = Modifier.focusRequester(req).onFocusChanged { f = it.isFocused })
                        PillButton(LocalStrings.current.cancel, onClick = onCancel, heightPx = 76, hPadPx = 36, fontPx = 24)
                    }
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(64.design),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.design),
                ) {
                    Text(D.pairYourCode, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = TextUnit(2.2f, TextUnitType.Sp), maxLines = 1)
                    when (state) {
                        PairingUi.Requesting -> Text(D.pairRequesting, color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx)
                        is PairingUi.ShowCode -> {
                            CodeBoxes(groupPairingCode(state.code))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                                Box(Modifier.size(14.design).clip(CircleShape).background(Ux.Accent))
                                Text(D.pairWaiting(formatRemaining(CODE_TTL_MS - (now - shownAt))), color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
                            }
                            Text(D.pairDevice(android.os.Build.MODEL ?: "Android TV"), color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                        }
                        // Jamais le message brut du service : il peut contenir une adresse.
                        is PairingUi.Failed -> Text(D.pairFailed, color = Ux.Err, fontFamily = Manrope, fontSize = 26.spx, textAlign = TextAlign.Center)
                        PairingUi.Idle -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.design), verticalAlignment = Alignment.Top) {
        Text("$n", color = Ux.Accent, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx)
        Text(text, color = Ux.Text, fontFamily = Manrope, fontSize = 28.spx, lineHeight = 36.spx, maxLines = 2)
    }
}

@Composable
private fun CodeBoxes(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.design), verticalAlignment = Alignment.CenterVertically) {
        code.forEach { ch ->
            if (ch == '-') Text("-", color = Ux.Muted2, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 56.spx)
            else Box(Modifier.width(72.design).height(120.design).clip(RoundedCornerShape(20.design)).background(Ux.Surface), contentAlignment = Alignment.Center) {
                Text(ch.toString(), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 60.spx, maxLines = 1)
            }
        }
    }
}

/** Adresse du tableau de bord (Worker) : même style que les formulaires de source ; HTTPS exigé (validé par le modèle de vue). */
@Composable
fun WorkerUrlDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    var url by remember { androidx.compose.runtime.mutableStateOf(current) }
    val S = LocalStrings.current
    val ok = com.ultratv.tv.nativeapp.data.config.WorkerUrl.normalize(url, allowCleartext = com.ultratv.tv.nativeapp.BuildConfig.DEBUG) != null
    AddProviderDialog(
        title = D.workerUrlTitle, subtitle = D.workerUrlSub, icon = Icons.Link,
        onDismiss = onDismiss, onSubmit = { onSave(url.trim()) }, canSubmit = ok,
        submitLabel = LocalStrings.current.save,
    ) {
        FormField(D.workerUrlField, url, { url = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            placeholder = "https://", autoFocus = true, last = true, required = true,
            error = if (url.isBlank() || ok) null else S.wiz.invalidUrl, testTag = "field-worker")
    }
}

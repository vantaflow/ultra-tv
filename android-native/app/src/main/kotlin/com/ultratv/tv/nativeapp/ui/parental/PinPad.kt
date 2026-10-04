package com.ultratv.tv.nativeapp.ui.parental

import com.ultratv.tv.nativeapp.ui.common.responsiveWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * Pavé PIN plein écran (maquette Parental.dc.html) : cadenas 96, titre Sora 52, quatre cases 88×104, pavé 3×4 (touche 80),
 * « Annuler ». Les chiffres de la télécommande (KEYCODE_0..9) et Retour arrière fonctionnent aussi. [onComplete] est appelé
 * à la saisie du dernier chiffre ; [error] fait trembler/rougir les cases et vide la saisie.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
    error: String? = null,
    length: Int = 4,
    resetKey: Any? = null,
) {
    val D = LocalDs.current
    var pin by remember(resetKey) { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(resetKey) { runCatching { first.requestFocus() } }
    fun press(d: String) { if (pin.length < length) { pin += d; if (pin.length == length) onComplete(pin) } }
    fun erase() { pin = pin.dropLast(1) }

    ModalFocusScope(
        onBack = onCancel,
        modifier = Modifier.background(Ux.Bg).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val d = when (e.key) { Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"; Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"
                Key.Five, Key.NumPad5 -> "5"; Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"; Key.Nine, Key.NumPad9 -> "9"; else -> null }
            when {
                d != null -> { press(d); true }
                e.key == Key.Backspace || e.key == Key.Delete -> { erase(); true }
                else -> false
            }
        },
    ) {
        Column(Modifier.responsiveWidth(720), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.design)) {
            Box(Modifier.size(96.design).clip(CircleShape).background(Ux.Surface), contentAlignment = Alignment.Center) {
                DIcon("M6 10V8a6 6 0 0 1 12 0v2M5 10h14v11H5z", 44.design, Ux.Text)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.design)) {
                Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(error ?: subtitle, color = if (error != null) Ux.Err else Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.design)) {
                for (i in 0 until length) {
                    val filled = i < pin.length
                    Box(
                        Modifier.width(88.design).height(104.design).clip(RoundedCornerShape(20.design)).background(if (filled) Ux.Surface2 else Ux.SurfaceDeep)
                            .then(if (i == pin.length && error == null) Modifier.border(3.design, Ux.Accent, RoundedCornerShape(20.design)) else if (error != null) Modifier.border(3.design, Color(0xFFFF5A5A), RoundedCornerShape(20.design)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { if (filled) Box(Modifier.size(22.design).clip(CircleShape).background(Ux.Text)) }
                }
            }
            Column(Modifier.width(420.design), verticalArrangement = Arrangement.spacedBy(14.design)) {
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
                keys.chunked(3).forEach { rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.design)) {
                        rowKeys.forEach { k ->
                            if (k.isEmpty()) Spacer(Modifier.weight(1f).height(80.design))
                            else FocusSurface(
                                onClick = { if (k == "⌫") erase() else press(k) }, shape = RoundedCornerShape(18.design), bg = Ux.Surface, ringWidth = 5.design,
                                modifier = Modifier.weight(1f).height(80.design).then(if (k == "5") Modifier.focusRequester(first) else Modifier),
                            ) { f ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(k, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            FocusSurface(onClick = onCancel, shape = RoundedCornerShape(26.design), bg = Color.Transparent, ringWidth = 5.design, modifier = Modifier.height(52.design)) { f ->
                Box(Modifier.fillMaxWidth().width(240.design), contentAlignment = Alignment.Center) {
                    Text(com.ultratv.tv.nativeapp.i18n.LocalStrings.current.cancel, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
                }
            }
        }
    }
}

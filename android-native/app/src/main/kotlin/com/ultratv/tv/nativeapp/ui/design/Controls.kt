package com.ultratv.tv.nativeapp.ui.design

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design

/** Interrupteur de la maquette : piste 64×36, bouton 28 ; actif = accent (noir si la ligne est focalisée), inactif = #3F3F46 (#D4D4D8 si focalisée). */
@Composable
fun Switch(on: Boolean, inverted: Boolean = false) {
    val onDarkFocus = Ux.Cta == Color.White   // focus blanc (thème sombre) ; encre en thème clair
    val track = when {
        on -> if (inverted && onDarkFocus) Color(0xFF0A0A0C) else Ux.Accent
        inverted -> if (onDarkFocus) Color(0xFFD4D4D8) else Color(0xFF52525B)
        else -> Ux.Line
    }
    Box(Modifier.width(64.design).height(36.design).clip(RoundedCornerShape(18.design)).background(track)) {
        Box(Modifier.padding(start = if (on) 32.design else 4.design, top = 4.design).width(28.design).height(28.design).clip(RoundedCornerShape(14.design)).background(Color.White))
    }
}

private const val CHEVRON = "M9 6l6 6-6 6"

/** Ligne de réglage « valeur + chevron » (maquette Réglages) : 84 px, fond #141418, blanche + anneau au focus. */
@Composable
fun PrefRow(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1.0f, modifier = modifier.fillMaxWidth().height(84.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 32.design), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hint != null) Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.design))
            Text(value, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.design))
            Spacer(Modifier.width(12.design))
            DIcon(CHEVRON, 20.design, if (f) Ux.OnFocus2 else Ux.Text3, strokeWidth = 2.5f)
        }
    }
}

/** Ligne de réglage avec interrupteur. */
@Composable
fun SwitchPrefRow(label: String, on: Boolean, modifier: Modifier = Modifier, hint: String? = null, onChange: (Boolean) -> Unit) {
    FocusSurface(onClick = { onChange(!on) }, shape = RoundedCornerShape(18.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1.0f, modifier = modifier.fillMaxWidth().height(84.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 32.design), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hint != null) Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(on, inverted = f)
        }
    }
}

/** Titre de rubrique (Sora 40) avec sous-titre facultatif. */
@Composable
fun PaneTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GroupLabel(text: String) = Text(text.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1)

/** Boîte de choix modale (liste de valeurs, la courante cochée) — remplace les anciennes puces. */
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    val first = FocusRequester()
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.widthIn(min = 560.design, max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design), verticalArrangement = Arrangement.spacedBy(12.design)) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.design))
            LazyColumn(Modifier.heightIn(max = 640.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
                items(options, key = { it.second }) { (key, label) ->
                    val sel = key == selected
                    FocusSurface(onClick = { onPick(key) }, shape = RoundedCornerShape(16.design), bg = if (sel) Ux.Surface2 else Ux.Surface, ringWidth = 5.design, focusedScale = 1.0f, modifier = Modifier.fillMaxWidth().height(68.design)) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (sel) DIcon(Icons.Check, 24.design, if (f) Ux.TextOnLight else Ux.Accent, strokeWidth = 3f)
                        }
                    }
                }
            }
        }
    }
}

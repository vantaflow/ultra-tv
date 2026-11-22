package com.ultratv.tv.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.sourcePickerTitle
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * Choix de la source depuis le menu latéral : une ligne par source, la source active cochée et focalisée à l'ouverture.
 * OK choisit (même effet que Réglages › Source › Définir par défaut), Retour ferme sans rien changer.
 */
@Composable
fun SourcePickerDialog(choices: List<SourceChoice>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    val activeFocus = remember { FocusRequester() }
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.width(760.design).background(Ux.SurfaceDeep, RoundedCornerShape(32.design)).padding(40.design),
            verticalArrangement = Arrangement.spacedBy(12.design),
        ) {
            Text(D.sourcePickerTitle, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, color = Ux.Text, maxLines = 1)
            Spacer(Modifier.height(8.design))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.design)) {
                for (c in choices) {
                    FocusSurface(
                        onClick = { onPick(c.id) },
                        shape = RoundedCornerShape(18.design),
                        bg = if (c.active) Ux.Accent.copy(alpha = 0.18f) else Ux.Surface,
                        modifier = Modifier.fillMaxWidth().height(80.design).testTag("source-pick-${c.id}")
                            .then(if (c.active) Modifier.focusRequester(activeFocus) else Modifier),
                    ) { f ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 28.design).height(80.design), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx, color = if (f) Ux.TextOnLight else Ux.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (c.active) Box(Modifier.size(32.design), contentAlignment = Alignment.Center) { DIcon(Icons.Check, 28.design, if (f) Ux.TextOnLight else Ux.AccentInk, strokeWidth = 2.6f) }
                        }
                    }
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { activeFocus.requestFocus() } }
}

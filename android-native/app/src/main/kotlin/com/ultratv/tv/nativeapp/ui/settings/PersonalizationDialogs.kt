package com.ultratv.tv.nativeapp.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.accentColor
import com.ultratv.tv.nativeapp.i18n.accentName
import com.ultratv.tv.nativeapp.i18n.doneLabel
import com.ultratv.tv.nativeapp.i18n.homeBlockName
import com.ultratv.tv.nativeapp.i18n.homeRows
import com.ultratv.tv.nativeapp.i18n.homeRowsHint
import com.ultratv.tv.nativeapp.i18n.moveDown
import com.ultratv.tv.nativeapp.i18n.moveUp
import com.ultratv.tv.nativeapp.i18n.resetDefault
import com.ultratv.tv.nativeapp.i18n.rowHidden
import com.ultratv.tv.nativeapp.i18n.rowShown
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.ChoiceRow
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Switch
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.home.HomeBlock
import com.ultratv.tv.nativeapp.ui.home.HomeLayout

/** Choix de la couleur d'accent : pastille de la couleur devant chaque nom, la couleur active cochée. */
@Composable
internal fun AccentDialog(current: AccentChoice, onPick: (AccentChoice) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.widthIn(min = 560.design, max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design),
            verticalArrangement = Arrangement.spacedBy(10.design),
        ) {
            Text(D.accentColor, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.design))
            AccentChoice.entries.forEach { a ->
                ChoiceRow(D.accentName(a), a == current, { onPick(a) }, heightPx = 68, fontPx = 24, bg = Ux.Surface, selectedBg = Ux.Surface2, ringWidth = 5.design, leading = {
                    Box(Modifier.size(32.design).clip(CircleShape).background(Color(a.argb)).border(2.design, Color(0x33FFFFFF), CircleShape))
                })
            }
        }
    }
}

private const val UP = "M6 15l6-6 6 6"
private const val DOWN = "M6 9l6 6 6-6"

/**
 * Rangées de l'accueil : chaque ligne = [interrupteur + nom] (OK affiche / masque) puis ▲ et ▼ (déplacer).
 * Chaque modification est enregistrée tout de suite ; le focus suit la rangée déplacée.
 */
@Composable
internal fun HomeRowsDialog(initial: HomeLayout, onSave: (HomeLayout) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    var layout by remember { mutableStateOf(initial) }
    val focus = remember { HomeBlock.entries.associateWith { Triple(FocusRequester(), FocusRequester(), FocusRequester()) } }
    // Après un déplacement : (bloc, bouton 1 = ▲ / 2 = ▼) à refocaliser une fois la liste recomposée.
    var refocus by remember { mutableStateOf<Pair<HomeBlock, Int>?>(null) }
    LaunchedEffect(refocus) {
        val (b, which) = refocus ?: return@LaunchedEffect
        val t = focus.getValue(b)
        runCatching { (if (which == 1) t.second else t.third).requestFocus() }
        refocus = null
    }
    LaunchedEffect(Unit) { runCatching { focus.getValue(layout.order.first()).first.requestFocus() } }
    fun update(l: HomeLayout) { layout = l; onSave(l) }

    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.widthIn(min = 720.design, max = 980.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design),
            verticalArrangement = Arrangement.spacedBy(12.design),
        ) {
            Text(D.homeRows, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(D.homeRowsHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.design))
            Column(Modifier.heightIn(max = 620.design).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.design)) {
                layout.order.forEachIndexed { i, b ->
                    val (main, up, down) = focus.getValue(b)
                    val visible = layout.isVisible(b)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                        FocusSurface(
                            onClick = { update(layout.toggle(b)) }, shape = RoundedCornerShape(16.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1f,
                            modifier = Modifier.weight(1f).height(68.designT).focusRequester(main),
                        ) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = if (f) Ux.OnFocus2 else Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.width(44.design))
                                Text(
                                    D.homeBlockName(b), color = if (f) Ux.TextOnLight else if (visible) Ux.Text else Ux.Text3, fontFamily = Manrope,
                                    fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                Text(if (visible) D.rowShown else D.rowHidden, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1)
                                Spacer(Modifier.width(14.design))
                                Switch(visible, inverted = f)
                            }
                        }
                        MoveButton(UP, D.moveUp, enabled = i > 0, modifier = Modifier.focusRequester(up)) { update(layout.move(b, -1)); refocus = b to 1 }
                        MoveButton(DOWN, D.moveDown, enabled = i < layout.order.lastIndex, modifier = Modifier.focusRequester(down)) { update(layout.move(b, 1)); refocus = b to 2 }
                    }
                }
            }
            Row(Modifier.padding(top = 8.design), horizontalArrangement = Arrangement.spacedBy(16.design)) {
                PillButton(D.doneLabel, onDismiss, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
                PillButton(D.resetDefault, { update(HomeLayout.DEFAULT) }, heightPx = 64, hPadPx = 30, fontPx = 22)
            }
        }
    }
}

/** Bouton ▲ / ▼ carré ; inactif en bout de liste (atténué, sans effet), mais focalisable pour garder une grille régulière. */
@Composable
private fun MoveButton(icon: String, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = { if (enabled) onClick() }, shape = RoundedCornerShape(16.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1f,
        modifier = modifier.size(68.designT).semantics { contentDescription = label },
    ) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DIcon(icon, 30.design, when { f -> Ux.TextOnLight; enabled -> Ux.Text; else -> Ux.Muted2 }, strokeWidth = 2.8f)
        }
    }
}

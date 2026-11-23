package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.homeBlockName
import com.ultratv.tv.nativeapp.i18n.rowHiddenToast
import com.ultratv.tv.nativeapp.i18n.rowHideHint
import com.ultratv.tv.nativeapp.i18n.rowHideTitle
import com.ultratv.tv.nativeapp.i18n.rowMenuDown
import com.ultratv.tv.nativeapp.i18n.rowMenuHide
import com.ultratv.tv.nativeapp.i18n.rowMenuSettings
import com.ultratv.tv.nativeapp.i18n.rowMenuUp
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx

/** Une entrée du menu d'une rangée ; [enabled] faux = grisée, non focalisable. */
internal class RowMenuEntry(val label: String, val enabled: Boolean, val onClick: () -> Unit)

/**
 * Menu d'une rangée de l'accueil (appui long sur OK ou touche Menu ; au toucher, appui long sur le titre) :
 * « Monter la rangée », « Descendre la rangée » (grisées si impossible), « Masquer la rangée » (confirmation légère et
 * indication pour la réafficher) et « Réglages des rangées… » (la fenêtre de Réglages › Affichage › Rangées de l'accueil).
 * Tout passe par la MÊME préférence (HomeLayout, [onSave]). [onDone] reçoit le bloc à refocaliser (null = aucun).
 */
@Composable
internal fun HomeRowMenuHost(
    block: HomeBlock,
    layout: HomeLayout,
    shown: List<HomeBlock>,
    onSave: (HomeLayout) -> Unit,
    onDone: (refocus: HomeBlock?) -> Unit,
) {
    val D = LocalDs.current
    val S = LocalStrings.current
    var step by remember(block) { mutableStateOf(0) }   // 0 = menu, 1 = confirmation de masquage, 2 = réglages des rangées
    val name = D.homeBlockName(block)
    when (step) {
        0 -> RowMenuDialog(
            title = name, hint = null,
            entries = listOf(
                RowMenuEntry(D.rowMenuUp, layout.canMoveAmong(block, -1, shown)) { onSave(layout.moveAmong(block, -1, shown)); onDone(block) },
                RowMenuEntry(D.rowMenuDown, layout.canMoveAmong(block, 1, shown)) { onSave(layout.moveAmong(block, 1, shown)); onDone(block) },
                RowMenuEntry(D.rowMenuHide, true) { step = 1 },
                RowMenuEntry(D.rowMenuSettings, true) { step = 2 },
            ),
            onDismiss = { onDone(block) },
        )
        1 -> RowMenuDialog(
            title = D.rowHideTitle(name), hint = D.rowHideHint,
            entries = listOf(
                RowMenuEntry(D.rowMenuHide, true) {
                    onSave(layout.hide(block))
                    com.ultratv.tv.nativeapp.ui.common.Toaster.show(D.rowHiddenToast)
                    onDone(null)
                },
                RowMenuEntry(S.cancel, true) { onDone(block) },
            ),
            onDismiss = { onDone(block) },
        )
        else -> com.ultratv.tv.nativeapp.ui.settings.HomeRowsDialog(layout, onSave = onSave, onDismiss = { onDone(block) })
    }
}

internal const val ROW_MENU_TAG = "home-row-menu"

@Composable
private fun RowMenuDialog(title: String, hint: String?, entries: List<RowMenuEntry>, onDismiss: () -> Unit) {
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.widthIn(min = 560.design, max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(36.design).testTag(ROW_MENU_TAG),
            verticalArrangement = Arrangement.spacedBy(10.design),
        ) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, color = Ux.Text3, fontFamily = Manrope, fontSize = 21.spx, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.design))
            entries.forEach { e ->
                if (e.enabled) FocusSurface(onClick = e.onClick, shape = RoundedCornerShape(16.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(64.designT)) { f ->
                    Box(Modifier.fillMaxWidth().padding(horizontal = 24.design).align(Alignment.CenterStart)) {
                        Text(e.label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 23.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else Box(Modifier.fillMaxWidth().height(64.designT).clip(RoundedCornerShape(16.design)).background(Ux.Surface.copy(alpha = 0.4f)).padding(horizontal = 24.design), contentAlignment = Alignment.CenterStart) {
                    // Grisée : impossible ici (première / dernière rangée affichée), jamais focalisable.
                    Text(e.label, color = Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 23.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

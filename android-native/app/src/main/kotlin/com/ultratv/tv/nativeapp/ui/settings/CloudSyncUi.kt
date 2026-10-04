package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.config.CloudDevice
import com.ultratv.tv.nativeapp.data.config.CloudSyncLogic
import com.ultratv.tv.nativeapp.data.config.PendingRemoval
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.responsiveWidth
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

@Composable
fun rememberCloudStrings(): CloudSyncStrings { val d = LocalDs.current; return remember(d.lang) { CloudSyncStrings(d.lang) } }

/**
 * Choix des appareils qui reçoivent une source : « Tous les appareils » ou des cases à cocher. Fonctionne au D-pad
 * (TV) comme au doigt ; l'appareil courant est toujours inclus (le Worker l'ajoute).
 * [initial] : null = tous ; sinon les identifiants cochés.
 */
@Composable
fun ShareDevicesDialog(devices: List<CloudDevice>, selfId: String?, initial: List<String>?, onConfirm: (List<String>?) -> Unit, onDismiss: () -> Unit) {
    val C = rememberCloudStrings()
    var all by remember { mutableStateOf(initial == null) }
    var picked by remember { mutableStateOf((initial ?: emptyList()).toSet() + listOfNotNull(selfId)) }
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.responsiveWidthDialog().clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(32.design),
            verticalArrangement = Arrangement.spacedBy(12.design),
        ) {
            Text(C.shareTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.design)) {
                item { CheckRow(C.allDevices, all) { all = !all } }
                items(devices, key = { it.id }) { d ->
                    val self = d.id == selfId
                    CheckRow(CloudSyncLogic.deviceDisplayName(d) + if (self) " (${C.thisOne})" else "", all || d.id in picked, enabled = !all && !self) {
                        picked = if (d.id in picked) picked - d.id else picked + d.id
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.design), modifier = Modifier.padding(top = 8.design)) {
                PillButton(C.confirm, { onConfirm(if (all) null else picked.toList()) }, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
                PillButton(com.ultratv.tv.nativeapp.i18n.LocalStrings.current.cancel, onDismiss, heightPx = 64, hPadPx = 30, fontPx = 22)
            }
        }
    }
}

@Composable
private fun Modifier.responsiveWidthDialog(): Modifier = this.responsiveWidth(760)


@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FocusSurface(onClick = { if (enabled) onClick() }, shape = RoundedCornerShape(16.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(68.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 24.design), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (f) Ux.TextOnLight else if (enabled) Ux.Text else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.width(32.design).height(32.design).clip(RoundedCornerShape(8.design)).background(if (checked) Ux.Accent else Ux.Surface2), contentAlignment = Alignment.Center) {
                if (checked) DIcon(Icons.Check, 22.design, Ux.White, strokeWidth = 3f)
            }
        }
    }
}

/** Retraits en attente : des favoris ou enregistrements dépendent de la source. */
@Composable
fun RemovalDialog(pending: List<PendingRemoval>, onRemove: (Set<Long>) -> Unit, onKeep: (Set<Long>) -> Unit) {
    val C = rememberCloudStrings()
    val ids = pending.map { it.localId }.toSet()
    ModalFocusScope(modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.responsiveWidthDialog().clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(32.design), verticalArrangement = Arrangement.spacedBy(16.design)) {
            Text(C.removalTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 2)
            pending.forEach { Text(C.removalBody(it.name, it.dependents), color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                PillButton(C.keepLocal, { onKeep(ids) }, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
                PillButton(C.removeAnyway, { onRemove(ids) }, heightPx = 64, hPadPx = 30, fontPx = 22)
            }
        }
    }
}

/** Proposition après l'ajout d'une source, sur un appareil appairé : jamais envoyée sans action explicite. */
@Composable
fun OfferShareDialog(onShare: () -> Unit, onLater: () -> Unit) {
    val C = rememberCloudStrings()
    ModalFocusScope(onBack = onLater, modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.responsiveWidthDialog().clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(32.design), verticalArrangement = Arrangement.spacedBy(16.design)) {
            Text(C.offerTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 3)
            Text(C.offerBody, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx)
            Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                PillButton(C.shareSource, onShare, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
                PillButton(C.notNow, onLater, heightPx = 64, hPadPx = 30, fontPx = 22)
            }
        }
    }
}

/** Renommage de cet appareil (formulaire commun : boutons épinglés, clavier système). */
@Composable
fun RenameDeviceDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val C = rememberCloudStrings()
    var name by remember { mutableStateOf(current) }
    AddProviderDialog(
        title = C.thisDevice, subtitle = C.deviceNameHint, icon = Icons.Monitor, onDismiss = onDismiss,
        onSubmit = { onSave(name.trim()) }, canSubmit = name.isNotBlank(), submitLabel = C.rename,
    ) { FormField(C.thisDevice, name, { name = it.take(40) }, autoFocus = true, last = true, required = true, testTag = "field-devname") }
}

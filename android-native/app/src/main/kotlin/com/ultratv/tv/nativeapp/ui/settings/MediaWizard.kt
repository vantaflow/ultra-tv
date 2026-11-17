package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.data.discovery.MediaDiscovery
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic
import com.ultratv.tv.nativeapp.data.mediaserver.Plex
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.mlibConnLocal
import com.ultratv.tv.nativeapp.i18n.mlibConnRelay
import com.ultratv.tv.nativeapp.i18n.mlibConnRemote
import com.ultratv.tv.nativeapp.i18n.mlibEnterAddress
import com.ultratv.tv.nativeapp.i18n.mlibFindLead
import com.ultratv.tv.nativeapp.i18n.mlibFindTitle
import com.ultratv.tv.nativeapp.i18n.mlibLibsAll
import com.ultratv.tv.nativeapp.i18n.mlibLibsImport
import com.ultratv.tv.nativeapp.i18n.mlibLibsLead
import com.ultratv.tv.nativeapp.i18n.mlibLibsMovies
import com.ultratv.tv.nativeapp.i18n.mlibLibsSeries
import com.ultratv.tv.nativeapp.i18n.mlibLibsTitle
import com.ultratv.tv.nativeapp.i18n.mlibLibsUnknown
import com.ultratv.tv.nativeapp.i18n.mlibNasBtn
import com.ultratv.tv.nativeapp.i18n.mlibNone
import com.ultratv.tv.nativeapp.i18n.mlibOwner
import com.ultratv.tv.nativeapp.i18n.mlibPickKind
import com.ultratv.tv.nativeapp.i18n.mlibPlexBtn
import com.ultratv.tv.nativeapp.i18n.mlibConnHint
import com.ultratv.tv.nativeapp.i18n.mlibSearchAgain
import com.ultratv.tv.nativeapp.i18n.mlibSearching
import com.ultratv.tv.nativeapp.i18n.mlibShared
import com.ultratv.tv.nativeapp.i18n.mlibSrvLead
import com.ultratv.tv.nativeapp.i18n.mlibSrvTitle
import com.ultratv.tv.nativeapp.i18n.mlibStep1
import com.ultratv.tv.nativeapp.i18n.mlibStep2
import com.ultratv.tv.nativeapp.i18n.mlibStep3
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

/*
 * Assistant « Ma bibliothèque » (maquette WizardB) : 1 Trouver le serveur → 2 Se connecter → 3 Choisir les bibliothèques.
 * L'étape 1 est l'écran [FindServerDialog] ; l'étape 2 réutilise les dialogues existants (MediaServerDialog, PlexDialog, NasDialog)
 * habillés du bandeau [WizardSteps] ; l'étape 3 est [LibraryPickDialog]. TV : cartes au D-pad ; téléphone : colonne empilée.
 */

/** Bandeau des trois étapes : pastille numérotée (pleine sur l'étape courante, coche sur les précédentes) et libellé. */
@Composable
fun WizardSteps(step: Int) {
    val D = LocalDs.current
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val labels = listOf(D.mlibStep1, D.mlibStep2, D.mlibStep3)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 16.design), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val n = i + 1
            val on = n == step
            val done = n < step
            val dot = if (touch) 26.dp else 48.design
            Box(
                Modifier.size(dot).clip(RoundedCornerShape(50)).background(if (on || done) Ux.Accent else Ux.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (done) DIcon(Icons.Check, if (touch) 14.dp else 24.design, Ux.White, strokeWidth = 3f)
                else Text(n.toString(), fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 13.sp else 22.spx, color = if (on) Ux.TextOnLight else Ux.Text3)
            }
            if (!touch || on) Text(label, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 13.sp else 22.spx, color = if (on) Ux.Text else Ux.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = if (touch) Modifier.weight(1f, fill = false) else Modifier)
            if (i < 2) Box(Modifier.weight(1f).height(if (touch) 2.dp else 3.design).clip(RoundedCornerShape(3.dp)).background(Ux.Line))
        }
    }
}

/** Pastille de logo coloré d'un type de source (J / E / P / N). */
@Composable
fun MediaLogo(kind: String, size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    val (mark, argb) = MediaLibraryLogic.logo(kind)
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.5f)).background(Color(argb)), contentAlignment = Alignment.Center) {
        Text(mark, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = Color.White)
    }
}

private fun kindLabel(k: MediaDiscovery.Kind) = when (k) { MediaDiscovery.Kind.JELLYFIN -> "Jellyfin"; MediaDiscovery.Kind.EMBY -> "Emby"; MediaDiscovery.Kind.PLEX -> "Plex" }

/**
 * Étape 1 : serveurs détectés sur le réseau (grandes cartes : logo, type, nom, adresse), recherche relançable, puis les trois
 * accès manuels (adresse, partage NAS, compte Plex). [found] s'enrichit pendant la recherche ([searching]).
 */
@Composable
fun FindServerDialog(
    found: List<MediaDiscovery.Found>,
    searching: Boolean,
    onSearch: () -> Unit,
    onPick: (MediaDiscovery.Found) -> Unit,
    onManual: () -> Unit,
    onNas: () -> Unit,
    onPlex: () -> Unit,
    onDismiss: () -> Unit,
) {
    val D = LocalDs.current
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier
                .then(if (touch) Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).heightIn(max = 640.dp) else Modifier.width(1500.design).heightIn(max = 900.design))
                .clip(RoundedCornerShape(if (touch) 24.dp else 32.design))
                .background(Ux.SurfaceDeep)
                .padding(if (touch) 20.dp else 56.design),
            verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 28.design),
        ) {
            WizardSteps(1)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 24.design)) {
                Text(D.mlibFindTitle, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 22.sp else 46.spx, color = Ux.Text, maxLines = 2)
                Text(D.mlibFindLead, fontFamily = Manrope, fontSize = if (touch) 13.sp else 24.spx, color = Ux.Text3, maxLines = 3)
                if (found.isEmpty() && !searching) Text(D.mlibNone, fontFamily = Manrope, fontSize = if (touch) 14.sp else 24.spx, color = Ux.Text2)
                // Cartes : 3 par ligne sur TV, 1 par ligne sur téléphone.
                found.chunked(if (touch) 1 else 3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 24.design)) {
                        row.forEach { f -> ServerCard(f, touch, Modifier.weight(1f)) { onPick(f) } }
                        repeat((if (touch) 1 else 3) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 16.design)) {
                    Box(Modifier.size(if (touch) 10.dp else 14.design).clip(RoundedCornerShape(50)).background(if (searching) Ux.Accent else Ux.Muted))
                    if (searching) Text(D.mlibSearching, fontFamily = Manrope, fontSize = if (touch) 13.sp else 21.spx, color = Ux.Text3, maxLines = 1)
                    else ActionPill(D.mlibSearchAgain, touch, onSearch)
                }
            }
            val actions: @Composable () -> Unit = {
                ActionPill(D.mlibEnterAddress, touch, onManual)
                ActionPill(D.mlibNasBtn, touch, onNas)
                ActionPill(D.mlibPlexBtn, touch, onPlex)
            }
            if (touch) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() } else Row(horizontalArrangement = Arrangement.spacedBy(16.design)) { actions() }
        }
    }
}

@Composable
private fun ServerCard(f: MediaDiscovery.Found, touch: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val D = LocalDs.current
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(if (touch) 18.dp else 28.design), bg = Ux.Surface, modifier = modifier.testTag("server-card")) { focused ->
        val ink = if (focused) Ux.TextOnLight else Ux.Text
        Column(Modifier.fillMaxWidth().padding(if (touch) 14.dp else 30.design), verticalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 14.design)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MediaLogo(f.kind.name, if (touch) 40.dp else 64.design, if (touch) 18.sp else 26.spx)
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (focused) Color(0xFFD5DCE8) else Ux.Surface2).padding(horizontal = if (touch) 10.dp else 14.design, vertical = if (touch) 4.dp else 6.design)) {
                    Text(kindLabel(f.kind), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 12.sp else 17.spx, color = if (focused) Ux.TextOnLight else Color(0xFFD5DCE8), maxLines = 1)
                }
            }
            Text(f.name, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 17.sp else 34.spx, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.address.substringAfter("://"), fontFamily = Manrope, fontSize = if (touch) 12.sp else 20.spx, color = ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(D.mlibLibsUnknown, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 12.sp else 20.spx, color = ink.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionPill(label: String, touch: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(if (touch) 14.dp else 18.design), bg = Ux.Surface2, modifier = if (touch) Modifier.fillMaxWidth() else Modifier) { focused ->
        Box(Modifier.padding(horizontal = if (touch) 16.dp else 30.design, vertical = if (touch) 10.dp else 20.design), contentAlignment = Alignment.Center) {
            Text(label, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 14.sp else 22.spx, color = if (focused) Ux.TextOnLight else Ux.Text, maxLines = 1)
        }
    }
}

/**
 * Étape 2 (Plex) : choix du serveur du compte avec la nature de la connexion retenue (Locale / Distante / Relais) ; la meilleure
 * connexion est essayée à l'ajout (voir [com.ultratv.tv.nativeapp.data.repo.ProviderRepository.addPlexServer]).
 */
@Composable
fun PlexServerPickDialog(servers: List<Plex.Server>, onPick: (Plex.Server) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier
                .then(if (touch) Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).heightIn(max = 640.dp) else Modifier.width(1000.design).heightIn(max = 900.design))
                .clip(RoundedCornerShape(if (touch) 24.dp else 32.design))
                .background(Ux.SurfaceDeep)
                .padding(if (touch) 20.dp else 48.design),
            verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 18.design),
        ) {
            WizardSteps(2)
            Text(D.mlibSrvTitle, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 20.sp else 36.spx, color = Ux.Text, maxLines = 1)
            Text(D.mlibSrvLead, fontFamily = Manrope, fontSize = if (touch) 13.sp else 21.spx, color = Ux.Text3, maxLines = 2)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design)) {
                servers.forEach { s ->
                    val kind = MediaLibraryLogic.bestConnKind(s)
                    FocusSurface(onClick = { onPick(s) }, shape = RoundedCornerShape(if (touch) 16.dp else 20.design), bg = Ux.Surface, modifier = Modifier.fillMaxWidth().testTag("plex-server")) { focused ->
                        Row(Modifier.fillMaxWidth().padding(if (touch) 14.dp else 24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 18.design)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(s.name, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 16.sp else 26.spx, color = if (focused) Ux.TextOnLight else Ux.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (s.owned) D.mlibOwner else D.mlibShared, fontFamily = Manrope, fontSize = if (touch) 12.sp else 18.spx, color = (if (focused) Ux.TextOnLight else Ux.Text).copy(alpha = 0.75f), maxLines = 1)
                            }
                            if (kind != null) {
                                val (label, chip) = when (kind) {
                                    MediaLibraryLogic.ConnKind.LOCAL -> D.mlibConnLocal to Ux.Accent
                                    MediaLibraryLogic.ConnKind.REMOTE -> D.mlibConnRemote to Color(0xFF7FA8FF)
                                    MediaLibraryLogic.ConnKind.RELAY -> D.mlibConnRelay to Color(0xFFA9B4C8)
                                }
                                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(chip).padding(horizontal = if (touch) 10.dp else 14.design, vertical = if (touch) 4.dp else 6.design)) {
                                    Text(label, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 12.sp else 17.spx, color = Color(0xFF0A0F1E), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Text(D.mlibConnHint, fontFamily = Manrope, fontSize = if (touch) 12.sp else 18.spx, color = Ux.Text3, maxLines = 3)
        }
    }
}

/**
 * Étape 3 : cases des bibliothèques à importer (toutes cochées au départ), films puis séries. « Importer » exige au moins une case ;
 * fermer sans valider importe tout (voir SettingsViewModel.skipLibraries).
 */
@Composable
fun LibraryPickDialog(libraries: List<MediaLibraryLogic.LibraryChoice>, onConfirm: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    var checked by remember(libraries) { mutableStateOf(MediaLibraryLogic.allChecked(libraries)) }
    AddProviderDialog(
        title = D.mlibLibsTitle,
        subtitle = D.mlibLibsLead,
        icon = Icons.Monitor,
        onDismiss = onDismiss,
        onSubmit = { onConfirm(checked) },
        canSubmit = MediaLibraryLogic.canImport(checked),
        submitLabel = D.mlibLibsImport,
        step = 3,
    ) {
        val all = MediaLibraryLogic.allChecked(libraries)
        LibraryRow(D.mlibLibsAll, checked.size == all.size, touch) { checked = if (checked.size == all.size) emptySet() else all }
        for ((kind, title) in listOf("MOVIE" to D.mlibLibsMovies, "SERIES" to D.mlibLibsSeries)) {
            val group = libraries.filter { it.kind == kind }
            if (group.isEmpty()) continue
            Text(title, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 13.sp else 22.spx, color = Ux.Text3, maxLines = 1)
            group.forEach { l -> val key = MediaLibraryLogic.keyOf(l); LibraryRow(l.name, key in checked, touch) { checked = MediaLibraryLogic.toggle(checked, key) } }
        }
    }
}

@Composable
private fun LibraryRow(label: String, on: Boolean, touch: Boolean, onToggle: () -> Unit) {
    FocusSurface(onClick = onToggle, shape = RoundedCornerShape(if (touch) 14.dp else 18.design), bg = Ux.Surface, modifier = Modifier.fillMaxWidth().testTag("library-row")) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = if (touch) 14.dp else 24.design, vertical = if (touch) 10.dp else 16.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 20.design)) {
            Box(Modifier.size(if (touch) 24.dp else 36.design).clip(RoundedCornerShape(8.dp)).background(if (on) Ux.Accent else Ux.Surface2), contentAlignment = Alignment.Center) {
                if (on) DIcon(Icons.Check, if (touch) 16.dp else 24.design, Ux.White, strokeWidth = 3f)
            }
            Text(label, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = if (touch) 15.sp else 26.spx, color = if (focused) Ux.TextOnLight else Ux.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Choix du type de serveur pour « Saisir une adresse » : Jellyfin ou Emby. */
@Composable
fun MediaKindChoice(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(D.mlibPickKind, listOf("JELLYFIN" to "Jellyfin", "EMBY" to "Emby"), null, onPick = onPick, onDismiss = onDismiss)
}

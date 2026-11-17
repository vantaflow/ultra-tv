package com.ultratv.tv.nativeapp.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic
import com.ultratv.tv.nativeapp.data.mediaserver.MediaServer
import com.ultratv.tv.nativeapp.data.nas.Nas
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.mlibCounts
import com.ultratv.tv.nativeapp.i18n.mlibHubEmpty
import com.ultratv.tv.nativeapp.i18n.mlibHubTitle
import com.ultratv.tv.nativeapp.i18n.mlibNasAuto
import com.ultratv.tv.nativeapp.i18n.mlibNasFolders
import com.ultratv.tv.nativeapp.i18n.mlibStatusOffline
import com.ultratv.tv.nativeapp.i18n.mlibStatusOnline
import com.ultratv.tv.nativeapp.i18n.mlibStatusRelay
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.settings.CloudSyncStrings
import com.ultratv.tv.nativeapp.ui.settings.MediaLogo

/**
 * « Mes bibliothèques » (maquette HubB) : une section par serveur Jellyfin / Emby / Plex ou partage NAS — logo couleur, nom, état
 * (En ligne / Relais / Hors ligne · dernière copie), nombre de films et de séries — et une rangée d'affiches avec barre de
 * progression sur les titres en cours. Ouvrir un partage NAS montre sa vue dossiers + « Reconnu automatiquement ».
 * Téléphone : sections empilées, rangées défilantes.
 */
@Composable
fun LibraryHubScreen(onOpenMovie: (Long) -> Unit, onOpenSeries: (Long) -> Unit, vm: LibraryHubViewModel = hiltViewModel()) {
    val D = LocalDs.current
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val rows by vm.rows.collectAsState()
    var nasOpen by remember { mutableStateOf<LibraryRow?>(null) }
    BackHandler(enabled = nasOpen != null) { nasOpen = null }
    Column(
        Modifier.fillMaxSize().background(Ux.Bg).padding(if (touch) androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp) else androidx.compose.foundation.layout.PaddingValues(start = 72.design, end = 72.design, top = 48.design, bottom = 32.design)),
        verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 30.design),
    ) {
        Text(D.mlibHubTitle, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 26.sp else 50.spx, color = Ux.Text, maxLines = 1)
        val list = rows
        val open = nasOpen
        when {
            list == null -> Unit
            open != null -> NasDetailView(open, vm, touch)
            list.isEmpty() -> Text(D.mlibHubEmpty, fontFamily = Manrope, fontSize = if (touch) 14.sp else 26.spx, color = Ux.Text3)
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (touch) 18.dp else 30.design)) {
                items(list, key = { it.provider.id }) { r -> LibrarySection(r, touch, onOpenMovie, onOpenSeries) { nasOpen = r } }
            }
        }
    }
}

@Composable
private fun LibrarySection(r: LibraryRow, touch: Boolean, onOpenMovie: (Long) -> Unit, onOpenSeries: (Long) -> Unit, onOpenNas: () -> Unit) {
    val D = LocalDs.current
    val isNas = Nas.isNasKind(r.provider.kind)
    Column(verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design), modifier = Modifier.testTag("library-section")) {
        val header: @Composable (Boolean) -> Unit = { focused ->
            val ink = if (focused) Ux.TextOnLight else Ux.Text
            Row(
                Modifier.padding(horizontal = if (isNas) (if (touch) 8.dp else 12.design) else 0.dp, vertical = if (isNas) (if (touch) 4.dp else 6.design) else 0.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 16.design),
            ) {
                MediaLogo(r.provider.kind, if (touch) 32.dp else 46.design, if (touch) 14.sp else 20.spx)
                Text(r.provider.name, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 17.sp else 30.spx, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                StatusPill(r, touch)
                if (!touch) Text(D.mlibCounts(r.movies, r.series), fontFamily = Manrope, fontSize = 20.spx, color = if (focused) Ux.TextOnLight else Ux.Text3, maxLines = 1)
            }
        }
        // Seul un partage NAS s'ouvre (vue dossiers) : son en-tête est donc focalisable.
        if (isNas) FocusSurface(onClick = onOpenNas, shape = RoundedCornerShape(if (touch) 14.dp else 18.design), bg = Color.Transparent, focusedScale = 1.0f, ringWidth = 4.design, modifier = Modifier.fillMaxWidth().testTag("nas-open")) { f -> header(f) }
        else header(false)
        if (touch) Text(D.mlibCounts(r.movies, r.series), fontFamily = Manrope, fontSize = 12.sp, color = Ux.Text3, maxLines = 1)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 18.design)) {
            items(r.tiles, key = { "${it.isSeries}:${it.id}" }) { t -> PosterTile(t, touch) { if (t.isSeries) onOpenSeries(t.id) else onOpenMovie(t.id) } }
        }
    }
}

@Composable
private fun StatusPill(r: LibraryRow, touch: Boolean) {
    val D = LocalDs.current
    val ok = r.status != MediaLibraryLogic.Status.OFFLINE
    val label = when (r.status) {
        MediaLibraryLogic.Status.ONLINE -> D.mlibStatusOnline
        MediaLibraryLogic.Status.RELAY -> D.mlibStatusRelay
        MediaLibraryLogic.Status.OFFLINE -> {
            val minutes = if (r.lastCopyAt > 0) (System.currentTimeMillis() - r.lastCopyAt) / 60_000 else 0L
            D.mlibStatusOffline(CloudSyncStrings(D.lang).ago(minutes))
        }
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Ux.SurfaceDeep).padding(horizontal = if (touch) 10.dp else 14.design, vertical = if (touch) 3.dp else 6.design),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 8.design),
    ) {
        Box(Modifier.size(if (touch) 8.dp else 10.design).clip(RoundedCornerShape(50)).background(if (ok) Ux.Accent else Color(0xFFF5A524)))
        Text(label, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 11.sp else 17.spx, color = if (ok) Color(0xFF9BE7DF) else Color(0xFFF5C451), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterTile(t: MediaLibraryLogic.Tile, touch: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(if (touch) 12.dp else 20.design), bg = Ux.Surface, focusedScale = 1.04f, ringWidth = 5.design,
        modifier = Modifier.width(if (touch) 112.dp else 226.design).height(if (touch) 160.dp else 236.design).testTag("library-tile")) { _ ->
        Box(Modifier.fillMaxSize()) {
            PosterImage(t.poster, t.title, Modifier.fillMaxSize(), radius = if (touch) 12 else 20)
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(if (touch) 8.dp else 16.design),
                verticalArrangement = Arrangement.spacedBy(if (touch) 5.dp else 10.design),
            ) {
                Text(t.title, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 11.sp else 18.spx, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (t.progress > 0f) Box(Modifier.fillMaxWidth().height(if (touch) 3.dp else 5.design).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(t.progress).background(Ux.Accent))
                }
            }
        }
    }
}

/** Vue d'un partage NAS (maquette Library) : dossiers à gauche, « Reconnu automatiquement » (fichier → titre) à droite. */
@Composable
private fun NasDetailView(r: LibraryRow, vm: LibraryHubViewModel, touch: Boolean) {
    val D = LocalDs.current
    val detail by produceState<NasDetail?>(null, r.provider.id) { value = vm.nasDetail(r.provider.id) }
    val d = detail
    val panels: @Composable (Modifier, Modifier) -> Unit = { left, right ->
        Column(left.clip(RoundedCornerShape(if (touch) 18.dp else 28.design)).background(Ux.SurfaceDeep).padding(if (touch) 14.dp else 28.design), verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 12.design)) {
            Text(r.provider.name, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 18.sp else 28.spx, color = Ux.Text, maxLines = 1)
            Text(r.provider.baseUrl.substringBefore('?') + " · " + D.mlibCounts(r.movies, r.series), fontFamily = Manrope, fontSize = if (touch) 12.sp else 18.spx, color = Ux.Text3, maxLines = 2)
            Text(D.mlibNasFolders, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 13.sp else 20.spx, color = Ux.Text3, maxLines = 1)
            d?.folders?.forEach { (name, n) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (touch) 12.dp else 16.design)).background(Ux.Bg).padding(horizontal = if (touch) 12.dp else 18.design, vertical = if (touch) 10.dp else 14.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 14.design)) {
                    DIcon("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", if (touch) 20.dp else 24.design, Ux.Text2, strokeWidth = 2f)
                    Text(name, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 15.sp else 22.spx, color = Ux.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(n.toString(), fontFamily = Manrope, fontSize = if (touch) 13.sp else 18.spx, color = Ux.Text3, maxLines = 1)
                }
            }
        }
        // Panneau « Reconnu automatiquement » : seulement si des fichiers ont été reconnus.
        if (d != null && d.recognised.isNotEmpty()) Column(right.clip(RoundedCornerShape(if (touch) 18.dp else 28.design)).background(Ux.SurfaceDeep).padding(if (touch) 14.dp else 28.design), verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design)) {
            Text(D.mlibNasAuto, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 14.sp else 22.spx, color = Ux.Text3, maxLines = 1)
            d.recognised.forEach { (file, title) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (touch) 12.dp else 16.design)).background(Ux.Bg).padding(horizontal = if (touch) 12.dp else 18.design, vertical = if (touch) 8.dp else 14.design), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(file, fontFamily = Manrope, fontSize = if (touch) 11.sp else 17.spx, color = Ux.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→ $title", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 14.sp else 21.spx, color = Ux.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (touch) LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { panels(Modifier.fillMaxWidth(), Modifier.fillMaxWidth()) } } }
    else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.design)) { panels(Modifier.width(520.design).fillMaxHeight(), Modifier.weight(1f).fillMaxHeight()) }
}

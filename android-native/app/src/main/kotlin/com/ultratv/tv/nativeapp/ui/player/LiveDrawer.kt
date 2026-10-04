package com.ultratv.tv.nativeapp.ui.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*

/**
 * Guide dans le lecteur (maquette LecteurGuide) : à gauche, catégories + chaînes de la catégorie (numéro, logo, nom,
 * progression, programme, « EN COURS ») ; en bas à droite, le programme de la chaîne sélectionnée. La vidéo continue
 * derrière. Le lecteur reste sombre quel que soit le thème.
 */
@Composable
internal fun LiveDrawer(
    vm: PlayerViewModel,
    onPick: (com.ultratv.tv.nativeapp.data.db.ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries by vm.queue.collectAsState()
    val cats by vm.categories.collectAsState()
    val activeCat by vm.activeCategory.collectAsState()
    val D = LocalDs.current
    val current = entries.firstOrNull { it.isCurrent }
    val currentCat = activeCat ?: current?.channel?.categoryId
    var selected by remember { mutableStateOf<PlayerViewModel.DrawerEntry?>(null) }
    val shown = selected ?: current ?: entries.firstOrNull()
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size, current?.channel?.id) { current?.let { c -> entries.indexOf(c).takeIf { it >= 0 }?.let { listState.scrollToItem((it - 2).coerceAtLeast(0)) } } }
    BackHandler { onDismiss() }
    ModalDark {
        Row(Modifier.fillMaxSize()) {
            Row(
                Modifier.width(1100.design).fillMaxHeight().background(Color(0xF00A0A0C)).padding(start = 96.design, top = 54.design, end = 40.design, bottom = 54.design),
                horizontalArrangement = Arrangement.spacedBy(32.design),
            ) {
                Column(Modifier.width(300.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
                    Label(D.categoriesLabel, Modifier.padding(bottom = 8.design))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.design)) {
                        items(cats, key = { it.remoteId }) { c ->
                            val sel = c.remoteId == currentCat
                            FocusSurface(onClick = { vm.browse(c.remoteId) }, shape = RoundedCornerShape(14.design), bg = if (sel) Ux.Surface2 else Color.Transparent, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(56.design)) { f ->
                                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                    if (sel && !f) Box(Modifier.width(4.design).fillMaxHeight().background(Ux.Accent)) else Box(Modifier.width(4.design))
                                    Text(c.label, color = if (f) Ux.TextOnLight else if (sel) Ux.White else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.design))
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.design)) {
                    val title = cats.firstOrNull { it.remoteId == currentCat }?.label ?: D.channelsWord
                    Label("${title.uppercase()} · ${entries.size}", Modifier.padding(bottom = 8.design))
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.design)) {
                        items(entries, key = { it.channel.id }) { e ->
                            ChannelLine(e, index = entries.indexOf(e) + 1, D = D, onFocus = { selected = e }, onClick = { onPick(e.channel) })
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomStart) {
                Column(Modifier.fillMaxWidth().background(Color(0xE00A0A0C)).padding(start = 48.design, end = 96.design, top = 32.design, bottom = 54.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
                    Label(D.selectedChannel, color = Ux.Accent)
                    Text(shown?.now?.title ?: shown?.channel?.title.orEmpty(), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    shown?.now?.let { n ->
                        Text(
                            "${EpgClock.hm(n.startMs)} – ${EpgClock.hm(n.endMs)}" + (shown.next?.let { " · ${LocalStrings.current.liveThen} ${it.title}" } ?: ""),
                            color = Ux.Text2, fontFamily = Manrope, fontSize = 20.spx, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(D.drawerHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, modifier = Modifier.padding(top = 6.design))
                }
            }
        }
    }
}

@Composable
private fun ModalDark(content: @Composable () -> Unit) = com.ultratv.tv.nativeapp.ui.common.ModalFocusScope(onBack = null, modifier = Modifier.background(Color(0x66000000))) { Box(Modifier.fillMaxSize()) { content() } }

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, color: Color = Ux.Text3) =
    Text(text, color = color, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.spx, letterSpacing = TextUnit(1.8f, TextUnitType.Sp), maxLines = 1, modifier = modifier)

@Composable
private fun ChannelLine(e: PlayerViewModel.DrawerEntry, index: Int, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, onFocus: () -> Unit, onClick: () -> Unit) {
    val now = e.now
    val fraction = if (now != null && now.endMs > now.startMs) ((System.currentTimeMillis() - now.startMs).toFloat() / (now.endMs - now.startMs)).coerceIn(0f, 1f) else 0f
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(96.design).onFocusChanged { if (it.isFocused) onFocus() }) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 18.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            Text("$index", color = if (f) Ux.OnFocus2 else Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, modifier = Modifier.width(48.design))
            LogoBox(e.channel.logo, e.channel.title, Modifier.width(64.design).height(42.design), radius = 8, pad = 4, bg = if (f) Ux.Surface else Ux.Surface2)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(e.channel.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressLine(fraction, Modifier.fillMaxWidth().clip(RoundedCornerShape(2.design)), heightPx = 4, track = if (f) Ux.OnFocus2.copy(alpha = 0.4f) else Ux.Surface2)
                Text(now?.title.orEmpty(), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (e.isCurrent) Text(D.onAirPill, color = Ux.Accent, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.spx, letterSpacing = TextUnit(1.3f, TextUnitType.Sp), maxLines = 1)
        }
    }
}

package com.ultratv.tv.nativeapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.prettyCategoryName
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.LiveBadge
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * Direct (maquette Direct.dc.html) : catégories avec compteurs · liste des chaînes · aperçu.
 * L'aperçu n'ouvre AUCUN flux (une seule connexion autorisée par la plupart des fournisseurs) :
 * logo + programme en cours (guide). OK ouvre le lecteur plein écran — l'aperçu « devient » le lecteur.
 */
@Composable
fun LiveScreen(onPlay: (url: String, title: String) -> Unit, vm: LiveViewModel = hiltViewModel()) {
    val D = LocalDs.current
    val cats by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val locked by vm.lockedChannels.collectAsState()
    val nowNext by vm.nowNext.collectAsState()
    val favs by vm.favoriteIds.collectAsState()
    val channels = vm.channels.collectAsLazyPagingItems()
    var pinPrompt by remember { mutableStateOf<ChannelEntity?>(null) }
    var actionsFor by remember { mutableStateOf<ChannelEntity?>(null) }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    val current = cats.firstOrNull { it.id == selected }

    Row(Modifier.fillMaxSize()) {
        // ── Catégories (340) ──
        Column(
            Modifier.width(340.design).fillMaxHeight().padding(start = 48.design, end = 24.design, top = 54.design),
            verticalArrangement = Arrangement.spacedBy(10.design),
        ) {
            Text(D.directTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, modifier = Modifier.padding(bottom = 10.design), maxLines = 1)
            val focus = LocalFocusManager.current
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(bottom = 54.design)) {
                items(cats, key = { it.id }, contentType = { "cat" }) { c ->
                    CategoryRow(
                        label = when (c.id) { CATEGORY_FAVORITES -> D.catFavorites; CATEGORY_ALL -> D.catAll; else -> prettyCategoryName(c.name.orEmpty()) },
                        count = c.count, selected = c.id == selected, locked = c.locked,
                        onFocus = { vm.selectCategory(c.id) },
                        onClick = { vm.selectCategory(c.id); focus.moveFocus(FocusDirection.Right) },
                    )
                }
            }
        }
        Box(Modifier.width(0.5f.dp1()).fillMaxHeight().background(Ux.Surface))

        // ── Chaînes (640) ──
        Column(Modifier.width(640.design).fillMaxHeight().padding(horizontal = 32.design).padding(top = 54.design), verticalArrangement = Arrangement.spacedBy(12.design)) {
            val name = when (selected) { CATEGORY_FAVORITES -> D.catFavorites; CATEGORY_ALL -> D.catAll; else -> prettyCategoryName(current?.name.orEmpty()) }
            Text(
                D.categoryHeader.format(name, java.text.NumberFormat.getIntegerInstance().format(current?.count ?: 0)),
                color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.design),
            )
            ChannelList(channels, locked, favs, nowNext, selected, vm,
                onFocusChannel = { focusedChannel = it },
                onPlay = { c -> if ("${c.providerId}:${c.remoteId}" in locked) pinPrompt = c else vm.resolveAndPlay(c, onPlay) },
                onActions = { actionsFor = it },
                emptyText = if (selected == CATEGORY_FAVORITES) D.noFavorites else D.noChannels)
        }

        // ── Aperçu ──
        Preview(
            channel = focusedChannel ?: channels.itemSnapshotList.firstOrNull(),
            now = (focusedChannel ?: channels.itemSnapshotList.firstOrNull())?.let { nowNext[it.id]?.first },
            next = (focusedChannel ?: channels.itemSnapshotList.firstOrNull())?.let { nowNext[it.id]?.second },
            D = D, modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }

    pinPrompt?.let { ch ->
        com.ultratv.tv.nativeapp.ui.parental.PinPromptDialog(
            title = ch.title,
            onUnlocked = { pinPrompt = null; vm.resolveAndPlay(ch, onPlay) },
            onCancel = { pinPrompt = null },
        )
    }
    actionsFor?.let { ch ->
        val isFav = ch.remoteId in favs
        val isLocked = "${ch.providerId}:${ch.remoteId}" in locked
        ModalFocusScope(onBack = { actionsFor = null }, modifier = Modifier.background(Color(0xB80A0A0C))) {
            Column(Modifier.clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(48.design), verticalArrangement = Arrangement.spacedBy(16.design)) {
                Text(ch.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.design))
                PillButton(if (isFav) D.removeFavorite else D.addFavorite, onClick = { vm.toggleFavorite(ch); actionsFor = null }, bg = Ux.Surface2, modifier = Modifier.width(560.design))
                PillButton(if (isLocked) D.unlockChannel else D.lockChannel, onClick = { vm.toggleLock(ch); actionsFor = null }, bg = Ux.Surface2, modifier = Modifier.width(560.design))
                PillButton(D.close, onClick = { actionsFor = null }, bg = Ux.Surface, modifier = Modifier.width(560.design))
            }
        }
    }
}

private fun Float.dp1() = androidx.compose.ui.unit.Dp(this)

@Composable
private fun CategoryRow(label: String, count: Int, selected: Boolean, locked: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(16.design),
        bg = if (selected) Ux.Surface2 else Color.Transparent,
        ringWidth = 5.design,
        modifier = Modifier.fillMaxWidth().height(64.design).onFocusChanged { if (it.isFocused) onFocus() },
    ) { f ->
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (selected && !f) Box(Modifier.width(4.design).fillMaxHeight().background(Ux.Accent)) else Spacer(Modifier.width(4.design))
            Row(Modifier.weight(1f).padding(horizontal = 16.design), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (locked) "🔒 " else "") + label,
                    color = if (f) Ux.TextOnLight else if (selected) Ux.White else Ux.Text2,
                    fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.design))
                Text(java.text.NumberFormat.getIntegerInstance().format(count), color = if (f) Ux.Line else if (selected) Ux.Text else Color(0xFF71717A), fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: LazyPagingItems<ChannelEntity>,
    locked: Set<String>,
    favs: Set<String>,
    nowNext: Map<Long, Pair<EpgEntity?, EpgEntity?>>,
    selected: String,
    vm: LiveViewModel,
    onFocusChannel: (ChannelEntity) -> Unit,
    onPlay: (ChannelEntity) -> Unit,
    onActions: (ChannelEntity) -> Unit,
    emptyText: String,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.scrollToItem(0) }
    // Seules les lignes visibles interrogent le guide.
    LaunchedEffect(state, channels) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }.collect { idx ->
            vm.setVisible(idx.mapNotNull { channels.itemSnapshotList.getOrNull(it)?.id })
        }
    }
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    if (channels.itemCount > 0) RequestInitialFocus(first, hasFocus = { firstFocused }, key = channels.itemCount > 0)
    if (channels.itemCount == 0 && channels.loadState.refresh !is androidx.paging.LoadState.Loading) {
        Text(emptyText, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
        return
    }
    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(12.design), contentPadding = PaddingValues(bottom = 54.design, top = 6.design)) {
        items(count = channels.itemCount, key = channels.itemKey { it.id }, contentType = { "channel" }) { i ->
            val c = channels[i]
            if (c != null) {
                ChannelRow(
                    c, position = i + 1, locked = "${c.providerId}:${c.remoteId}" in locked, favorite = c.remoteId in favs,
                    now = nowNext[c.id]?.first,
                    modifier = if (i == 0) Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused } else Modifier,
                    onFocus = { onFocusChannel(c) }, onClick = { onPlay(c) }, onLongClick = { onActions(c) },
                )
            } else Spacer(Modifier.fillMaxWidth().height(88.design))
        }
    }
}

/** Ligne chaîne de 88 px : numéro, logo en boîte fixe 72×48 (Fit), nom (1 ligne) et programme en cours. */
@Composable
private fun ChannelRow(
    c: ChannelEntity, position: Int, locked: Boolean, favorite: Boolean, now: EpgEntity?,
    modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(18.design), bg = Ux.SurfaceDeep,
        focusedScale = 1.03f, ringWidth = 5.design,
        modifier = modifier.fillMaxWidth().height(88.design).onFocusChanged { if (it.isFocused) onFocus() },
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 20.design), verticalAlignment = Alignment.CenterVertically) {
            Text(position.toString(), color = if (f) Ux.TextOnLight else Color(0xFF71717A), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, softWrap = false, modifier = Modifier.width(56.design))
            Spacer(Modifier.width(20.design))
            LogoBox(c.logo, c.title, Modifier.width(72.design).height(48.design), radius = 10, pad = 5, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface2)
            Spacer(Modifier.width(20.design))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text((if (locked) "🔒 " else "") + c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now?.title.orEmpty(), color = if (f) Ux.Line else Ux.Text3, fontFamily = Manrope, fontSize = 19.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (favorite) Text("♥", color = Ux.Accent, fontSize = 22.spx, maxLines = 1)
        }
    }
}

// ───────────────────────── Aperçu (sans flux vidéo) ─────────────────────────

@Composable
private fun Preview(channel: ChannelEntity?, now: EpgEntity?, next: EpgEntity?, D: DesignStrings, modifier: Modifier) {
    Column(modifier.padding(start = 32.design, end = 96.design, top = 54.design, bottom = 54.design), verticalArrangement = Arrangement.spacedBy(28.design)) {
        if (channel == null) return@Column
        Box(Modifier.fillMaxWidth().height(450.design).clip(RoundedCornerShape(24.design)).background(Color(0xFF1F1F25)), contentAlignment = Alignment.Center) {
            LogoBox(channel.logo, channel.title, Modifier.fillMaxSize(), radius = 24, pad = 96, bg = Color(0xFF1F1F25))
        }
        if (now != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                LiveBadge(D.live)
                Text("${EpgClock.hm(now.startMs)} – ${EpgClock.hm(now.endMs)}", color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1)
            }
        }
        Text(now?.title ?: channel.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, lineHeight = 48.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (now != null) {
            val frac = ((System.currentTimeMillis() - now.startMs).toFloat() / (now.endMs - now.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.design).clip(RoundedCornerShape(4.design)).background(Ux.Surface2)) { ProgressLine(frac, Modifier.fillMaxWidth(), heightPx = 8) }
            now.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 35.spx, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(D.noProgramInfo, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
        }
        if (next != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.design), modifier = Modifier.padding(top = 8.design)) {
                Text(D.upNext, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, letterSpacing = 2.sp(), maxLines = 1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${EpgClock.hm(next.startMs)} · ${next.title}", color = Ux.Text, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.design))
                    Text(durationText(D, next.endMs - next.startMs), color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                }
            }
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

private fun durationText(D: DesignStrings, ms: Long): String {
    val m = (ms / 60_000).toInt()
    return if (m >= 60) D.hourShort.format(m / 60) + if (m % 60 != 0) " " + D.minShort.format(m % 60) else "" else D.minShort.format(m)
}

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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
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
    val langView by vm.langView.collectAsState()
    val langCounts by vm.langCounts.collectAsState()
    var langPanel by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf<ChannelEntity?>(null) }
    var actionsFor by remember { mutableStateOf<ChannelEntity?>(null) }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // Le focus initial (programmatique) ne doit PAS changer de catégorie : seule une action de la télécommande le fait.
    var userMoved by remember { mutableStateOf(false) }
    // L'aperçu suit la chaîne focalisée avec ~300 ms de recul ; dans les catégories : première chaîne de la catégorie.
    var previewChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    LaunchedEffect(focusedChannel) { kotlinx.coroutines.delay(300); previewChannel = focusedChannel }
    LaunchedEffect(selected) { focusedChannel = null; previewChannel = null }
    val firstChannel = remember { FocusRequester() }
    LaunchedEffect(selected, channels.itemCount > 0) {
        if (!userMoved && channels.itemCount > 0) { kotlinx.coroutines.delay(250); runCatching { firstChannel.requestFocus() } }
    }
    val current = cats.firstOrNull { it.id == selected }

    Row(Modifier.fillMaxSize()) {
        // ── Catégories (340) ──
        Column(
            Modifier.width(340.design).fillMaxHeight().padding(start = 48.design, end = 24.design, top = 54.design),
            verticalArrangement = Arrangement.spacedBy(10.design),
        ) {
            Text(D.directTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, modifier = Modifier.padding(bottom = 10.design), maxLines = 1)
            val markMoved = Modifier.onPreviewKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && (e.key == androidx.compose.ui.input.key.Key.DirectionUp || e.key == androidx.compose.ui.input.key.Key.DirectionDown)) userMoved = true; false }
            val focus = LocalFocusManager.current
            LazyColumn(modifier = markMoved, verticalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(bottom = 54.design)) {
                items(cats, key = { it.id }, contentType = { "cat" }) { c ->
                    CategoryRow(
                        label = when (c.id) { CATEGORY_FAVORITES -> D.catFavorites; CATEGORY_ALL -> D.catAll; else -> prettyCategoryName(c.name.orEmpty()) },
                        count = c.count, selected = c.id == selected, locked = c.locked,
                        onFocus = { if (userMoved) vm.selectCategory(c.id) },
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
                D.categoryHeader(name, current?.count ?: 0) + (current?.sections?.takeIf { it > 0 }?.let { " · " + D.sections(it) } ?: ""),
                color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.design),
            )
            com.ultratv.tv.nativeapp.ui.common.LangPill(langView, onClick = { langPanel = true }, modifier = Modifier.padding(bottom = 8.design))
            ChannelList(channels, locked, favs, nowNext, selected, vm,
                onFocusChannel = { focusedChannel = it },
                onPlay = { c -> if ("${c.providerId}:${c.remoteId}" in locked) pinPrompt = c else vm.resolveAndPlay(c, onPlay) },
                onActions = { actionsFor = it },
                emptyText = if (selected == CATEGORY_FAVORITES) D.noFavorites else D.noChannels, categoryName = name, first = firstChannel)
        }

        // ── Aperçu ──
        Preview(
            channel = previewChannel ?: channels.itemSnapshotList.firstOrNull { it?.isSeparator == false },
            now = (previewChannel ?: channels.itemSnapshotList.firstOrNull { it?.isSeparator == false })?.let { nowNext[it.id]?.first },
            next = (previewChannel ?: channels.itemSnapshotList.firstOrNull { it?.isSeparator == false })?.let { nowNext[it.id]?.second },
            D = D, modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }

    if (langPanel) com.ultratv.tv.nativeapp.ui.common.LangViewPanel(langCounts, langView, onToggle = { vm.toggleLang(it) }, onClear = { vm.clearLangView() }, onDismiss = { langPanel = false })
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
        ModalFocusScope(onBack = { actionsFor = null }, modifier = Modifier.background(Ux.Scrim)) {
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
                    style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                )
                Spacer(Modifier.width(8.design))
                Text(java.text.NumberFormat.getIntegerInstance().format(count), color = if (f) Ux.OnFocus2 else if (selected) Ux.Text else Ux.Muted, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
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
    categoryName: String = "",
    first: FocusRequester = remember { FocusRequester() },
) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.scrollToItem(0) }
    // Seules les lignes visibles interrogent le guide.
    LaunchedEffect(state, channels) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }.collect { idx ->
            vm.setVisible(idx.mapNotNull { channels.itemSnapshotList.getOrNull(it)?.takeIf { c -> !c.isSeparator }?.id })
        }
    }
    // Section courante : le dernier séparateur au-dessus (ou à) la première ligne visible (en-tête collant).
    val stickyLabel by remember(state, channels) {
        androidx.compose.runtime.derivedStateOf {
            var i = state.firstVisibleItemIndex
            var label: String? = null
            while (i >= 0 && label == null) { channels.itemSnapshotList.getOrNull(i)?.takeIf { it.isSeparator }?.let { label = it.title }; i-- }
            label?.takeIf { !sameLabel(it, categoryName) }
        }
    }
    var firstFocused by remember { mutableStateOf(false) }
    if (channels.itemCount == 0 && channels.loadState.refresh !is androidx.paging.LoadState.Loading) {
        Text(emptyText, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
        return
    }
    Box {
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(12.design), contentPadding = PaddingValues(bottom = 54.design, top = 6.design)) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }, contentType = { idx -> if (channels.peek(idx)?.isSeparator == true) "section" else "channel" }) { i ->
                val c = channels[i]
                when {
                    c == null -> Spacer(Modifier.fillMaxWidth().height(88.design))
                    // Séparateur : en-tête de section, NON focalisable (le D-pad le saute), non lisible.
                    c.isSeparator -> if (!sameLabel(c.title, categoryName)) SectionHeader(c.title)
                    else -> ChannelRow(
                        c, position = if (c.seq > 0) c.seq else i + 1, locked = "${c.providerId}:${c.remoteId}" in locked, favorite = c.remoteId in favs,
                        now = nowNext[c.id]?.first,
                        modifier = if (i == 0 || (i == 1 && channels.peek(0)?.isSeparator == true)) Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused } else Modifier,
                        onFocus = { onFocusChannel(c) }, onClick = { onPlay(c) }, onLongClick = { onActions(c) },
                    )
                }
            }
        }
        stickyLabel?.let { Box(Modifier.fillMaxWidth().background(Ux.Bg).padding(top = 6.design, bottom = 6.design)) { SectionHeader(it) } }
    }
}

private fun sameLabel(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

/** En-tête de section (séparateur du fournisseur) : barre accent 4×20, libellé majuscules 18 gras #A1A1AA, filet à droite. */
@Composable
private fun SectionHeader(label: String) {
    Row(Modifier.fillMaxWidth().height(44.design).padding(horizontal = 4.design), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.design).height(20.design).background(Ux.Accent))
        Spacer(Modifier.width(14.design))
        Text(label.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, letterSpacing = 2.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(16.design))
        Box(Modifier.fillMaxWidth().height(0.5f.dp1()).background(Ux.Surface2))
    }
}

/** Pastille de qualité : 4K accent, FHD blanc, HD gris clair, SD gris ; inversée sur la ligne focalisée. */
@Composable
internal fun QualityBadge(quality: Int, focused: Boolean) {
    val (text, bg, fg) = when (quality) {
        4 -> Triple("4K", Ux.Accent, Color.White)
        3 -> Triple("FHD", Color.White, Color(0xFF0A0A0C))
        2 -> Triple("HD", Color(0xFFD4D4D8), Color(0xFF0A0A0C))
        1 -> Triple("SD", Ux.Muted2, Color(0xFFE4E4E7))
        else -> return
    }
    val (b, f) = if (focused) Color(0xFF0A0A0C) to Color.White else bg to fg
    Text(text, color = f, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.design)).background(b).padding(horizontal = 8.design, vertical = 3.design))
}

/** Ligne chaîne de 88 px : numéro, logo en boîte fixe 72×48 (Fit), nom (1 ligne), programme en cours, qualité. */
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
            Text(position.toString(), color = if (f) Ux.TextOnLight else Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, softWrap = false, modifier = Modifier.width(56.design))
            Spacer(Modifier.width(20.design))
            LogoBox(c.logo, c.title, Modifier.width(72.design).height(48.design), radius = 10, pad = 5, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface2)
            Spacer(Modifier.width(20.design))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text((if (locked) "🔒 " else "") + c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
                Text(now?.title.orEmpty(), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 19.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (favorite) { Text("♥", color = Ux.Accent, fontSize = 22.spx, maxLines = 1); Spacer(Modifier.width(10.design)) }
            com.ultratv.tv.nativeapp.ui.common.LangBadge(c.lang, f)
            Spacer(Modifier.width(8.design))
            QualityBadge(c.quality, f)
        }
    }
}

// ───────────────────────── Aperçu (sans flux vidéo) ─────────────────────────

@Composable
private fun Preview(channel: ChannelEntity?, now: EpgEntity?, next: EpgEntity?, D: DesignStrings, modifier: Modifier) {
    Column(modifier.padding(start = 32.design, end = 96.design, top = 54.design, bottom = 54.design), verticalArrangement = Arrangement.spacedBy(28.design)) {
        if (channel == null) return@Column
        Box(Modifier.fillMaxWidth().height(450.design).clip(RoundedCornerShape(24.design)).background(Ux.Tone), contentAlignment = Alignment.Center) {
            LogoBox(channel.logo, channel.title, Modifier.fillMaxSize(), radius = 24, pad = 96, bg = Ux.Tone)
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

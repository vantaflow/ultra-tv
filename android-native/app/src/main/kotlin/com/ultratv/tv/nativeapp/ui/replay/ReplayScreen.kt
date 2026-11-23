package com.ultratv.tv.nativeapp.ui.replay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.replay.ReplayDay
import com.ultratv.tv.nativeapp.data.replay.ReplayEmpty
import com.ultratv.tv.nativeapp.data.replay.ReplayRow
import com.ultratv.tv.nativeapp.data.replay.ReplayRubric
import com.ultratv.tv.nativeapp.data.replay.ReplayUrls
import com.ultratv.tv.nativeapp.data.repo.EpgRetention
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.DisplayLocale
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.replayAllChannels
import com.ultratv.tv.nativeapp.i18n.replayArchiveDays
import com.ultratv.tv.nativeapp.i18n.replayCategory
import com.ultratv.tv.nativeapp.i18n.replayEmptyBody
import com.ultratv.tv.nativeapp.i18n.replayEmptyTitle
import com.ultratv.tv.nativeapp.i18n.replayHiddenBody
import com.ultratv.tv.nativeapp.i18n.replayMinutes
import com.ultratv.tv.nativeapp.i18n.replayNav
import com.ultratv.tv.nativeapp.i18n.replayNoProgrammes
import com.ultratv.tv.nativeapp.i18n.replayNoSource
import com.ultratv.tv.nativeapp.i18n.replayOnNow
import com.ultratv.tv.nativeapp.i18n.replayPickChannel
import com.ultratv.tv.nativeapp.i18n.replayToday
import com.ultratv.tv.nativeapp.i18n.replayUnavailable
import com.ultratv.tv.nativeapp.i18n.replayYesterday
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.StateCard
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.parental.PinPromptDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Rubrique Replay : choix de la chaîne (chaînes à archive de la source active, filtrables par catégorie), du jour (aujourd'hui,
 * hier… jusqu'à la durée d'archive de la chaîne), puis d'un programme passé ; OK le lit depuis le début par le rattrapage
 * existant. Programme hors de la fenêtre d'archive : grisé « Indisponible ».
 *
 * TV : deux volets (chaînes | jours + programmes). Retour dans le volet de droite rend le focus à la chaîne choisie ; depuis la
 * liste des chaînes, Retour ouvre le menu latéral comme les autres rubriques. Téléphone / tablette : chaînes, puis page de la
 * chaîne (Retour revient aux chaînes).
 */
@Composable
fun ReplayScreen(
    onPlay: (url: String, title: String) -> Unit,
    onGoLive: () -> Unit,
    onGoSettings: () -> Unit,
    vm: ReplayHubViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val st by vm.state.collectAsStateWithLifecycle()
    val cs by vm.channel.collectAsStateWithLifecycle()
    val selectedId by vm.selectedChannelId.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val touch = LocalTouch.current
    val scope = rememberCoroutineScope()
    var pinFor by remember { mutableStateOf<ChannelEntity?>(null) }
    // Chaîne qui vient d'être choisie à la télécommande : le focus passe sur ses jours dès qu'ils sont chargés.
    var focusDaysFor by remember { mutableStateOf<Long?>(null) }

    val open: (ChannelEntity) -> Unit = { c ->
        scope.launch {
            if (vm.needsPin(c)) pinFor = c
            else { vm.selectChannel(c); if (!touch) focusDaysFor = c.id }
        }
    }
    val play: (ReplayRow) -> Unit = { r ->
        val c = cs.channel
        if (c != null) {
            if (r.playable) vm.play(c, r.prog, onPlay) else Toaster.show(D.replayUnavailable)
        }
    }

    Box(Modifier.fillMaxSize().background(Ux.Bg)) {
        when {
            st.loading -> Unit
            st.empty != null -> ReplayEmptyState(st.empty!!, D, touch, onGoLive, onGoSettings)
            touch -> ReplayTouchContent(st, cs, selectedId, locked, D, open, vm::selectCategory, vm::selectDay, play, vm::clearChannel)
            else -> ReplayTvContent(st, cs, selectedId, locked, D, focusDaysFor, { focusDaysFor = null }, open, vm::selectCategory, vm::selectDay, play)
        }
    }
    pinFor?.let { c ->
        PinPromptDialog(
            title = c.title,
            onUnlocked = { pinFor = null; vm.markUnlocked(c); vm.selectChannel(c); if (!touch) focusDaysFor = c.id },
            onCancel = { pinFor = null },
        )
    }
}

@Composable
private fun ReplayEmptyState(reason: ReplayEmpty, D: DesignStrings, touch: Boolean, onGoLive: () -> Unit, onGoSettings: () -> Unit) {
    val body = when (reason) {
        ReplayEmpty.NO_SOURCE -> D.replayNoSource
        ReplayEmpty.NO_ARCHIVE -> D.replayEmptyBody
        ReplayEmpty.ALL_HIDDEN -> D.replayHiddenBody
    }
    val (label, action) = if (reason == ReplayEmpty.NO_SOURCE) D.settingsTitle to onGoSettings else D.directTitle to onGoLive
    Box(Modifier.fillMaxSize().padding(if (touch) 16.dp else 64.design), contentAlignment = Alignment.Center) {
        StateCard(D.replayEmptyTitle, body, ReplayIcons.Replay, label, action, modifier = Modifier.widthIn(max = 900.design).testTag("replay-empty"))
    }
}

/** Libellé d'un jour : Aujourd'hui, Hier, puis « sam. 14 nov. » dans la langue de l'appli (chiffres latins). */
internal fun replayDayLabel(D: DesignStrings, day: ReplayDay): String = when (day.offset) {
    0 -> D.replayToday
    1 -> D.replayYesterday
    else -> {
        val loc = DisplayLocale.of(D.lang)
        SimpleDateFormat("EEE d MMM", loc).format(Date(day.startMs)).replaceFirstChar { it.titlecase(loc) }
    }
}

private fun channelKey(c: ChannelEntity) = "${c.providerId}:${c.remoteId}"

private fun archiveDaysOf(c: ChannelEntity) = EpgRetention.archiveDays(c.catchupDays)

private fun categoryOptions(D: DesignStrings, st: ReplayHubState): List<Pair<String, String>> =
    listOf(ReplayRubric.ALL to D.replayAllChannels) + st.categories.map { it.id to ((if (it.locked) "🔒 " else "") + it.name + "  (" + it.count + ")") }

// ───────────────────────── TV ─────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ReplayTvContent(
    st: ReplayHubState, cs: ReplayChannelState, selectedId: Long?, locked: Set<String>, D: DesignStrings,
    focusDaysFor: Long?, onDaysFocused: () -> Unit,
    onOpen: (ChannelEntity) -> Unit, onCategory: (String) -> Unit, onDay: (Int) -> Unit, onPlay: (ReplayRow) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val channelFocus = remember { HashMap<Long, FocusRequester>() }
    fun reqFor(id: Long) = channelFocus.getOrPut(id) { FocusRequester() }
    val dayFocus = remember { FocusRequester() }
    var rightFocused by remember { mutableStateOf(false) }
    var catOpen by rememberSaveable { mutableStateOf(false) }

    // Retour depuis les jours / programmes : focus rendu à la chaîne choisie (jamais de piège ; depuis la liste, le menu s'ouvre).
    BackHandler(enabled = rightFocused) {
        val id = selectedId ?: return@BackHandler
        val i = st.channels.indexOfFirst { it.id == id }
        scope.launch {
            if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == i }) listState.scrollToItem(i)
            withFrameNanos { }
            if (!runCatching { reqFor(id).requestFocus() }.getOrDefault(false)) runCatching { reqFor(st.channels.firstOrNull()?.id ?: return@launch).requestFocus() }
        }
    }
    // Chaîne choisie : le focus passe sur le jour sélectionné dès que ses jours sont affichés.
    LaunchedEffect(focusDaysFor, cs.channel?.id, cs.days.size) {
        if (focusDaysFor != null && cs.channel?.id == focusDaysFor && cs.days.isNotEmpty()) {
            withFrameNanos { }
            runCatching { dayFocus.requestFocus() }
            onDaysFocused()
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 48.design, end = 48.design, top = 40.design, bottom = 24.design)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(D.replayNav, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (st.categories.isNotEmpty()) {
                val name = if (st.category == ReplayRubric.ALL) D.replayAllChannels else st.categories.firstOrNull { it.id == st.category }?.name.orEmpty()
                FocusSurface(
                    onClick = { catOpen = true }, shape = RoundedCornerShape(50), bg = Ux.Button, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 0.dp,
                    modifier = Modifier.height(52.designT).testTag("replay-category"),
                ) { f ->
                    Row(Modifier.padding(horizontal = 22.design).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Text(D.replayCategory + " · " + name, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 520.design))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.design))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                // Ouverture de l'écran : focus sur la liste des chaînes (pas sur le bouton de catégorie en haut).
                modifier = Modifier.width(560.design).fillMaxHeight().focusRestorer().then(com.ultratv.tv.nativeapp.ui.common.screenEntryTarget()).focusGroup().testTag("replay-channels"),
                verticalArrangement = Arrangement.spacedBy(10.design),
                contentPadding = PaddingValues(vertical = 8.design),
            ) {
                itemsIndexed(st.channels, key = { _, c -> c.id }, contentType = { _, _ -> "ch" }) { _, c ->
                    ReplayChannelRow(c, channelKey(c) in locked, c.id == selectedId, Modifier.focusRequester(reqFor(c.id))) { onOpen(c) }
                }
            }
            Spacer(Modifier.width(32.design))
            Column(Modifier.weight(1f).fillMaxHeight().onFocusChanged { rightFocused = it.hasFocus }.focusGroup()) {
                val c = cs.channel
                if (c == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(D.replayPickChannel, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                        Text(c.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(D.replayArchiveDays(archiveDaysOf(c)), color = Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1)
                    }
                    Spacer(Modifier.height(14.design))
                    LazyRow(Modifier.fillMaxWidth().focusRestorer().focusGroup().testTag("replay-days"), horizontalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(vertical = 6.design)) {
                        itemsIndexed(cs.days, key = { _, d -> d.offset }) { _, d ->
                            val sel = d.offset == cs.day
                            ReplayDayChip(replayDayLabel(D, d), sel, if (sel) Modifier.focusRequester(dayFocus) else Modifier) { onDay(d.offset) }
                        }
                    }
                    Spacer(Modifier.height(14.design))
                    if (cs.rows.isEmpty()) {
                        Text(D.replayNoProgrammes, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, modifier = Modifier.padding(top = 24.design))
                    } else {
                        LazyColumn(Modifier.fillMaxSize().focusRestorer().focusGroup().testTag("replay-programmes"), verticalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(vertical = 8.design)) {
                            itemsIndexed(cs.rows, key = { _, r -> r.prog.startMs }, contentType = { _, _ -> "prog" }) { _, r ->
                                ReplayProgrammeRow(r, D) { onPlay(r) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (catOpen) ChoiceDialog(D.replayCategory, categoryOptions(D, st), st.category, onPick = { id -> catOpen = false; onCategory(id) }, onDismiss = { catOpen = false })
}

@Composable
private fun ReplayChannelRow(c: ChannelEntity, locked: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val D = LocalDs.current
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(18.design), bg = if (selected) Ux.NowTint else Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.02f, ringWidth = 0.dp,
        modifier = modifier.fillMaxWidth().height(84.designT),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
            LogoBox(c.logo, c.title, Modifier.size(56.design), radius = 12, pad = 4, bg = if (f) Color(0xFFE4E4E7) else Ux.Tile, decodeSize = DpSize(56.design, 56.design))
            Column(Modifier.weight(1f)) {
                val num = if (c.num > 0 && com.ultratv.tv.nativeapp.ui.common.LocalChannelDisplay.current.showNumbers) "${c.num} · " else ""
                Text((if (locked) "🔒 " else "") + num + c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 21.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(D.replayArchiveDays(archiveDaysOf(c)), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReplayDayChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(50), bg = if (selected) Ux.Accent else Ux.Button, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 0.dp,
        modifier = modifier.height(50.designT),
    ) { f ->
        Row(Modifier.padding(horizontal = 22.design).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = when { f -> Ux.TextOnLight; selected -> Ux.OnAccent; else -> Ux.Text }, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 19.spx, maxLines = 1)
        }
    }
}

@Composable
private fun ReplayProgrammeRow(r: ReplayRow, D: DesignStrings, onClick: () -> Unit) {
    val p = r.prog
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.01f, ringWidth = 0.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.designT),
    ) { f ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.design, vertical = 14.design).alpha(if (r.playable) 1f else 0.45f),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.design),
        ) {
            Text(EpgClock.hm(p.startMs), color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, modifier = Modifier.width(96.design))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(p.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
                    Text(EpgClock.range(p.startMs, p.endMs) + " · " + D.replayMinutes(ReplayUrls.durationMinutes(p)), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1)
                    val badge = when { !r.playable -> D.replayUnavailable; r.onNow -> D.replayOnNow; else -> null }
                    if (badge != null) Text(
                        badge, color = if (r.playable) Ux.OnAccent else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.spx, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (r.playable) Ux.Accent else Ux.Button).padding(horizontal = 10.design, vertical = 3.design),
                    )
                }
                val desc = p.description?.trim().orEmpty()
                if (desc.isNotEmpty()) Text(desc, color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontSize = 16.spx, lineHeight = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (r.playable) DIcon(Icons.Play, 26.design, if (f) Ux.TextOnLight else Ux.Text2)
        }
    }
}

// ───────────────────────── Téléphone / tablette ─────────────────────────

@Composable
private fun ReplayTouchContent(
    st: ReplayHubState, cs: ReplayChannelState, selectedId: Long?, locked: Set<String>, D: DesignStrings,
    onOpen: (ChannelEntity) -> Unit, onCategory: (String) -> Unit, onDay: (Int) -> Unit, onPlay: (ReplayRow) -> Unit, onClose: () -> Unit,
) {
    val c = cs.channel
    BackHandler(enabled = selectedId != null) { onClose() }
    if (selectedId != null && c != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    // Chevron « retour » : retourné en arabe (sens de lecture).
                    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                    Box(Modifier.size(24.dp).then(if (rtl) Modifier.graphicsLayer { scaleX = -1f } else Modifier)) { DIcon(Icons.Chevron, 24.dp, Ux.Text) }
                }
                Column(Modifier.weight(1f)) {
                    Text(c.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(D.replayArchiveDays(archiveDaysOf(c)), color = Ux.Text3, fontFamily = Manrope, fontSize = 13.sp, maxLines = 1)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(cs.days, key = { _, d -> d.offset }) { _, d -> TouchChip(replayDayLabel(D, d), d.offset == cs.day) { onDay(d.offset) } }
            }
            if (cs.rows.isEmpty()) Text(D.replayNoProgrammes, color = Ux.Text3, fontFamily = Manrope, fontSize = 15.sp, modifier = Modifier.padding(top = 24.dp))
            else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(cs.rows, key = { _, r -> r.prog.startMs }) { _, r -> TouchProgrammeRow(r, D) { onPlay(r) } }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(D.replayNav, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        if (st.categories.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(categoryOptions(D, st), key = { _, o -> o.first }) { _, o -> TouchChip(o.second, o.first == st.category) { onCategory(o.first) } }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(st.channels, key = { _, ch -> ch.id }) { _, ch ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).background(Ux.Card).clickable { onOpen(ch) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LogoBox(ch.logo, ch.title, Modifier.size(44.dp), radius = 10, pad = 3, bg = Ux.Tile, decodeSize = DpSize(44.dp, 44.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (channelKey(ch) in locked) "🔒 " else "") + ch.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(D.replayArchiveDays(archiveDaysOf(ch)), color = Ux.Text3, fontFamily = Manrope, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) Ux.Accent else Ux.Button).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Ux.OnAccent else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

@Composable
private fun TouchProgrammeRow(r: ReplayRow, D: DesignStrings, onClick: () -> Unit) {
    val p = r.prog
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ux.Card).clickable(onClick = onClick).padding(12.dp).alpha(if (r.playable) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(EpgClock.hm(p.startMs), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val badge = when { !r.playable -> D.replayUnavailable; r.onNow -> D.replayOnNow; else -> null }
            Text(D.replayMinutes(ReplayUrls.durationMinutes(p)) + (badge?.let { " · $it" } ?: ""), color = Ux.Text3, fontFamily = Manrope, fontSize = 12.sp, maxLines = 1)
            val desc = p.description?.trim().orEmpty()
            if (desc.isNotEmpty()) Text(desc, color = Ux.Text2, fontFamily = Manrope, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

package com.ultratv.tv.nativeapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.ui.common.prettyCategoryName
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import com.ultratv.tv.nativeapp.ui.components.UltraIcon
import com.ultratv.tv.nativeapp.ui.common.ChannelLogo

/**
 * Tivimate-inspired Live TV layout. Two stacked panes:
 *
 *   ┌──────────────────────┬──────────────────────────────────────────┐
 *   │  Categories          │  Channels in selected category           │
 *   │  • All channels      │  ┌─────┐ 001  TF1                         │
 *   │  • News              │  ┌─────┐ 002  France 2                    │
 *   │  • Sport             │  ┌─────┐ 003  M6                          │
 *   │  • Kids              │  …                                        │
 *   └──────────────────────┴──────────────────────────────────────────┘
 *
 * The right pane only renders channels for the selected category, so even on
 * a 50k-channel provider only ~hundreds are composed at any time — that's
 * the main lag fix.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun LiveScreen(onPlay: (url: String, title: String) -> Unit, vm: LiveViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsState()
    val chans by vm.channels.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val locked by vm.lockedChannels.collectAsState()
    val nowNext by vm.nowNext.collectAsState()
    // Channel awaiting PIN unlock; non-null while the dialog is up.
    var pinPrompt by remember { mutableStateOf<com.ultratv.tv.nativeapp.data.db.ChannelEntity?>(null) }
    // Currently focused channel for the preview pane (defaults to the first one).
    var activeIdx by remember(chans.size) { mutableStateOf(0) }
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current

    Row(Modifier.fillMaxSize().padding(top = 76.dp)) {
        // ---- Left pane: categories (200 dp — compact, focus-only) ----
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .clipToBounds()
                .padding(top = 20.dp, end = 0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                S.categories.uppercase(),
                color = UltraTokens.Fg3,
                fontSize = 11.sp,
                letterSpacing = 2.3.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 24.dp, bottom = 14.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item("__all__") {
                    CategoryRow(
                        label = "All channels",
                        selected = selected == CATEGORY_ALL,
                        onClick = { vm.selectCategory(CATEGORY_ALL) },
                    )
                }
                items(cats, key = { it.id }) { cat ->
                    CategoryRow(
                        label = prettyCategoryName(cat.name) + if (cat.locked) "  🔒" else "",
                        selected = selected == cat.remoteId,
                        onClick = { vm.selectCategory(cat.remoteId) },
                    )
                }
            }
        }

        // Thin vertical divider
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(UltraTokens.Line),
        )

        // ---- Middle pane: channels (420 dp — readable but compact) ----
        Column(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
                .clipToBounds()
                .padding(top = 20.dp, start = 0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val title = if (selected == CATEGORY_ALL) S.liveAllChannels
            else prettyCategoryName(cats.firstOrNull { it.remoteId == selected }?.name ?: "")
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title.uppercase(),
                    color = UltraTokens.Fg3,
                    fontSize = 11.sp,
                    letterSpacing = 2.3.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${chans.size} chaînes",
                    fontFamily = UltraFonts.Mono,
                    fontSize = 11.sp,
                    color = UltraTokens.Fg4,
                )
            }

            val listState = rememberLazyListState()
            // Reset scroll when the user switches category so they always see
            // the top of the new list, like Tivimate.
            LaunchedEffect(selected) { listState.scrollToItem(0) }

            if (chans.isEmpty()) {
                Text(
                    S.liveNoChannelsInCategory,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    itemsIndexed(chans, key = { _, c -> c.id }) { i, c ->
                        val isLocked = "${c.providerId}:${c.remoteId}" in locked
                        val nn = nowNext[c.id]
                        ChannelRow(
                            channel = c,
                            position = i + 1,
                            locked = isLocked,
                            active = i == activeIdx,
                            nowProgramme = nn?.first,
                            nextProgramme = nn?.second,
                            onFocus = { activeIdx = i },
                        ) {
                            activeIdx = i
                            if (isLocked) pinPrompt = c
                            else vm.resolveAndPlay(c, onPlay)
                        }
                    }
                }
            }
        }

        // Divider between channels and preview
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(UltraTokens.Line),
        )

        // ---- Right pane: live mini-player + now/next + Watch CTA ----
        val active = chans.getOrNull(activeIdx)
        if (active != null) {
            LivePreviewPane(
                channel = active,
                vm = vm,
                nowProgramme = nowNext[active.id]?.first,
                nextProgramme = nowNext[active.id]?.second,
                onWatch = {
                    val isLocked = "${active.providerId}:${active.remoteId}" in locked
                    if (isLocked) pinPrompt = active
                    else vm.resolveAndPlay(active, onPlay)
                },
            )
        }
    }

    // PIN dialog when the user clicks a locked channel.
    pinPrompt?.let { ch ->
        com.ultratv.tv.nativeapp.ui.parental.PinPromptDialog(
            title = "🔒 ${ch.name}",
            onUnlocked = {
                pinPrompt = null
                vm.resolveAndPlay(ch, onPlay)
            },
            onCancel = { pinPrompt = null },
        )
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = selected || focused
    Box {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            shape = CardDefaults.shape(RoundedCornerShape(0.dp)),
            colors = CardDefaults.colors(
                containerColor = if (highlighted) UltraTokens.AccentSoft else Color.Transparent,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (highlighted) UltraTokens.Fg else UltraTokens.Fg3,
                    maxLines = 1,
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 8.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(UltraTokens.Accent, RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    position: Int,
    locked: Boolean = false,
    active: Boolean = false,
    nowProgramme: com.ultratv.tv.nativeapp.data.db.EpgEntity? = null,
    nextProgramme: com.ultratv.tv.nativeapp.data.db.EpgEntity? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Fire focus callback so the preview pane can auto-tune to whatever the
    // user is hovering, OTT-Navigator style.
    LaunchedEffect(focused) { if (focused) onFocus() }
    val highlight = focused || active
    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = CardDefaults.shape(RoundedCornerShape(0.dp)),
        colors = com.ultratv.tv.nativeapp.ui.theme.ultraCardColors(
            containerColor = if (highlight) UltraTokens.AccentSoft else Color.Transparent,
            focusedContainerColor = UltraTokens.AccentSoft,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "%02d".format(position),
                color = if (highlight) UltraTokens.Accent else UltraTokens.Fg4,
                fontSize = 13.sp,
                fontFamily = UltraFonts.Mono,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(32.dp),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logo != null) {
                    AsyncImage(model = channel.logo, contentDescription = channel.name, modifier = Modifier.fillMaxSize())
                } else {
                    com.ultratv.tv.nativeapp.ui.common.LetterAvatar(
                        text = channel.name, fontSize = 16.sp, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.name + if (locked) "  🔒" else "",
                        color = if (highlight) UltraTokens.Fg else UltraTokens.Fg2,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                if (nowProgramme != null) {
                    Text(
                        nowProgramme.title + (nextProgramme?.let { "  ·  puis ${it.title}" } ?: ""),
                        color = UltraTokens.Fg3,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun LivePreviewPane(
    channel: ChannelEntity,
    vm: LiveViewModel,
    nowProgramme: com.ultratv.tv.nativeapp.data.db.EpgEntity?,
    nextProgramme: com.ultratv.tv.nativeapp.data.db.EpgEntity?,
    onWatch: () -> Unit,
) {
    val nowTitle = nowProgramme?.title ?: "Programme en cours"
    val nextTitle = nextProgramme?.title ?: "À venir"
    val hue = channel.name.hashCode()
    val context = androidx.compose.ui.platform.LocalContext.current

    // One mini-player kept alive while the screen is on. We swap its
    // MediaItem with a debounce when the focused channel changes, so D-pad
    // navigation doesn't hammer the network with stalker create_link calls.
    val miniPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            volume = 0f // Silent — audio belongs to the full player.
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { miniPlayer.release() }
    }
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember(channel.id) { mutableStateOf(true) }
    LaunchedEffect(channel.id) {
        loading = true
        resolvedUrl = null
        // 700 ms debounce: user is scrolling, don't hit the network on each
        // row. resolvePreviewUrl swallows Stalker `create_link` calls when
        // needed; for plain URLs it's a no-op.
        kotlinx.coroutines.delay(700)
        val url = runCatching { vm.resolvePreviewUrl(channel) }.getOrNull()
        resolvedUrl = url
        loading = false
        if (url != null) {
            miniPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            miniPlayer.prepare()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // 16:9 preview window with the live mini-player
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            hueColor(hue, 0.55f, 0.35f),
                            hueColor(hue, 0.45f, 0.10f),
                        )
                    )
                )
                .border(1.dp, UltraTokens.Line2, RoundedCornerShape(18.dp)),
        ) {
            // Mini-player surface
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false
                        player = miniPlayer
                    }
                },
            )
            // Fallback while loading or unresolved: big channel logo overlay.
            if (resolvedUrl == null || loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChannelLogo(
                        name = channel.name,
                        logoUrl = channel.logo,
                        short = null,
                        hueSeed = hue,
                        hd = null,
                        size = 120.dp,
                        showBadge = false,
                    )
                }
            }

            // Top overlay: LIVE chip + category badge
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LiveChip()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UhdBadge()
                }
            }

            // Bottom overlay: number/name + now title + watch CTA
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xD9000000),
                        )
                    )
                    .padding(start = 22.dp, end = 22.dp, top = 60.dp, bottom = 22.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "· ${channel.name}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            nowTitle,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontFamily = UltraFonts.Serif,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        // TiviMate-style full-day schedule of the focused channel.
        var schedule by remember(channel.id) { mutableStateOf<List<com.ultratv.tv.nativeapp.data.db.EpgEntity>>(emptyList()) }
        LaunchedEffect(channel.id) {
            schedule = runCatching { vm.loadDaySchedule(channel.id) }.getOrDefault(emptyList())
        }
        DaySchedule(channel = channel, items = schedule, onWatch = onWatch, modifier = Modifier.weight(1f))

        // D-pad hint bar pinned to the bottom of the column.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hint("OK", "Lecture plein écran")
            Hint("▲▼", "Zap")
            Hint("★", "Favori")
        }
    }
}

/**
 * Vertical full-day schedule for the focused channel — the TiviMate
 * "tonight's schedule" column. Past programmes appear muted, the current
 * one in accent, the rest with a subdued time + title. The whole column
 * scrolls; the current programme auto-scrolls into view.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun DaySchedule(
    channel: ChannelEntity,
    items: List<com.ultratv.tv.nativeapp.data.db.EpgEntity>,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    val currentIdx = items.indexOfFirst { it.startMs <= now && it.endMs > now }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(currentIdx, channel.id) {
        if (currentIdx >= 0) {
            // Scroll so the current programme sits roughly at the top third.
            listState.animateScrollToItem(
                index = currentIdx.coerceAtLeast(0),
                scrollOffset = 0,
            )
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PROGRAMME DE LA JOURNÉE",
                color = UltraTokens.Fg3,
                fontSize = 10.sp,
                letterSpacing = 2.3.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            Text(channel.name, color = UltraTokens.Fg3, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))

        if (items.isEmpty()) {
            Text(
                "Pas d'EPG disponible pour cette chaîne.",
                color = UltraTokens.Fg4,
                fontSize = 13.sp,
            )
            return
        }

        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items, key = { it.id }) { prog ->
                ScheduleRow(prog = prog, isCurrent = (prog.startMs <= now && prog.endMs > now), onClick = onWatch)
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun ScheduleRow(prog: com.ultratv.tv.nativeapp.data.db.EpgEntity, isCurrent: Boolean, onClick: () -> Unit) {
    val past = prog.endMs <= System.currentTimeMillis()
    val timeColor = when {
        isCurrent -> UltraTokens.Accent
        past -> UltraTokens.Fg4
        else -> UltraTokens.Fg3
    }
    val titleColor = when {
        isCurrent -> UltraTokens.Fg
        past -> UltraTokens.Fg4
        else -> UltraTokens.Fg2
    }
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = com.ultratv.tv.nativeapp.ui.theme.ultraCardColors(
            containerColor = if (isCurrent) UltraTokens.AccentSoft else Color.Transparent,
            focusedContainerColor = if (isCurrent) UltraTokens.Accent else UltraTokens.AccentSoft,
            focusedContentColor = if (isCurrent) Color.White else UltraTokens.Fg,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatHm(prog.startMs),
                color = timeColor,
                fontSize = 13.sp,
                fontFamily = UltraFonts.Mono,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.width(72.dp),
            )
            Text(
                prog.title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
            if (isCurrent) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(UltraTokens.Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "EN COURS",
                        color = Color.White,
                        fontSize = 9.sp,
                        letterSpacing = 0.6.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TonightSchedule(
    channel: ChannelEntity,
    now: com.ultratv.tv.nativeapp.data.db.EpgEntity?,
    next: com.ultratv.tv.nativeapp.data.db.EpgEntity?,
    onWatch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // Current programme — biggest block, with a thin progress bar
        // computed from now/end. Click = open full-screen player.
        Card(
            onClick = onWatch,
            shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
            colors = com.ultratv.tv.nativeapp.ui.theme.ultraCardColors(
                containerColor = UltraTokens.AccentSoft,
                focusedContainerColor = UltraTokens.Accent,
                focusedContentColor = androidx.compose.ui.graphics.Color.White,
            ),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x4DFF3A2F), RoundedCornerShape(14.dp)),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "EN COURS",
                        color = UltraTokens.Accent,
                        fontSize = 10.sp,
                        letterSpacing = 2.3.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        channel.name,
                        color = UltraTokens.Fg3,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    now?.title ?: "Programme en cours",
                    color = UltraTokens.Fg,
                    fontFamily = UltraFonts.Serif,
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    maxLines = 2,
                )
                if (now != null) {
                    Spacer(Modifier.height(8.dp))
                    val nowMs = System.currentTimeMillis()
                    val total = (now.endMs - now.startMs).coerceAtLeast(1)
                    val elapsed = (nowMs - now.startMs).coerceIn(0, total)
                    val pct = elapsed.toFloat() / total.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatHm(now.startMs),
                            color = UltraTokens.Fg3,
                            fontSize = 11.sp,
                            fontFamily = UltraFonts.Mono,
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(Color(0x33FFFFFF), RoundedCornerShape(2.dp)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(pct)
                                    .height(3.dp)
                                    .background(UltraTokens.Accent, RoundedCornerShape(2.dp)),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            formatHm(now.endMs),
                            color = UltraTokens.Fg3,
                            fontSize = 11.sp,
                            fontFamily = UltraFonts.Mono,
                        )
                    }
                }
            }
        }
        // Upcoming entries: at minimum the "next" we already know about.
        if (next != null) {
            UpcomingRow(prog = next)
        }
    }
}

@Composable
private fun UpcomingRow(prog: com.ultratv.tv.nativeapp.data.db.EpgEntity) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatHm(prog.startMs),
            color = UltraTokens.Fg3,
            fontSize = 12.sp,
            fontFamily = UltraFonts.Mono,
            modifier = Modifier.width(56.dp),
        )
        Text(
            prog.title,
            color = UltraTokens.Fg2,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

private val hmFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private fun formatHm(ms: Long): String = hmFmt.format(java.util.Date(ms))


@Composable
private fun LiveChip() {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x24FF3A2F))
            .border(1.dp, Color(0x66FF3A2F), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(UltraTokens.Live)
        )
        Spacer(Modifier.width(8.dp))
        Text("EN DIRECT", color = Color(0xFFFFB5AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun UhdBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(UltraTokens.Uhd)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text("HD", color = Color(0xFF2B1700), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun ProgrammeCard(
    label: String,
    title: String,
    sub: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (accent) Brush.linearGradient(
                    listOf(Color(0x1AFF3A2F), Color(0x05FF3A2F))
                ) else Brush.linearGradient(listOf(UltraTokens.Surface1, UltraTokens.Surface1))
            )
            .border(
                1.dp,
                if (accent) Color(0x4DFF3A2F) else UltraTokens.Line,
                RoundedCornerShape(14.dp),
            )
            .padding(18.dp),
    ) {
        Text(
            label,
            color = if (accent) UltraTokens.Accent else UltraTokens.Fg3,
            fontSize = 10.sp,
            letterSpacing = 2.3.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = UltraTokens.Fg,
            fontFamily = UltraFonts.Serif,
            fontSize = 22.sp,
            lineHeight = 24.sp,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Text(sub, color = UltraTokens.Fg3, fontSize = 12.sp)
    }
}

@Composable
private fun Hint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(UltraTokens.Surface3)
                .border(1.dp, UltraTokens.Line2, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(key, color = UltraTokens.Fg2, fontSize = 11.sp, fontFamily = UltraFonts.Mono)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = UltraTokens.Fg3, fontSize = 12.sp)
    }
}

private fun hueColor(seed: Int, sat: Float, light: Float): Color =
    com.ultratv.tv.nativeapp.ui.common.HueGradient.hsl(seed, sat, light)

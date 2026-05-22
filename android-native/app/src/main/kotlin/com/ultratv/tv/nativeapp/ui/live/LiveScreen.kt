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
        // ---- Left pane: categories ----
        Column(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
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

        // ---- Middle pane: channels in selected category ----
        Column(
            modifier = Modifier
                .width(470.dp)
                .fillMaxHeight()
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

        // ---- Right pane: live preview window + now/next ----
        val active = chans.getOrNull(activeIdx)
        if (active != null) {
            LivePreviewPane(
                channel = active,
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
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
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
    nowProgramme: com.ultratv.tv.nativeapp.data.db.EpgEntity?,
    nextProgramme: com.ultratv.tv.nativeapp.data.db.EpgEntity?,
    onWatch: () -> Unit,
) {
    val nowTitle = nowProgramme?.title ?: "Programme en cours"
    val nextTitle = nextProgramme?.title ?: "À venir"
    val hue = channel.name.hashCode()

    Column(
        Modifier
            .fillMaxSize()
            .padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // "TV" preview window — 16:9, gradient backdrop, channel logo centered
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
            // Big centered channel logo
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
                    Spacer(Modifier.width(14.dp))
                    androidx.tv.material3.Button(
                        onClick = onWatch,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = UltraTokens.CtaBg,
                            contentColor = UltraTokens.CtaFgOnCta,
                        ),
                        modifier = Modifier.border(3.dp, UltraTokens.Accent, RoundedCornerShape(12.dp)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UltraIcon(UltraIcon.Play, size = 16.dp, color = UltraTokens.CtaFgOnCta)
                            Spacer(Modifier.width(8.dp))
                            Text("Regarder", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Now + next cards
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProgrammeCard(
                label = "EN COURS",
                title = nowTitle,
                sub = "Programme en cours",
                accent = true,
                modifier = Modifier.weight(1f),
            )
            ProgrammeCard(
                label = "ENSUITE",
                title = nextTitle,
                sub = "À venir",
                accent = false,
                modifier = Modifier.weight(1f),
            )
        }

        // Hints
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hint("OK", "Lecture")
            Hint("▲▼", "Zap")
            Hint("★", "Favori")
        }
    }
}

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

private fun hueColor(seed: Int, sat: Float, light: Float): Color {
    val h = ((seed % 360) + 360) % 360
    val c = (1f - kotlin.math.abs(2 * light - 1f)) * sat
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = light - c / 2f
    fun b(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
    return Color(0xFF shl 24 or (b(r1) shl 16) or (b(g1) shl 8) or b(b1))
}

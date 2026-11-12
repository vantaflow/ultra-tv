package com.ultratv.tv.nativeapp.ui.sport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.recording.ScheduleResult
import com.ultratv.tv.nativeapp.data.sport.SportDay
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportLogic
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.recNoSpace
import com.ultratv.tv.nativeapp.i18n.recNothing
import com.ultratv.tv.nativeapp.i18n.recScheduled
import com.ultratv.tv.nativeapp.i18n.remindSet
import com.ultratv.tv.nativeapp.i18n.sportChannels
import com.ultratv.tv.nativeapp.i18n.sportEmptyLive
import com.ultratv.tv.nativeapp.i18n.sportEmptyUpcoming
import com.ultratv.tv.nativeapp.i18n.sportHomeRow
import com.ultratv.tv.nativeapp.i18n.sportLiveNow
import com.ultratv.tv.nativeapp.i18n.sportNav
import com.ultratv.tv.nativeapp.i18n.sportNoChannels
import com.ultratv.tv.nativeapp.i18n.sportNoGuideBody
import com.ultratv.tv.nativeapp.i18n.sportNoGuideTitle
import com.ultratv.tv.nativeapp.i18n.sportNoSource
import com.ultratv.tv.nativeapp.i18n.sportOpenSync
import com.ultratv.tv.nativeapp.i18n.sportRecord
import com.ultratv.tv.nativeapp.i18n.sportRemind
import com.ultratv.tv.nativeapp.i18n.sportToday
import com.ultratv.tv.nativeapp.i18n.sportTomorrow
import com.ultratv.tv.nativeapp.i18n.sportUpcoming
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.EpgProgressBar
import com.ultratv.tv.nativeapp.ui.common.RowBleed
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.rememberEpgClock
import com.ultratv.tv.nativeapp.ui.common.rowBleedStart
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.SectionTitle
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.StateCard
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.parental.PinPromptDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LIVE_GREEN = Color(0xFF3DDC84)

/**
 * Rubrique Sport (facultative) : « En direct maintenant », « À venir (48 h) » et « Chaînes sport », tirés du guide et des
 * catégories de la source de l'utilisateur (aucun service externe). OK sur un direct lance la chaîne avec le même contexte
 * que le Guide et le Direct (élément LIVE + file de zapping).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SportScreen(
    onPlay: (url: String, title: String) -> Unit,
    onGoSettings: () -> Unit,
    vm: SportViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val st by vm.state.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val clock = rememberEpgClock()
    val touch = LocalTouch.current
    var pinFor by remember { mutableStateOf<ChannelEntity?>(null) }

    val play: (ChannelEntity) -> Unit = { c ->
        if ("${c.providerId}:${c.remoteId}" in locked) pinFor = c else vm.play(c) { onPlay(c.streamUrl, c.title) }
    }
    // Android 13+ : la permission de notification est demandée au PREMIER rappel (comme dans le Guide).
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotifications: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) runCatching { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    val onRemind: (SportEntry) -> Unit = { e -> vm.addReminder(e.channel, e.prog); askNotifications(); Toaster.ok(D.remindSet) }
    val onRecord: (SportEntry) -> Unit = { e ->
        vm.record(e.channel, e.prog) { r ->
            when (r) {
                is ScheduleResult.Scheduled -> Toaster.ok(D.recScheduled(r.count))
                ScheduleResult.NoSpace -> Toaster.err(D.recNoSpace)
                ScheduleResult.NothingToSchedule -> Toaster.show(D.recNothing)
            }
        }
    }

    val pad = if (touch) 16.dp else 72.design
    val cardW = if (touch) 260.dp else 380.design
    val locale = remember(D.lang) { D.lang.code.takeIf { it.length in 2..3 }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault() }

    LazyColumn(
        Modifier.fillMaxSize().background(Ux.Bg),
        contentPadding = PaddingValues(start = pad, end = pad, top = if (touch) 16.dp else 40.design, bottom = 54.design),
        verticalArrangement = Arrangement.spacedBy(14.design),
    ) {
        item(key = "title") {
            Text(D.sportNav, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            st.loading -> item(key = "loading") { Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx) }
            !st.hasSource -> item(key = "nosource") {
                StateCard(D.sportNav, D.sportNoSource, SportIcons.Trophy, D.openSourceSettings, onGoSettings, Modifier.fillMaxWidth())
            }
            else -> {
                if (!st.hasGuide) item(key = "noguide") {
                    StateCard(D.sportNoGuideTitle, D.sportNoGuideBody, SportIcons.Trophy, D.sportOpenSync, onGoSettings, Modifier.fillMaxWidth())
                } else {
                    // ── En direct maintenant ──
                    item(key = "live-h") { Header(D.sportLiveNow) }
                    if (st.live.isEmpty()) item(key = "live-empty") { Empty(D.sportEmptyLive) }
                    else item(key = "live") {
                        LazyRow(Modifier.rowBleedStart(RowBleed).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(24.design), contentPadding = PaddingValues(start = RowBleed, end = 48.design)) {
                            items(st.live, key = { "l${it.channel.id}" }, contentType = { "live" }) { e -> SportLiveCard(e, clock, cardW) { play(e.channel) } }
                        }
                    }
                    // ── À venir (48 h), groupé par jour ──
                    item(key = "up-h") { Header(D.sportUpcoming) }
                    if (st.upcoming.isEmpty()) item(key = "up-empty") { Empty(D.sportEmptyUpcoming) }
                    else {
                        for (day in st.upcoming) {
                            item(key = "d${day.dayStartMs}") { DayLabel(dayLabel(D, day, clock.value, locale)) }
                            items(day.entries, key = { "u${it.channel.id}-${it.prog.startMs}" }, contentType = { "upcoming" }) { e ->
                                UpcomingRow(e, D, touch, onRemind = { onRemind(e) }, onRecord = { onRecord(e) })
                            }
                        }
                    }
                }
                // ── Chaînes sport (aussi sans guide) ──
                item(key = "ch-h") { Header(D.sportChannels) }
                if (st.channels.isEmpty()) item(key = "ch-empty") { Empty(D.sportNoChannels) }
                else item(key = "ch") {
                    LazyRow(Modifier.rowBleedStart(RowBleed).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(20.design), contentPadding = PaddingValues(start = RowBleed, end = 48.design)) {
                        items(st.channels, key = { "c${it.id}" }, contentType = { "channel" }) { c -> SportChannelCard(c) { play(c) } }
                    }
                }
            }
        }
    }

    pinFor?.let { c -> PinPromptDialog(title = c.title, onUnlocked = { pinFor = null; vm.play(c) { onPlay(c.streamUrl, c.title) } }, onCancel = { pinFor = null }) }
}

@Composable
private fun Header(text: String) {
    Box(Modifier.padding(top = 18.design)) { SectionTitle(text) }
}

@Composable
private fun DayLabel(text: String) {
    Text(text, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, modifier = Modifier.padding(top = 8.design))
}

@Composable
private fun Empty(text: String) {
    Text(text, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, lineHeight = 34.spx)
}

/** « Aujourd'hui » / « Demain » / « jeudi 12 novembre ». */
private fun dayLabel(D: DesignStrings, day: SportDay, nowMs: Long, locale: Locale): String = when (SportLogic.dayOffset(day.dayStartMs, nowMs)) {
    0 -> D.sportToday
    1 -> D.sportTomorrow
    else -> SimpleDateFormat("EEEE d MMMM", locale).format(Date(day.dayStartMs)).replaceFirstChar { it.titlecase(locale) }
}

/** Carte d'un direct : logo, numéro et nom de la chaîne, titre du programme, horaire et progression. */
@Composable
internal fun SportLiveCard(e: SportEntry, clock: State<Long>, width: Dp, onClick: () -> Unit) {
    val c = e.channel
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Surface, ringWidth = 5.design, modifier = Modifier.width(width).height(230.designT)) { f ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.design, vertical = 16.design), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                LogoBox(c.logo, c.title, Modifier.width(72.design).height(52.design), radius = 8, pad = 4, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface2, decodeSize = DpSize(72.design, 52.design))
                Text(
                    (if (c.num > 0) "${c.num}  " else "") + c.title,
                    color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            Text(e.prog.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, lineHeight = 31.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                    Box(Modifier.size(12.design).clip(CircleShape).background(LIVE_GREEN))
                    Text(EpgClock.range(e.prog.startMs, e.prog.endMs), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                }
                EpgProgressBar(e.prog.startMs, e.prog.endMs, clock, heightDp = 4f, track = if (f) Ux.OnFocus2 else Ux.Surface2)
            }
        }
    }
}

@Composable
private fun SportChannelCard(c: ChannelEntity, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Surface, ringWidth = 5.design, modifier = Modifier.width(300.design).height(110.designT)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 18.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
            LogoBox(c.logo, c.title, Modifier.width(72.design).height(52.design), radius = 8, pad = 4, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface2, decodeSize = DpSize(72.design, 52.design))
            Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, lineHeight = 26.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

/** Un programme à venir : heure, chaîne, titre, puis « Me rappeler » et « Enregistrer ». */
@Composable
private fun UpcomingRow(e: SportEntry, D: DesignStrings, touch: Boolean, onRemind: () -> Unit, onRecord: () -> Unit) {
    val c = e.channel
    val info: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
            Text(EpgClock.hm(e.prog.startMs), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, modifier = Modifier.width(96.design))
            LogoBox(c.logo, c.title, Modifier.width(64.design).height(46.design), radius = 8, pad = 4, decodeSize = DpSize(64.design, 46.design))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(e.prog.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(c.title, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val buttons: @Composable () -> Unit = {
        PillButton(D.sportRemind, onRemind, heightPx = 60, hPadPx = 26, fontPx = 22)
        PillButton(D.sportRecord, onRecord, heightPx = 60, hPadPx = 26, fontPx = 22, iconPath = Icons.Record)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.design)).background(Ux.SurfaceDeep).padding(horizontal = 20.design, vertical = 14.design), verticalArrangement = Arrangement.spacedBy(12.design)) {
        if (touch) {
            info(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.design)) { buttons() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.design)) {
                info(Modifier.weight(1f))
                buttons()
            }
        }
    }
}

/** Rangée « Sport en direct » de l'accueil TV (n'existe que si la rubrique est activée et qu'un direct de sport est en cours). */
@Composable
fun SportHomeSection(rowModifier: Modifier, contentPadding: PaddingValues, onPlayChannel: (ChannelEntity) -> Unit, onSeeAll: () -> Unit, vm: SportViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    if (st.live.isEmpty()) return
    val D = LocalDs.current
    val clock = rememberEpgClock()
    Column(verticalArrangement = Arrangement.spacedBy(20.design)) {
        SectionTitle(D.sportHomeRow)
        LazyRow(rowModifier, horizontalArrangement = Arrangement.spacedBy(24.design), contentPadding = contentPadding) {
            items(st.live.take(HOME_MAX), key = { "h${it.channel.id}" }, contentType = { "sport" }) { e -> SportLiveCard(e, clock, 380.design) { onPlayChannel(e.channel) } }
            item(key = "see-all", contentType = "see-all") {
                FocusSurface(onClick = onSeeAll, shape = RoundedCornerShape(20.design), bg = Ux.Surface, ringWidth = 5.design, modifier = Modifier.width(220.design).height(230.designT)) { f ->
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(D.seeAll, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(10.design))
                        com.ultratv.tv.nativeapp.ui.design.DIcon(Icons.Arrow, 26.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.4f)
                    }
                }
            }
        }
    }
}

private const val HOME_MAX = 12

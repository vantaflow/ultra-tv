package com.ultratv.tv.nativeapp.ui.sport

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.sport.LeagueChannels
import com.ultratv.tv.nativeapp.data.sport.LeagueLogic
import com.ultratv.tv.nativeapp.data.sport.LeagueMatch
import com.ultratv.tv.nativeapp.data.sport.MatchSheet
import com.ultratv.tv.nativeapp.data.sport.MatchState
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportMatching
import com.ultratv.tv.nativeapp.data.sport.SpoilerLogic
import com.ultratv.tv.nativeapp.data.sport.StandingRow
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.sportLgCustomize
import com.ultratv.tv.nativeapp.i18n.sportLgDone
import com.ultratv.tv.nativeapp.i18n.sportLgEmpty
import com.ultratv.tv.nativeapp.i18n.sportLgFixtures
import com.ultratv.tv.nativeapp.i18n.sportLgHide
import com.ultratv.tv.nativeapp.i18n.sportLgLive
import com.ultratv.tv.nativeapp.i18n.sportLgNotPaired
import com.ultratv.tv.nativeapp.i18n.sportLgResults
import com.ultratv.tv.nativeapp.i18n.sportLgShow
import com.ultratv.tv.nativeapp.i18n.sportLgStale
import com.ultratv.tv.nativeapp.i18n.sportLgTable
import com.ultratv.tv.nativeapp.i18n.sportLgUnavailable
import com.ultratv.tv.nativeapp.i18n.sportMatchKeyMoments
import com.ultratv.tv.nativeapp.i18n.sportMatchNoChannel
import com.ultratv.tv.nativeapp.i18n.sportMatchRound
import com.ultratv.tv.nativeapp.i18n.sportMatchStats
import com.ultratv.tv.nativeapp.i18n.sportMatchWatch
import com.ultratv.tv.nativeapp.i18n.sportNoSpoilerHint
import com.ultratv.tv.nativeapp.i18n.sportNoSpoilerLabel
import com.ultratv.tv.nativeapp.i18n.sportSpoilerHidden
import com.ultratv.tv.nativeapp.i18n.sportSpoilerShowAll
import com.ultratv.tv.nativeapp.i18n.sportSpoilerShowScore
import com.ultratv.tv.nativeapp.i18n.sportTblGd
import com.ultratv.tv.nativeapp.i18n.sportTblPlayed
import com.ultratv.tv.nativeapp.i18n.sportTblPts
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Espace « Compétitions » de la rubrique Sport : ligues dans l'ordre du profil, résultats / à venir / classement, feuille
 * de match (score, temps forts, statistiques) et mode sans spoiler. Chaque match est relié aux chaînes de l'utilisateur
 * (programmes du guide qui diffusent la même rencontre) : OK sur une chaîne lance le direct comme partout dans l'appli.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LeaguesContent(
    entries: List<SportEntry>,
    onPlay: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
    vm: LeaguesViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val st by vm.state.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val touch = LocalTouch.current
    var customizing by rememberSaveable { mutableStateOf(false) }
    val locale = remember(D.lang) { D.lang.code.takeIf { it.length in 2..3 }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault() }
    val pad = if (touch) 16.dp else 56.design

    Column(modifier.fillMaxSize().background(Ux.Bg).padding(start = pad, end = pad, top = 8.design), verticalArrangement = Arrangement.spacedBy(14.design)) {
        // ── Ligues (ordre et masquage du profil) ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
            LazyRow(Modifier.weight(1f).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.design)) {
                items(st.visible, key = { it.key }) { l -> Pill(l.name, l.key == st.selected) { vm.select(l.key) } }
            }
            Pill(D.sportLgCustomize, false) { customizing = true }
        }
        // ── Onglets + mode sans spoiler ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.design)) {
            Pill(D.sportLgResults, st.tab == LeagueTab.RESULTS) { vm.selectTab(LeagueTab.RESULTS) }
            Pill(D.sportLgFixtures, st.tab == LeagueTab.FIXTURES) { vm.selectTab(LeagueTab.FIXTURES) }
            Pill(D.sportLgTable, st.tab == LeagueTab.TABLE) { vm.selectTab(LeagueTab.TABLE) }
            Spacer(Modifier.weight(1f))
            if (st.noSpoiler && st.tab != LeagueTab.FIXTURES && st.status == LeagueStatus.OK) {
                val shown = if (st.tab == LeagueTab.TABLE) st.selected?.let { it in st.revealedTables } == true else LeagueLogic.results(st.matches).all { it.id in st.revealed }
                if (!shown) Pill(D.sportSpoilerShowAll, false) {
                    if (st.tab == LeagueTab.TABLE) st.selected?.let(vm::revealTable) else vm.revealAll(st.matches)
                }
            }
        }
        if (st.stale && st.status == LeagueStatus.OK) Text(D.sportLgStale, color = Ux.Text3, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1)

        when (st.status) {
            LeagueStatus.LOADING -> Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx)
            LeagueStatus.NOT_PAIRED -> Text(D.sportLgNotPaired, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            LeagueStatus.UNAVAILABLE -> Text(D.sportLgUnavailable, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            LeagueStatus.OK -> when (st.tab) {
                LeagueTab.TABLE -> TableList(st, D)
                else -> MatchList(st, D, locale, onOpen = vm::openMatch)
            }
        }
    }

    sheet?.let { s ->
        val live = st.matches.firstOrNull { it.id == s.match.id } ?: s.match
        MatchSheetDialog(
            D, locale, live, s.sheet, s.loading, st.noSpoiler, s.match.id in st.revealed,
            channels = remember(live, entries) { LeagueChannels.channelsFor(live, entries) },
            onReveal = { vm.reveal(s.match.id) }, onPlay = { c -> vm.closeMatch(); onPlay(c) }, onClose = vm::closeMatch,
        )
    }
    if (customizing) CustomizeDialog(D, st, vm, onClose = { customizing = false })
}

// ───────────────────────── liste des matchs ─────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MatchList(st: LeaguesState, D: DesignStrings, locale: Locale, onOpen: (LeagueMatch) -> Unit) {
    val now = System.currentTimeMillis()
    val live = remember(st.matches) { LeagueLogic.live(st.matches) }
    val fixtures = remember(st.matches) { LeagueLogic.fixtures(st.matches, now) }
    val results = remember(st.matches) { LeagueLogic.results(st.matches) }
    val rows: List<MatchRowItem> = when (st.tab) {
        LeagueTab.FIXTURES -> fixtures.map { MatchRowItem(it) }
        else -> live.map { MatchRowItem(it, header = D.sportLgLive) }.take(1) + live.drop(1).map { MatchRowItem(it) } + results.map { MatchRowItem(it) }
    }
    if (rows.isEmpty()) { Text(D.sportLgEmpty, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx); return }
    val fmt = remember(locale) { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMHHmm"), locale) }
    LazyColumn(Modifier.fillMaxSize().focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 54.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
        items(rows, key = { it.match.id }) { r ->
            if (r.header != null) Text(r.header, color = Ux.Accent, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1)
            MatchRow(r.match, fmt, D, show = SpoilerLogic.showScore(st.noSpoiler, r.match.id, st.revealed)) { onOpen(r.match) }
        }
    }
}

private data class MatchRowItem(val match: LeagueMatch, val header: String? = null)

@Composable
private fun MatchRow(m: LeagueMatch, fmt: SimpleDateFormat, D: DesignStrings, show: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.01f, ringWidth = 0.dp, modifier = Modifier.fillMaxWidth().heightIn(min = 76.designT)) { f ->
        val c1 = if (f) Ux.TextOnLight else Ux.Text
        val c2 = if (f) Ux.OnFocus2 else Ux.Text3
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.design, vertical = 10.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
            Column(Modifier.width(if (LocalTouch.current) 84.dp else 150.design)) {
                Text(m.startMs?.let { fmt.format(Date(it)) }.orEmpty(), color = c2, fontFamily = Manrope, fontSize = 15.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (m.state == MatchState.LIVE) Text(listOfNotNull(D.sportLgLive.uppercase(), m.progress?.takeIf { show }?.let { "$it'" }).joinToString(" "), color = Ux.Accent, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.spx, maxLines = 1)
            }
            if (m.home != null && m.away != null) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                    TeamLine(m.home, m.homeBadge, c1, if (show) m.homeScore else null)
                    TeamLine(m.away, m.awayBadge, c1, if (show) m.awayScore else null)
                }
            } else Text(m.title, color = c1, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!show && SpoilerLogic.hasSpoiler(m)) Text(D.sportSpoilerHidden, color = c2, fontFamily = Manrope, fontSize = 14.spx, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 110.design))
        }
    }
}

@Composable
private fun TeamLine(name: String, badge: String?, color: Color, score: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
        Crest(badge, 28.design)
        Text(name, color = color, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (score != null) Text(score.toString(), color = color, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
    }
}

/** Écusson (URL https du Worker), décodé petit ; rien si absent ou en échec. */
@Composable
private fun Crest(url: String?, size: Dp) {
    if (url == null) { Box(Modifier.size(size)); return }
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) { Box(Modifier.size(size)); return }
    val ctx = LocalContext.current
    val request = remember(url) { coil3.request.ImageRequest.Builder(ctx).data(url).size(96, 96).build() }
    coil3.compose.AsyncImage(request, null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit, onError = { failed = true })
}

// ───────────────────────── classement ─────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TableList(st: LeaguesState, D: DesignStrings) {
    val key = st.selected ?: return
    if (!SpoilerLogic.showTable(st.noSpoiler, key, st.revealedTables)) { Text(D.sportSpoilerHidden, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx); return }
    val rows = st.table
    if (rows == null) { Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx); return }
    if (rows.isEmpty()) { Text(D.sportLgEmpty, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx); return }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.design)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.design), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(40.design))
            Spacer(Modifier.weight(1f))
            HeadCell(D.sportTblPlayed); HeadCell(D.sportTblGd); HeadCell(D.sportTblPts)
        }
        LazyColumn(Modifier.fillMaxSize().focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 54.design), verticalArrangement = Arrangement.spacedBy(6.design)) {
            items(rows, key = { "${it.group}-${it.rank}-${it.team}" }) { r -> StandingLine(r) }
        }
    }
}

@Composable
private fun HeadCell(text: String) = Text(text, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(60.design))

@Composable
private fun StandingLine(r: StandingRow) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.design)).background(Ux.Card).padding(horizontal = 18.design, vertical = 10.design),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design),
    ) {
        Text(r.rank?.toString().orEmpty(), color = Ux.Text3, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, modifier = Modifier.width(30.design))
        Crest(r.badge, 26.design)
        Text(r.team, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 19.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Cell(r.played?.toString()); Cell(r.goalDiff?.let { if (it > 0) "+$it" else "$it" }); Cell(r.points?.toString(), bold = true)
    }
}

@Composable
private fun Cell(text: String?, bold: Boolean = false) =
    Text(text.orEmpty(), color = if (bold) Ux.Text else Ux.Text2, fontFamily = Manrope, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, fontSize = 18.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(60.design))

// ───────────────────────── feuille de match ─────────────────────────

@Composable
private fun MatchSheetDialog(
    D: DesignStrings, locale: Locale, m: LeagueMatch, sheet: MatchSheet?, loading: Boolean,
    noSpoiler: Boolean, revealed: Boolean, channels: List<SportEntry>,
    onReveal: () -> Unit, onPlay: (ChannelEntity) -> Unit, onClose: () -> Unit,
) {
    val show = !noSpoiler || revealed
    val fmt = remember(locale) { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMHHmm"), locale) }
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Scrim)) {
        val maxW = if (LocalTouch.current) 520.dp else 820.design
        LazyColumn(
            Modifier.widthIn(max = maxW).fillMaxWidth().heightIn(max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).focusGroup(),
            contentPadding = PaddingValues(28.design), verticalArrangement = Arrangement.spacedBy(14.design),
        ) {
            item {
                Text(m.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(m.startMs?.let { fmt.format(Date(it)) }, sheet?.venue, m.round?.let { D.sportMatchRound(it) }).joinToString(" · "), color = Ux.Text3, fontFamily = Manrope, fontSize = 17.spx, maxLines = 2)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Crest(m.homeBadge, 56.design); Spacer(Modifier.width(18.design))
                    if (m.hasScore && show) Text("${m.homeScore} – ${m.awayScore}", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, maxLines = 1)
                    else if (SpoilerLogic.hasSpoiler(m)) Text("• – •", color = Ux.Text3, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, maxLines = 1)
                    else Text("–", color = Ux.Text3, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, maxLines = 1)
                    Spacer(Modifier.width(18.design)); Crest(m.awayBadge, 56.design)
                }
            }
            if (!show && SpoilerLogic.hasSpoiler(m)) item { Pill(D.sportSpoilerShowScore, false, onReveal) }
            // Temps forts et statistiques : seulement quand on a le droit de voir le score (ils le révèlent).
            if (show && sheet != null && sheet.timeline.isNotEmpty()) {
                item { Text(D.sportMatchKeyMoments, color = Ux.Accent, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.spx) }
                items(sheet.timeline.filter { it.kind != "sub" }.take(24)) { t ->
                    val glyph = when (t.kind) { "goal" -> "⚽"; "card" -> if ((t.detail ?: "").contains("red", true)) "🟥" else "🟨"; "var" -> "VAR"; else -> "•" }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.design), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.minute?.let { "$it'" }.orEmpty(), color = Ux.Text3, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, modifier = Modifier.width(52.design))
                        Text(glyph, fontSize = 17.spx, color = Ux.Text, maxLines = 1)
                        Text(listOfNotNull(t.player, t.assist?.let { "($it)" }).joinToString(" "), color = Ux.Text, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (t.home) (m.home ?: "") else (m.away ?: ""), color = Ux.Text3, fontFamily = Manrope, fontSize = 15.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.design))
                    }
                }
            }
            if (show && sheet != null && sheet.stats.isNotEmpty()) {
                item { Text(D.sportMatchStats, color = Ux.Accent, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.spx) }
                items(sheet.stats.take(12)) { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.home.toString(), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, modifier = Modifier.width(56.design))
                        Text(s.label, color = Ux.Text2, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Text(s.away.toString(), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(56.design))
                    }
                }
            }
            if (show && loading) item { Text(D.loading, color = Ux.Text3, fontFamily = Manrope, fontSize = 17.spx) }
            // ── « Aussi sur » : les chaînes de l'utilisateur qui diffusent ce match ──
            if (m.state != MatchState.POST) {
                item { Text(D.sportMatchWatch, color = Ux.Accent, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.spx) }
                if (channels.isEmpty()) item { Text(D.sportMatchNoChannel, color = Ux.Text3, fontFamily = Manrope, fontSize = 17.spx) }
                else items(channels, key = { it.channel.id }) { e ->
                    val badges = SportMatching.badgesOf(e.channel)
                    Pill((if (e.channel.num > 0) "${e.channel.num} · " else "") + e.channel.title + if (badges.isNotEmpty()) "  [$badges]" else "", false) { onPlay(e.channel) }
                }
            }
            item { Pill(D.sportLgDone, false, onClose) }
        }
    }
}

// ───────────────────────── personnalisation ─────────────────────────

@Composable
private fun CustomizeDialog(D: DesignStrings, st: LeaguesState, vm: LeaguesViewModel, onClose: () -> Unit) {
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Scrim)) {
        val all = st.prefs.everything()
        LazyColumn(
            Modifier.widthIn(max = if (LocalTouch.current) 520.dp else 760.design).fillMaxWidth().heightIn(max = 780.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).focusGroup(),
            contentPadding = PaddingValues(28.design), verticalArrangement = Arrangement.spacedBy(10.design),
        ) {
            item { Text(D.sportLgCustomize, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 2) }
            item { SwitchPrefRow(D.sportNoSpoilerLabel, st.noSpoiler, hint = D.sportNoSpoilerHint) { vm.setNoSpoiler(it) } }
            items(all, key = { it.key }) { l ->
                val hidden = st.prefs.isHidden(l.key)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.design)) {
                    Text(l.name, color = if (hidden) Ux.Text3 else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Pill("↑", false) { vm.move(l.key, -1) }
                    Pill("↓", false) { vm.move(l.key, 1) }
                    Pill(if (hidden) D.sportLgShow else D.sportLgHide, false) { vm.setHidden(l.key, !hidden) }
                }
            }
            item { Pill(D.sportLgDone, false, onClose) }
        }
    }
}

// ───────────────────────── éléments communs ─────────────────────────

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(50), bg = if (selected) Ux.Cta else Ux.Card, focusedBg = Ux.Cta,
        focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.heightIn(min = 44.designT),
    ) { f ->
        Box(Modifier.padding(horizontal = 20.design, vertical = 6.design).heightIn(min = 40.designT), contentAlignment = Alignment.Center) {
            Text(label, color = if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

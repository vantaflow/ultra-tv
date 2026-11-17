package com.ultratv.tv.nativeapp.ui.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.sport.LeagueCatalog
import com.ultratv.tv.nativeapp.data.sport.LeagueMatch
import com.ultratv.tv.nativeapp.data.sport.MatchSheet
import com.ultratv.tv.nativeapp.data.sport.MatchState
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportHubLogic
import com.ultratv.tv.nativeapp.data.sport.SportMatching
import com.ultratv.tv.nativeapp.data.sport.SpoilerLogic
import com.ultratv.tv.nativeapp.data.sport.StandingRow
import com.ultratv.tv.nativeapp.data.sport.StandingZones
import com.ultratv.tv.nativeapp.data.sport.Zone
import com.ultratv.tv.nativeapp.i18n.sportProgramme
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.sportBack
import com.ultratv.tv.nativeapp.i18n.sportEventsHidden
import com.ultratv.tv.nativeapp.i18n.sportHidden
import com.ultratv.tv.nativeapp.i18n.sportLgEmpty
import com.ultratv.tv.nativeapp.i18n.sportLgLive
import com.ultratv.tv.nativeapp.i18n.sportLgTable
import com.ultratv.tv.nativeapp.i18n.sportMatchKeyMoments
import com.ultratv.tv.nativeapp.i18n.sportMatchNoChannel
import com.ultratv.tv.nativeapp.i18n.sportMatchRound
import com.ultratv.tv.nativeapp.i18n.sportMatchStats
import com.ultratv.tv.nativeapp.i18n.sportMatchWatch
import com.ultratv.tv.nativeapp.i18n.sportMyLeagues
import com.ultratv.tv.nativeapp.i18n.sportMyLeaguesHint
import com.ultratv.tv.nativeapp.i18n.sportNoEvents
import com.ultratv.tv.nativeapp.i18n.sportNoSpoilerHint
import com.ultratv.tv.nativeapp.i18n.sportNoSpoilerLabel
import com.ultratv.tv.nativeapp.i18n.sportShown
import com.ultratv.tv.nativeapp.i18n.sportSpoilerShowAll
import com.ultratv.tv.nativeapp.i18n.sportSpoilerShowScore
import com.ultratv.tv.nativeapp.i18n.sportTableHidden
import com.ultratv.tv.nativeapp.i18n.sportTblD
import com.ultratv.tv.nativeapp.i18n.sportTblForm
import com.ultratv.tv.nativeapp.i18n.sportTblGd
import com.ultratv.tv.nativeapp.i18n.sportTblL
import com.ultratv.tv.nativeapp.i18n.sportTblPlayed
import com.ultratv.tv.nativeapp.i18n.sportTblPts
import com.ultratv.tv.nativeapp.i18n.sportTblTeam
import com.ultratv.tv.nativeapp.i18n.sportTblW
import com.ultratv.tv.nativeapp.i18n.sportWatchHint
import com.ultratv.tv.nativeapp.i18n.sportZoneRel
import com.ultratv.tv.nativeapp.i18n.sportZoneUcl
import com.ultratv.tv.nativeapp.i18n.sportZoneUel
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GOLD = Color(0xFFF5C451)
private val BLUE = Color(0xFF7FA8FF)
private val INK = Color(0xFF0A0A0C)

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(28.design)).background(Ux.Card).padding(30.design), verticalArrangement = Arrangement.spacedBy(16.design)) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        content()
    }
}

// ───────────────────────── feuille de match (plein écran) ─────────────────────────

/**
 * Feuille de match (maquette Match.dc.html) : bandeau du score (« Afficher le score » en mode sans spoiler) puis trois
 * colonnes : temps forts, statistiques en barres, « Regarder sur vos chaînes » (meilleure qualité d'abord, OK lance la chaîne).
 * Téléphone : les trois blocs s'empilent.
 */
@Composable
internal fun MatchSheetScreen(
    D: DesignStrings, m: LeagueMatch, sheet: MatchSheet?, loading: Boolean, noSpoiler: Boolean, revealed: Boolean,
    channels: List<SportEntry>, onReveal: () -> Unit, onPlay: (ChannelEntity) -> Unit, onClose: () -> Unit,
    league: String? = null, programme: SportEntry? = null,
) {
    val touch = LocalTouch.current
    val show = !noSpoiler || revealed
    val locale = remember(D.lang) { D.lang.code.takeIf { it.length in 2..3 }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault() }
    val fmt = remember(locale) { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMHHmm"), locale) }
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Bg)) {
        val pad = if (touch) 16.dp else 72.design
        val body: @Composable ColumnScope.() -> Unit = {
            // ── Bandeau haut : retour, compétition · journée · lieu, état ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
                Text(D.sportBack, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1,
                    modifier = Modifier.border(1.dp, Ux.Line, RoundedCornerShape(10.design)).padding(horizontal = 12.design, vertical = 4.design))
                Text(
                    listOfNotNull(league, m.round?.let { D.sportMatchRound(it) }, sheet?.venue, m.startMs?.takeIf { m.state != MatchState.LIVE }?.let { fmt.format(Date(it)) }).joinToString(" · "),
                    color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (m.state == MatchState.LIVE) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                    Box(Modifier.size(12.design).clip(CircleShape).background(Ux.Err))
                    Text(listOfNotNull(D.sportLgLive, m.progress?.takeIf { show }?.let { "$it'" }).joinToString(" · "), color = Ux.Err, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1)
                }
            }
            // ── Score ──
            ScoreBanner(D, m, show, touch, onReveal)
            // ── Colonnes ──
            if (programme != null) {
                // Aucun score relié (compétition non couverte) : le programme du guide et le choix de la chaîne.
                if (touch) {
                    WatchPanel(D, m, channels, onPlay, Modifier.fillMaxWidth())
                    ProgrammePanel(D, programme, Modifier.fillMaxWidth())
                } else Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.design)) {
                    ProgrammePanel(D, programme, Modifier.weight(1f).fillMaxHeight())
                    WatchPanel(D, m, channels, onPlay, Modifier.weight(1f).fillMaxHeight())
                }
            } else if (touch) {
                Moments(D, m, sheet, loading, show, Modifier.fillMaxWidth())
                StatsPanel(D, sheet, loading, show, Modifier.fillMaxWidth())
                WatchPanel(D, m, channels, onPlay, Modifier.fillMaxWidth())
            } else Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.design)) {
                Moments(D, m, sheet, loading, show, Modifier.weight(1f).fillMaxHeight())
                StatsPanel(D, sheet, loading, show, Modifier.weight(1f).fillMaxHeight())
                WatchPanel(D, m, channels, onPlay, Modifier.width(560.design).fillMaxHeight())
            }
        }
        if (touch) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(16.design)) { body() }
        else Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = 44.design), verticalArrangement = Arrangement.spacedBy(30.design)) { body() }
    }
}

@Composable
private fun ScoreBanner(D: DesignStrings, m: LeagueMatch, show: Boolean, touch: Boolean, onReveal: () -> Unit) {
    val nameSp = if (touch) 26 else 46
    val crest = if (touch) 56 else 104
    val center: @Composable () -> Unit = {
        when {
            m.hasScore && show -> Text("${m.homeScore} – ${m.awayScore}", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = (if (touch) 56 else 112).spx, letterSpacing = (-2).spx, maxLines = 1)
            SpoilerLogic.hasSpoiler(m) -> FocusSurface(
                onClick = onReveal, shape = RoundedCornerShape(22.design), bg = Ux.Button, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 5.design,
            ) { f ->
                Text(D.sportSpoilerShowScore, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 26.spx, maxLines = 1, modifier = Modifier.padding(horizontal = 40.design, vertical = 26.design))
            }
            else -> Text("–", color = Ux.Muted, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = (if (touch) 56 else 112).spx, maxLines = 1)
        }
    }
    val home = m.home; val away = m.away
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.design)).background(Ux.Card).padding(horizontal = if (touch) 16.design else 56.design, vertical = if (touch) 20.design else 36.design),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 12.design else 48.design),
    ) {
        if (home == null || away == null) {
            Text(m.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = nameSp.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            return@Row
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 10.design else 28.design, Alignment.End)) {
            Text(home, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = nameSp.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
            TeamCrest(home, m.homeBadge, crest.design)
        }
        center()
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 10.design else 28.design)) {
            TeamCrest(away, m.awayBadge, crest.design)
            Text(away, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = nameSp.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun Moments(D: DesignStrings, m: LeagueMatch, sheet: MatchSheet?, loading: Boolean, show: Boolean, modifier: Modifier) {
    Panel(D.sportMatchKeyMoments, modifier) {
        val items = sheet?.timeline.orEmpty().filter { it.kind != "sub" }.take(24)
        when {
            !show -> Text(D.sportEventsHidden, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 33.spx)
            loading && items.isEmpty() -> Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            items.isEmpty() -> Text(D.sportNoEvents, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            else -> LazyColumn(Modifier.fillMaxWidth().let { if (LocalTouch.current) it.height(420.design) else it }, verticalArrangement = Arrangement.spacedBy(0.design)) {
                items(items) { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                        Text(t.minute?.let { "$it'" }.orEmpty(), color = Ux.Text2, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.width(56.design))
                        val dot = when (t.kind) { "goal" -> Ux.Accent; "card" -> if ((t.detail ?: "").contains("red", true)) Ux.Err else GOLD; else -> Ux.Text2 }
                        Box(Modifier.size(14.design).clip(if (t.kind == "goal") CircleShape else RoundedCornerShape(3.design)).background(dot))
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(t.player, t.assist?.let { "($it)" }).joinToString(" ").ifEmpty { t.kind.uppercase() }, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (t.home) m.home.orEmpty() else m.away.orEmpty(), color = Ux.Text2, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ux.Line.copy(alpha = 0.6f)))
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(D: DesignStrings, sheet: MatchSheet?, loading: Boolean, show: Boolean, modifier: Modifier) {
    Panel(D.sportMatchStats, modifier) {
        val stats = sheet?.stats.orEmpty().take(8)
        when {
            !show -> Text(D.sportEventsHidden, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 33.spx)
            loading && stats.isEmpty() -> Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            stats.isEmpty() -> Text(D.sportNoEvents, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            else -> Column(verticalArrangement = Arrangement.spacedBy(22.design)) {
                for (s in stats) {
                    val pa = SportHubLogic.sharePercent(s.home, s.away)
                    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.home.toString(), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 21.spx, maxLines = 1)
                            Text(s.label, color = Ux.Text2, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(horizontal = 10.design))
                            Text(s.away.toString(), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 21.spx, maxLines = 1)
                        }
                        Row(Modifier.fillMaxWidth().height(10.design), horizontalArrangement = Arrangement.spacedBy(6.design)) {
                            Box(Modifier.weight(pa.coerceAtLeast(1).toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(Ux.Accent))
                            Box(Modifier.weight((100 - pa).coerceAtLeast(1).toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(Ux.Muted2))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WatchPanel(D: DesignStrings, m: LeagueMatch, channels: List<SportEntry>, onPlay: (ChannelEntity) -> Unit, modifier: Modifier) {
    Panel(D.sportMatchWatch, modifier) {
        if (m.state == MatchState.POST || channels.isEmpty()) Text(D.sportMatchNoChannel, color = Ux.Text2, fontFamily = Manrope, fontSize = 20.spx, lineHeight = 30.spx)
        else Column(Modifier.focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(16.design)) {
            for (e in channels) ChannelButton(e.channel, onClick = { onPlay(e.channel) })
        }
        Spacer(Modifier.weight(1f, fill = false))
        Text(D.sportWatchHint, color = Ux.Text2, fontFamily = Manrope, fontSize = 18.spx, lineHeight = 27.spx)
    }
}

/** Programme du guide (feuille sans score relié) : titre, horaire et chaîne, résumé. */
@Composable
private fun ProgrammePanel(D: DesignStrings, e: SportEntry, modifier: Modifier) {
    Panel(D.sportProgramme, modifier) {
        Text(e.prog.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            "${com.ultratv.tv.nativeapp.ui.common.EpgClock.hm(e.prog.startMs)} – ${com.ultratv.tv.nativeapp.ui.common.EpgClock.hm(e.prog.endMs)} · ${e.channel.title}",
            color = Ux.Text2, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        e.prog.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 20.spx, lineHeight = 30.spx, maxLines = 10, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelButton(c: ChannelEntity, onClick: () -> Unit) {
    val q = SportHubLogic.qualityOf(c)
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.SurfaceDeep, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 5.design, modifier = Modifier.fillMaxWidth()) { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.design, vertical = 16.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            LogoBox(c.logo, c.title, Modifier.width(64.design).height(44.design), radius = 10, pad = 4, decodeSize = androidx.compose.ui.unit.DpSize(64.design, 44.design))
            Column(Modifier.weight(1f)) {
                Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 23.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(SportMatching.badgesOf(c).ifEmpty { null }, if (c.num > 0) "n° ${c.num}" else null).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (q.isNotEmpty()) Text(
                q, color = INK, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.spx, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(10.design)).background(when (q) { "4K" -> GOLD; "HD" -> BLUE; else -> Color(0xFFA9B4C8) }).padding(horizontal = 14.design, vertical = 6.design),
            )
        }
    }
}

// ───────────────────────── classement complet + « Mes ligues » ─────────────────────────

/** Actions du classement complet et de « Mes ligues » (le ViewModel en production, des espions en test). */
internal class LeagueActions(
    val revealTable: (String) -> Unit, val setHidden: (String, Boolean) -> Unit, val move: (String, Int) -> Unit, val setNoSpoiler: (Boolean) -> Unit,
) {
    companion object {
        fun of(vm: LeaguesViewModel) = LeagueActions(vm::revealTable, vm::setHidden, vm::move, vm::setNoSpoiler)
    }
}

private const val HANDLE = "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"

/**
 * Classement complet (maquette Standings.dc.html) : J, G, N, P, +/−, forme sur 5 matchs, points, zones (Ligue des champions,
 * Europa, relégation) avec légende ; à droite « Mes ligues » (↑ ↓ déplacer, OK afficher / masquer) et mode sans spoiler.
 * [customize] : ouvert depuis la pastille « Mes ligues » (le focus part dans le panneau).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StandingsScreen(D: DesignStrings, lg: LeaguesState, vm: LeagueActions, customize: Boolean, onClose: () -> Unit) {
    val touch = LocalTouch.current
    val key = lg.selected
    val league = key?.let { LeagueCatalog.of(it) }
    val focusMine = remember { FocusRequester() }
    LaunchedEffect(customize) { if (customize) runCatching { focusMine.requestFocus() } }
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Bg)) {
        val pad = if (touch) 16.dp else 72.design
        val table: @Composable (Modifier) -> Unit = { mod -> TablePane(D, lg, vm, key, league?.name, mod) }
        val mine: @Composable (Modifier) -> Unit = { mod -> MyLeagues(D, lg, vm, focusMine, mod) }
        if (touch) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(20.design)) {
            mine(Modifier.fillMaxWidth()); table(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxSize().padding(horizontal = pad, vertical = 44.design), horizontalArrangement = Arrangement.spacedBy(36.design)) {
            table(Modifier.weight(1f).fillMaxHeight()); mine(Modifier.width(560.design).fillMaxHeight())
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TablePane(D: DesignStrings, lg: LeaguesState, vm: LeagueActions, key: String?, leagueName: String?, modifier: Modifier) {
    val touch = LocalTouch.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.design)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.design)) {
            Text(D.sportLgTable, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = (if (touch) 32 else 48).spx, maxLines = 1)
            if (leagueName != null) Text(leagueName, color = Ux.TextOnLight, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 21.spx, maxLines = 1,
                modifier = Modifier.border(4.design, Ux.Accent, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Ux.Cta).padding(horizontal = 24.design, vertical = 12.design))
            val round = lg.table?.let { SportHubLogic.roundOf(it) }
            if (round != null) Text(D.sportMatchRound(round), color = Ux.Text2, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1)
        }
        val rows = lg.table
        val hidden = key != null && !SpoilerLogic.showTable(lg.noSpoiler, key, lg.revealedTables)
        when {
            key == null || (rows != null && rows.isEmpty()) -> Text(D.sportLgEmpty, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            hidden -> Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                Text(D.sportTableHidden, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
                SportPill(D.sportSpoilerShowAll, { vm.revealTable(key) })
            }
            rows == null -> Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
            else -> {
                val grouped = rows.any { it.group != null }
                Column(Modifier.then(if (touch) Modifier else Modifier.weight(1f)).clip(RoundedCornerShape(28.design)).background(Ux.Card).padding(horizontal = 24.design, vertical = 14.design)) {
                    TableHead(D, touch)
                    if (touch) Column { for (r in rows) FullRow(r, StandingZones.of(key, r.rank, rows.size, grouped), touch) }
                    else LazyColumn(Modifier.fillMaxSize().focusRestorer().focusGroup()) {
                        items(rows, key = { "${it.group}-${it.rank}-${it.team}" }) { r -> FullRow(r, StandingZones.of(key, r.rank, rows.size, grouped), touch) }
                    }
                }
                if (StandingZones.hasZones(key)) Row(horizontalArrangement = Arrangement.spacedBy(28.design)) {
                    if (StandingZones.hasEurope(key)) { Legend(Ux.Accent, D.sportZoneUcl); Legend(Color(0xFFF5A524), D.sportZoneUel) }
                    Legend(Color(0xFFF0505A), D.sportZoneRel)
                }
            }
        }
    }
}

@Composable
private fun Legend(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
        Box(Modifier.width(18.design).height(6.design).clip(RoundedCornerShape(3.design)).background(c))
        Text(text, color = Ux.Text2, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TableHead(D: DesignStrings, touch: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.design, vertical = 12.design), verticalAlignment = Alignment.CenterVertically) {
        Head("#", Modifier.width(56.design), TextAlign.Start)
        Head(D.sportTblTeam, Modifier.weight(1f), TextAlign.Start)
        Head(D.sportTblPlayed, Modifier.width(70.design))
        if (!touch) { Head(D.sportTblW, Modifier.width(70.design)); Head(D.sportTblD, Modifier.width(70.design)); Head(D.sportTblL, Modifier.width(70.design)) }
        Head(D.sportTblGd, Modifier.width(90.design))
        if (!touch) Head(D.sportTblForm, Modifier.width(200.design), TextAlign.Center)
        Head(D.sportTblPts, Modifier.width(80.design))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ux.Line))
}

/** Ligne du classement complet : focalisable (pour défiler à la télécommande), liseré de zone à gauche. */
@Composable
private fun FullRow(r: StandingRow, zone: Zone, touch: Boolean) {
    FocusSurface(onClick = {}, shape = RoundedCornerShape(12.design), bg = Color.Transparent, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.fillMaxWidth()) { f ->
        val c1 = if (f) Ux.TextOnLight else Ux.Text
        val c2 = if (f) Ux.OnFocus2 else Ux.Text2
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            ZoneBar(zone, Modifier.fillMaxHeight())
            Row(Modifier.weight(1f).padding(horizontal = 8.design, vertical = 11.design), verticalAlignment = Alignment.CenterVertically) {
                Text(r.rank?.toString().orEmpty(), color = c2, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 21.spx, maxLines = 1, modifier = Modifier.width(48.design))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                    TeamCrest(r.team, r.badge, 38.design)
                    Text(r.team, color = c1, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 21.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Num(r.played, c2, 70)
                if (!touch) { Num(r.win, c2, 70); Num(r.draw, c2, 70); Num(r.loss, c2, 70) }
                Text(gdText(r.goalDiff), color = c1, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(90.design))
                if (!touch) Box(Modifier.width(200.design), contentAlignment = Alignment.Center) { FormBadges(r.form) }
                Text(r.points?.toString().orEmpty(), color = c1, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 21.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(80.design))
            }
        }
    }
}

@Composable
private fun Num(v: Int?, color: Color, w: Int) =
    Text(v?.toString().orEmpty(), color = color, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(w.design))

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MyLeagues(D: DesignStrings, lg: LeaguesState, vm: LeagueActions, firstFocus: FocusRequester, modifier: Modifier) {
    val touch = LocalTouch.current
    Column(modifier.clip(RoundedCornerShape(28.design)).background(Ux.Card).padding(30.design), verticalArrangement = Arrangement.spacedBy(18.design)) {
        Text(D.sportMyLeagues, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1)
        Text(D.sportMyLeaguesHint, color = Ux.Text2, fontFamily = Manrope, fontSize = 19.spx, lineHeight = 27.spx)
        val all = lg.prefs.everything()
        val rowsMod = if (touch) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()
        val list: @Composable (androidx.compose.foundation.lazy.LazyItemScope.(com.ultratv.tv.nativeapp.data.sport.League, Int) -> Unit) = { l, i -> LeagueRow(D, l, i == 0, lg.prefs.isHidden(l.key), vm, if (i == 0) firstFocus else null) }
        if (touch) Column(rowsMod, verticalArrangement = Arrangement.spacedBy(10.design)) { all.forEachIndexed { i, l -> LeagueRow(D, l, i == 0, lg.prefs.isHidden(l.key), vm, if (i == 0) firstFocus else null) } }
        else LazyColumn(rowsMod.focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(10.design)) {
            items(all.size, key = { all[it].key }) { i -> list(all[i], i) }
        }
        SwitchPrefRow(D.sportNoSpoilerLabel, lg.noSpoiler, hint = D.sportNoSpoilerHint) { vm.setNoSpoiler(it) }
    }
}

@Composable
private fun LeagueRow(D: DesignStrings, l: com.ultratv.tv.nativeapp.data.sport.League, first: Boolean, hidden: Boolean, vm: LeagueActions, requester: FocusRequester?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
        FocusSurface(
            onClick = { vm.setHidden(l.key, !hidden) }, shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 4.design,
            modifier = Modifier.weight(1f).let { if (requester != null) it.focusRequester(requester) else it },
        ) { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.design, vertical = 14.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                val ink = if (f) Ux.TextOnLight else if (hidden) Ux.Muted else Ux.Text
                DIcon(HANDLE, 22.design, ink, strokeWidth = 2f)
                Text(l.name, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    if (hidden) D.sportHidden else D.sportShown, color = if (f) INK else if (hidden) Ux.Muted else Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.spx, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (f) Color(0xFFD5DCE8) else Ux.Button).padding(horizontal = 12.design, vertical = 4.design),
                )
            }
        }
        SportPill("↑", { vm.move(l.key, -1) })
        SportPill("↓", { vm.move(l.key, 1) })
    }
}

package com.ultratv.tv.nativeapp.ui.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.sport.FormLogic
import com.ultratv.tv.nativeapp.data.sport.LeagueCatalog
import com.ultratv.tv.nativeapp.data.sport.LeagueMatch
import com.ultratv.tv.nativeapp.data.sport.LiveCardModel
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportHubLogic
import com.ultratv.tv.nativeapp.data.sport.SportMatching
import com.ultratv.tv.nativeapp.data.sport.SpoilerLogic
import com.ultratv.tv.nativeapp.data.sport.StandingRow
import com.ultratv.tv.nativeapp.data.sport.StandingZones
import com.ultratv.tv.nativeapp.data.sport.TeamBadgeLogic
import com.ultratv.tv.nativeapp.data.sport.Zone
import com.ultratv.tv.nativeapp.data.sport.matchLineOf
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.homeLiveNow
import com.ultratv.tv.nativeapp.i18n.reminderOn
import com.ultratv.tv.nativeapp.i18n.sportAlsoOnChan
import com.ultratv.tv.nativeapp.i18n.sportEmptyLive
import com.ultratv.tv.nativeapp.i18n.sportEmptyUpcoming
import com.ultratv.tv.nativeapp.i18n.sportFullTable
import com.ultratv.tv.nativeapp.i18n.sportMyLeagues
import com.ultratv.tv.nativeapp.i18n.sportLgEmpty
import com.ultratv.tv.nativeapp.i18n.sportLgLive
import com.ultratv.tv.nativeapp.i18n.sportLgNotPaired
import com.ultratv.tv.nativeapp.i18n.sportLgStale
import com.ultratv.tv.nativeapp.i18n.sportLgTable
import com.ultratv.tv.nativeapp.i18n.sportLgUnavailable
import com.ultratv.tv.nativeapp.i18n.sportLiveCount
import com.ultratv.tv.nativeapp.i18n.sportMatchRound
import com.ultratv.tv.nativeapp.i18n.sportNav
import com.ultratv.tv.nativeapp.i18n.sportNoGuideBody
import com.ultratv.tv.nativeapp.i18n.sportNoGuideTitle
import com.ultratv.tv.nativeapp.i18n.sportNoSource
import com.ultratv.tv.nativeapp.i18n.sportNoSpoilerShort
import com.ultratv.tv.nativeapp.i18n.sportOkHint
import com.ultratv.tv.nativeapp.i18n.sportOpenSync
import com.ultratv.tv.nativeapp.i18n.sportProgramme
import com.ultratv.tv.nativeapp.i18n.sportRecord
import com.ultratv.tv.nativeapp.i18n.sportRemind
import com.ultratv.tv.nativeapp.i18n.sportSpoilerShowAll
import com.ultratv.tv.nativeapp.i18n.sportSubtitle
import com.ultratv.tv.nativeapp.i18n.sportTableHidden
import com.ultratv.tv.nativeapp.i18n.sportTblGd
import com.ultratv.tv.nativeapp.i18n.sportTblPlayed
import com.ultratv.tv.nativeapp.i18n.sportTblPts
import com.ultratv.tv.nativeapp.i18n.sportTblTeam
import com.ultratv.tv.nativeapp.i18n.sportUpcomingShort
import com.ultratv.tv.nativeapp.i18n.sportUpcomingSub
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.StateCard
import com.ultratv.tv.nativeapp.ui.design.Switch
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import java.util.Locale

/**
 * Ce que l'accueil Sport sait des ligues (scores, classement, mode sans spoiler) et comment agir dessus : rempli par
 * [SportScreen] depuis [LeaguesViewModel]. Les valeurs par défaut (aucune donnée) servent au rendu de test.
 */
@Immutable
internal data class HubState(
    val lg: LeaguesState = LeaguesState(),
    /** Pastille « Tout » active : directs et programmes de tout le guide. */
    val all: Boolean = true,
    val onAll: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onOpenMatch: (LeagueMatch) -> Unit = {},
    /** OK sur une carte « En direct » : ouvre toujours une feuille (match ou programme) avec le choix de la chaîne. */
    val onOpenCard: ((LiveCardModel) -> Unit)? = null,
    val onNoSpoiler: (Boolean) -> Unit = {},
    val onRevealTable: (String) -> Unit = {},
    val onFullTable: () -> Unit = {},
    val onCustomize: () -> Unit = {},
)

// ───────────────────────── éléments communs ─────────────────────────

private val CrestPalette = listOf(0xFF1E3A8A, 0xFF0EA5E9, 0xFFB91C1C, 0xFF991B1B, 0xFFCA8A04, 0xFF15803D, 0xFF1D4ED8, 0xFF7C3AED, 0xFF0F766E, 0xFF4B5563).map { Color(it) }

/** Initiales d'une équipe pour la pastille colorée : un mot = 3 premières lettres, plusieurs = initiales des 3 premiers mots. */
internal fun initialsOf(name: String?): String {
    val words = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(3).joinToString("") { it.first().uppercase() }
    }
}

/** Couleur stable d'une équipe (même nom, même couleur ; alias courants rapprochés). */
internal fun crestColor(name: String?): Color =
    CrestPalette[(SportMatching.canonTeam(name.orEmpty()).hashCode() and 0x7fffffff) % CrestPalette.size]

/** Pastille d'équipe (maquette) : écusson s'il est connu (Worker ou TheSportsDB), sinon initiales sur fond coloré. */
@Composable
internal fun TeamCrest(name: String?, url: String?, size: Dp) {
    val resolved = url ?: TeamBadgeLogic.keyOf(name)?.let { LocalTeamBadges.current[it] }
    var loaded by remember(resolved) { mutableStateOf(false) }
    var failed by remember(resolved) { mutableStateOf(false) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (resolved == null || failed || !loaded) {
            Box(Modifier.size(size).clip(CircleShape).background(crestColor(name)), contentAlignment = Alignment.Center) {
                Text(initialsOf(name), color = Color.White, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.34f).toInt().spx, maxLines = 1)
            }
        }
        if (resolved != null && !failed) {
            val ctx = LocalContext.current
            val request = remember(resolved) { coil3.request.ImageRequest.Builder(ctx).data(resolved).size(96, 96).build() }
            coil3.compose.AsyncImage(
                request, null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                onSuccess = { loaded = true }, onError = { failed = true },
            )
        }
    }
}

/** Puce « chaîne · qualité » : liseré doré (4K), bleu (HD) ou gris (SD / inconnu). Hauteur fixe, une seule ligne ; lisible sur carte focalisée. */
@Composable
internal fun ChanChip(c: ChannelEntity, fontSp: Int = 18, focused: Boolean = false, modifier: Modifier = Modifier) {
    val q = SportHubLogic.qualityOf(c)
    val bar = when (q) { "4K" -> Color(0xFFF5C451); "HD" -> Color(0xFF7FA8FF); else -> Ux.Text3 }
    val shape = RoundedCornerShape(10.design)
    Row(modifier.height(34.designT).clip(shape).background(if (focused) Ux.TextOnLight.copy(alpha = 0.12f) else Ux.Button), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.design).fillMaxHeight().background(bar))
        Text(
            if (q.isEmpty()) c.title else "${c.title} · $q", color = if (focused) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = fontSp.spx,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.design),
        )
    }
}

/** Puces des chaînes d'un événement (au plus [max]) ; une même chaîne n'apparaît qu'une fois par qualité. */
@Composable
internal fun RowScope.ChanChips(channels: List<SportEntry>, max: Int = 3, fontSp: Int = 18, focused: Boolean = false) {
    val shown = remember(channels, max) { channels.distinctBy { it.channel.title to SportHubLogic.qualityOf(it.channel) }.take(max) }
    for (e in shown) ChanChip(e.channel, fontSp, focused, Modifier.weight(1f, fill = false))
}

@Composable
private fun LeaguePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    FocusSurface(
        onClick = onClick, shape = shape, bg = if (selected) Ux.Cta else Ux.Card, focusedBg = Ux.Cta,
        focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.height(52.designT).let { if (selected) it.border(3.design, Ux.Accent, shape) else it },
    ) { f ->
        Box(Modifier.padding(horizontal = 26.design).height(52.designT), contentAlignment = Alignment.Center) {
            Text(label, color = if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 20.spx, maxLines = 1)
        }
    }
}

/** Rangée de pastilles : « Tout », ligues visibles du profil, « Mes ligues » (ordre et masquage). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LeaguePills(D: DesignStrings, hub: HubState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
        LazyRow(Modifier.weight(1f).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.design)) {
            item(key = "all") { LeaguePill(D.catAll, hub.all, hub.onAll) }
            items(hub.lg.visible, key = { it.key }) { l -> LeaguePill(l.name, !hub.all && l.key == hub.lg.selected) { hub.onSelect(l.key) } }
        }
        // « Mes ligues » reste visible au bout de la rangée, même quand les pastilles défilent.
        LeaguePill(D.sportMyLeagues, false, hub.onCustomize)
    }
}

@Composable
private fun SpoilerToggle(D: DesignStrings, on: Boolean, onChange: (Boolean) -> Unit) {
    FocusSurface(
        onClick = { onChange(!on) }, shape = RoundedCornerShape(50), bg = Color.Transparent, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 0.dp,
        modifier = Modifier.height(48.designT),
    ) { f ->
        Row(Modifier.padding(horizontal = 18.design).height(48.designT), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
            Text(D.sportNoSpoilerShort, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 21.spx, maxLines = 1)
            Switch(on, inverted = f)
        }
    }
}

@Composable
private fun SectionHead(title: String, sub: String?, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
        if (dot) Box(Modifier.size(12.design).clip(CircleShape).background(Ux.Err))
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1)
        if (sub != null) Text(sub, color = Ux.Text2, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────── carte « En direct » ─────────────────────────

/** Carte d'un match en direct (maquette) : ligue + minute, équipes et score, avancement, « Aussi sur N chaînes » et puces. */
@Composable
internal fun HubLiveCard(m: LiveCardModel, D: DesignStrings, noSpoiler: Boolean, revealed: Set<String>, modifier: Modifier, onClick: () -> Unit) {
    val show = m.match == null || SpoilerLogic.showScore(noSpoiler, m.match.id, revealed)
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(26.design), bg = Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.02f, ringWidth = 5.design, modifier = modifier) { f ->
        val c1 = if (f) Ux.TextOnLight else Ux.Text
        val c2 = if (f) Ux.OnFocus2 else Ux.Text2
        Column(Modifier.fillMaxSize().padding(26.design), verticalArrangement = Arrangement.spacedBy(20.design)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(m.league, color = c2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.design))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.design)) {
                    Box(Modifier.size(9.design).clip(CircleShape).background(Ux.Err))
                    Text(m.minute ?: D.homeLiveNow, color = Ux.Err, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1)
                }
            }
            if (m.home != null && m.away != null) {
                Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
                    LiveTeamRow(m.home, m.match?.homeBadge, if (show) m.homeScore else null, m.homeScore != null, c1)
                    LiveTeamRow(m.away, m.match?.awayBadge, if (show) m.awayScore else null, m.awayScore != null, c1)
                }
            } else Text(m.title, color = c1, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 26.spx, lineHeight = 32.spx, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (m.progress != null) {
                Box(Modifier.fillMaxWidth().height(6.design).clip(RoundedCornerShape(50)).background(if (f) Ux.TextOnLight.copy(alpha = 0.14f) else Ux.Line)) {
                    Box(Modifier.fillMaxWidth(m.progress.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Ux.Accent))
                }
            }
            // Chaînes en bas : toutes les cartes ont la même hauteur, quel que soit leur contenu.
            Spacer(Modifier.weight(1f))
            if (m.channels.size >= 2) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                Text(D.sportAlsoOnChan(m.channels.size), color = c2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            if (m.channels.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.design)) { ChanChips(m.channels, max = 2, fontSp = 16, focused = f) }
        }
    }
}

@Composable
private fun LiveTeamRow(name: String, badge: String?, score: Int?, hasScore: Boolean, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
        TeamCrest(name, badge, 46.design)
        Text(name, color = color, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(score?.toString() ?: if (hasScore) "•" else "–", color = color, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 38.spx, maxLines = 1, softWrap = false)
    }
}

// ───────────────────────── ligne « À venir » ─────────────────────────

/** Ligne « À venir » (maquette) : heure + jour, match ou émission, ligue / type, puces chaînes, « Me rappeler » (et « Enregistrer »). */
@Composable
private fun HubUpcomingRow(
    u: UpcomingItem, D: DesignStrings, locale: Locale, nowMs: Long, reminded: Boolean, st: SportState, stacked: Boolean,
    onRemind: () -> Unit, onRecord: () -> Unit,
) {
    val e = u.entry
    val line = remember(e.prog.title) { matchLineOf(e.prog.title) }
    val kind = remember(e.prog.title, e.prog.description) { kindOf(e) }
    val sub = line.competition ?: kind?.let { kindLabel(D, it) } ?: D.sportProgramme
    val title = if (line.home != null) "${line.home} – ${line.away}" else line.title
    val channels = remember(e, st.alsoOn) { SportMatching.orderChannels(listOf(e) + st.alsoOn[SportMatching.entryKey(e)].orEmpty()) }
    val timeBlock: @Composable () -> Unit = {
        Column {
            Text(EpgClock.hm(e.prog.startMs), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1)
            Text(shortDay(D, u.dayStartMs, nowMs, locale), color = Ux.Text2, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val titleBlock: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            Text(title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 25.spx, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Ux.Text2, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val actions: @Composable () -> Unit = {
        SportPill(if (reminded) D.reminderOn else D.sportRemind, onRemind, active = reminded, icon = BELL)
        SportPill(D.sportRecord, onRecord, icon = com.ultratv.tv.nativeapp.ui.design.Icons.Record)
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.design)).background(Ux.Card).padding(horizontal = 22.design, vertical = 16.design), verticalArrangement = Arrangement.spacedBy(12.design)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.design), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(96.design)) { timeBlock() }
                titleBlock(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.design)) { ChanChips(channels, max = 2, fontSp = 16) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.design)) { actions() }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.design)).background(Ux.Card).padding(horizontal = 26.design, vertical = 18.design),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design),
        ) {
            Box(Modifier.width(120.design)) { timeBlock() }
            titleBlock(Modifier.weight(1f))
            Row(Modifier.widthIn(max = 300.design), horizontalArrangement = Arrangement.spacedBy(8.design)) { ChanChips(channels, max = 2, fontSp = 17) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.design)) { actions() }
        }
    }
}

// ───────────────────────── classement court ─────────────────────────

@Composable
internal fun ZoneBar(zone: Zone, modifier: Modifier = Modifier) {
    val c = when (zone) { Zone.QUALIFIED -> Ux.Accent; Zone.EUROPA -> Color(0xFFF5A524); Zone.RELEGATION -> Color(0xFFF0505A); Zone.NONE -> Color.Transparent }
    Box(modifier.width(4.design).background(c))
}

/** Classement court de la ligue choisie (8 lignes), zone qualifiée marquée ; sans spoiler : masqué avec un bouton pour l'afficher. */
@Composable
private fun MiniTable(D: DesignStrings, hub: HubState, modifier: Modifier = Modifier) {
    val lg = hub.lg
    val key = lg.selected
    val league = key?.let { LeagueCatalog.of(it) }
    Column(modifier.clip(RoundedCornerShape(28.design)).background(Ux.Card).padding(28.design), verticalArrangement = Arrangement.spacedBy(18.design)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(D.sportLgTable, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1)
            val round = lg.table?.let { SportHubLogic.roundOf(it) }
            if (league != null) Text(listOfNotNull(league.name, round?.let { D.sportMatchRound(it) }).joinToString(" · "), color = Ux.Text2, fontFamily = Manrope, fontSize = 19.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(start = 10.design))
        }
        val hidden = key != null && !SpoilerLogic.showTable(lg.noSpoiler, key, lg.revealedTables)
        val rows = lg.table
        when {
            key == null -> Text(D.sportLgEmpty, color = Ux.Text2, fontFamily = Manrope, fontSize = 19.spx)
            hidden -> {
                Text(D.sportTableHidden, color = Ux.Text2, fontFamily = Manrope, fontSize = 19.spx)
                SportPill(D.sportSpoilerShowAll, { hub.onRevealTable(key) })
            }
            rows == null -> Text(if (lg.status == LeagueStatus.NOT_PAIRED) D.sportLgNotPaired else if (lg.status == LeagueStatus.UNAVAILABLE) D.sportLgUnavailable else D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 19.spx)
            rows.isEmpty() -> Text(D.sportLgEmpty, color = Ux.Text2, fontFamily = Manrope, fontSize = 19.spx)
            else -> Column {
                Row(Modifier.fillMaxWidth().padding(start = 6.design, end = 6.design, bottom = 10.design), verticalAlignment = Alignment.CenterVertically) {
                    Head("#", Modifier.width(40.design), TextAlign.Start)
                    Head(D.sportTblTeam, Modifier.weight(1f), TextAlign.Start)
                    Head(D.sportTblPlayed, Modifier.width(54.design)); Head(D.sportTblGd, Modifier.width(54.design)); Head(D.sportTblPts, Modifier.width(64.design))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ux.Line))
                val grouped = rows.any { it.group != null }
                for (r in rows.take(MINI_ROWS)) MiniRow(r, StandingZones.of(key, r.rank, rows.size, grouped))
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        if (rows != null && !hidden) SportPill(D.sportFullTable, hub.onFullTable)
        Text(D.sportOkHint, color = Ux.Text2, fontFamily = Manrope, fontSize = 18.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

internal const val MINI_ROWS = 8

/** Icône « cloche » du bouton « Me rappeler » (maquette). */
private const val BELL = "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0"

@Composable
internal fun Head(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) =
    Text(text, color = Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.spx, maxLines = 1, textAlign = align, modifier = modifier)

@Composable
private fun MiniRow(r: StandingRow, zone: Zone) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        ZoneBar(zone, Modifier.fillMaxHeight())
        Row(Modifier.weight(1f).padding(start = 2.design, end = 6.design, top = 10.design, bottom = 10.design), verticalAlignment = Alignment.CenterVertically) {
            Text(r.rank?.toString().orEmpty(), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 21.spx, maxLines = 1, modifier = Modifier.width(36.design))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
                TeamCrest(r.team, r.badge, 34.design)
                Text(r.team, color = Ux.Text, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(r.played?.toString().orEmpty(), color = Ux.Text2, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(54.design))
            Text(gdText(r.goalDiff), color = Ux.Text2, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(54.design))
            Text(r.points?.toString().orEmpty(), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 21.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(64.design))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ux.Line.copy(alpha = 0.5f)))
}

/** Hauteur commune des cartes « En direct » : match (deux équipes) + progression + « Aussi sur » + chaînes. */
private val LIVE_CARD_H get() = 340.design

internal fun gdText(v: Int?): String = when { v == null -> ""; v > 0 -> "+$v"; v < 0 -> "−${-v}"; else -> "0" }

// ───────────────────────── accueil TV ─────────────────────────

/** Données dérivées de l'accueil (calculées une fois par minute / changement de données, pas à chaque image). */
@Immutable
private data class HubLists(val live: List<LiveCardModel>, val upcoming: List<UpcomingItem>)

@Composable
private fun rememberHubLists(st: SportState, D: DesignStrings, hub: HubState, clock: State<Long>): HubLists {
    val minute = clock.value / 60_000L
    val leagueKey = if (hub.all) null else hub.lg.selected
    val league = hub.lg.selected?.let { LeagueCatalog.of(it) }
    val matches = if (hub.lg.status == LeagueStatus.OK) hub.lg.matches else emptyList()
    val programme = D.sportProgramme
    val liveAll = hub.lg.liveAll
    return remember(st, hub.lg.selected, hub.all, matches, liveAll, minute, programme) {
        val now = System.currentTimeMillis()
        val cards = SportHubLogic.liveCards(leagueKey, league?.name, league?.kind, matches, st.live, st.entries.ifEmpty { st.live + st.upcoming.flatMap { it.entries } }, st.alsoOn, now, liveAll) { e ->
            kindOf(e)?.let { kindLabel(D, it) } ?: programme
        }
        val days = SportHubLogic.filterUpcoming(leagueKey, matches, st.entries, st.upcoming)
        HubLists(cards, days.flatMap { d -> d.entries.map { UpcomingItem(d.dayStartMs, it) } })
    }
}

/**
 * Accueil Sport TV (maquette Main.dc.html) : en-tête, « Sans spoiler », pastilles de ligues ; à gauche « En direct » puis
 * « À venir » (matchs et émissions du guide) ; à droite le classement court de la ligue choisie. Sans ViewModel : testable.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SportTvContent(
    st: SportState, D: DesignStrings, clock: State<Long>, reminded: Set<String>,
    onPlay: (ChannelEntity) -> Unit, onRemind: (SportEntry) -> Unit, onRecord: (SportEntry) -> Unit, onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
    hub: HubState = HubState(),
) {
    val locale = remember(D.lang) { com.ultratv.tv.nativeapp.i18n.DisplayFormats.sportLocale(D.lang) }
    val lists = rememberHubLists(st, D, hub, clock)
    val showSections = !st.loading && st.hasSource
    val open: (LiveCardModel) -> Unit = { m ->
        val card = hub.onOpenCard
        when {
            card != null -> card(m)
            m.match != null -> hub.onOpenMatch(m.match)
            else -> m.channels.firstOrNull()?.let { onPlay(it.channel) }
        }
    }

    Column(
        modifier.fillMaxSize().background(Ux.Bg).padding(start = 48.design, end = 56.design, top = 40.design, bottom = 28.design),
        verticalArrangement = Arrangement.spacedBy(28.design),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.design)) {
            Text(D.sportNav, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 52.spx, letterSpacing = (-1).spx, maxLines = 1, softWrap = false)
            Text(D.sportSubtitle, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (showSections) SpoilerToggle(D, hub.lg.noSpoiler, hub.onNoSpoiler)
            Text(EpgClock.hm(clock.value), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 1)
        }
        when {
            st.loading -> Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx)
            !st.hasSource -> StateCard(D.sportNav, D.sportNoSource, SportIcons.Trophy, D.openSourceSettings, onGoSettings, Modifier.fillMaxWidth())
            else -> {
                LeaguePills(D, hub, Modifier.fillMaxWidth())
                if (hub.lg.stale && hub.lg.status == LeagueStatus.OK) Text(D.sportLgStale, color = Ux.Text3, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.design)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(22.design)) {
                        // ── En direct ──
                        SectionHead(D.sportLgLive, if (lists.live.isNotEmpty()) D.sportLiveCount(lists.live.size) else null, dot = true)
                        if (lists.live.isEmpty()) Text(D.sportEmptyLive, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx)
                        else LazyRow(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(20.design), contentPadding = PaddingValues(vertical = 8.design, horizontal = 6.design)) {
                            items(lists.live, key = { it.key }, contentType = { "live" }) { m ->
                                HubLiveCard(m, D, hub.lg.noSpoiler, hub.lg.revealed, Modifier.width(360.design).height(LIVE_CARD_H)) { open(m) }
                            }
                        }
                        // ── À venir ──
                        Spacer(Modifier.height(0.design))
                        SectionHead(D.sportUpcomingShort, D.sportUpcomingSub)
                        when {
                            !st.hasGuide -> StateCard(D.sportNoGuideTitle, D.sportNoGuideBody, SportIcons.Trophy, D.sportOpenSync, onGoSettings, Modifier.fillMaxWidth())
                            lists.upcoming.isEmpty() -> Text(D.sportEmptyUpcoming, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx)
                            else -> LazyColumn(Modifier.weight(1f, fill = false).focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(12.design), contentPadding = PaddingValues(bottom = 24.design)) {
                                items(lists.upcoming, key = { "u${entryKey(it.entry)}" }, contentType = { "upcoming" }) { u ->
                                    HubUpcomingRow(u, D, locale, clock.value, entryKey(u.entry) in reminded, st, stacked = false, onRemind = { onRemind(u.entry) }, onRecord = { onRecord(u.entry) })
                                }
                            }
                        }
                    }
                    MiniTable(D, hub, Modifier.width(520.design).fillMaxHeight())
                }
            }
        }
    }
}

// ───────────────────────── accueil tactile (une colonne) ─────────────────────────

/** Version empilée pour téléphone : une colonne, cartes pleine largeur, classement court en dernier. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SportTouchContent(
    st: SportState, D: DesignStrings, clock: State<Long>, reminded: Set<String>,
    onPlay: (ChannelEntity) -> Unit, onRemind: (SportEntry) -> Unit, onRecord: (SportEntry) -> Unit, onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
    hub: HubState = HubState(),
) {
    val locale = remember(D.lang) { com.ultratv.tv.nativeapp.i18n.DisplayFormats.sportLocale(D.lang) }
    val lists = rememberHubLists(st, D, hub, clock)
    val showSections = !st.loading && st.hasSource
    val open: (LiveCardModel) -> Unit = { m ->
        val card = hub.onOpenCard
        when {
            card != null -> card(m)
            m.match != null -> hub.onOpenMatch(m.match)
            else -> m.channels.firstOrNull()?.let { onPlay(it.channel) }
        }
    }
    LazyColumn(
        modifier.fillMaxSize().background(Ux.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 54.design),
        verticalArrangement = Arrangement.spacedBy(14.design),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(D.sportNav, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 48.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(D.sportSubtitle, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            st.loading -> item(key = "loading") { Text(D.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx) }
            !st.hasSource -> item(key = "nosource") { StateCard(D.sportNav, D.sportNoSource, SportIcons.Trophy, D.openSourceSettings, onGoSettings, Modifier.fillMaxWidth()) }
            else -> {
                if (showSections) item(key = "spoiler") { SpoilerToggle(D, hub.lg.noSpoiler, hub.onNoSpoiler) }
                item(key = "pills") { LeaguePills(D, hub, Modifier.fillMaxWidth()) }
                item(key = "live-h") { SectionHead(D.sportLgLive, if (lists.live.isNotEmpty()) D.sportLiveCount(lists.live.size) else null, dot = true) }
                if (lists.live.isEmpty()) item(key = "live-empty") { Text(D.sportEmptyLive, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx) }
                else items(lists.live, key = { it.key }, contentType = { "live" }) { m -> HubLiveCard(m, D, hub.lg.noSpoiler, hub.lg.revealed, Modifier.fillMaxWidth()) { open(m) } }
                item(key = "up-h") { SectionHead(D.sportUpcomingShort, D.sportUpcomingSub) }
                when {
                    !st.hasGuide -> item(key = "noguide") { StateCard(D.sportNoGuideTitle, D.sportNoGuideBody, SportIcons.Trophy, D.sportOpenSync, onGoSettings, Modifier.fillMaxWidth()) }
                    lists.upcoming.isEmpty() -> item(key = "up-empty") { Text(D.sportEmptyUpcoming, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx) }
                    else -> items(lists.upcoming, key = { "u${entryKey(it.entry)}" }, contentType = { "upcoming" }) { u ->
                        HubUpcomingRow(u, D, locale, clock.value, entryKey(u.entry) in reminded, st, stacked = true, onRemind = { onRemind(u.entry) }, onRecord = { onRecord(u.entry) })
                    }
                }
                item(key = "table") { MiniTable(D, hub, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** Les cinq derniers résultats d'une équipe (carrés W vert / D gris / L rouge de la maquette). */
@Composable
internal fun FormBadges(form: String?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.design)) {
        for (c in FormLogic.parse(form)) {
            val bg = when (c) { 'W' -> Ux.Accent; 'L' -> Color(0xFFF0505A); else -> Ux.Muted }
            Box(Modifier.size(30.design).clip(RoundedCornerShape(8.design)).background(bg), contentAlignment = Alignment.Center) {
                Text(c.toString(), color = if (c == 'W') Ux.OnAccent else Color(0xFF0A0F1E), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 14.spx, maxLines = 1)
            }
        }
    }
}

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.recording.ScheduleResult
import com.ultratv.tv.nativeapp.data.sport.LeagueChannels
import com.ultratv.tv.nativeapp.data.sport.SportDay
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportKind
import com.ultratv.tv.nativeapp.data.sport.SportLogic
import com.ultratv.tv.nativeapp.data.sport.SportMatching
import com.ultratv.tv.nativeapp.data.sport.matchLineOf
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.homeLiveNow
import com.ultratv.tv.nativeapp.i18n.recNoSpace
import com.ultratv.tv.nativeapp.i18n.recNothing
import com.ultratv.tv.nativeapp.i18n.recScheduled
import com.ultratv.tv.nativeapp.i18n.remindSet
import com.ultratv.tv.nativeapp.i18n.reminderOn
import com.ultratv.tv.nativeapp.i18n.sportAlsoOn
import com.ultratv.tv.nativeapp.i18n.sportAlsoOnN
import com.ultratv.tv.nativeapp.i18n.sportChannels
import com.ultratv.tv.nativeapp.i18n.sportEmptyLive
import com.ultratv.tv.nativeapp.i18n.sportEmptyUpcoming
import com.ultratv.tv.nativeapp.i18n.sportFromGuide
import com.ultratv.tv.nativeapp.i18n.sportHomeRow
import com.ultratv.tv.nativeapp.i18n.sportKindBasket
import com.ultratv.tv.nativeapp.i18n.sportKindCombat
import com.ultratv.tv.nativeapp.i18n.sportKindFootball
import com.ultratv.tv.nativeapp.i18n.sportKindMotor
import com.ultratv.tv.nativeapp.i18n.sportKindRugby
import com.ultratv.tv.nativeapp.i18n.sportKindTennis
import com.ultratv.tv.nativeapp.i18n.sportLiveNow
import com.ultratv.tv.nativeapp.i18n.sportModeLeagues
import com.ultratv.tv.nativeapp.i18n.sportModeTv
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
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LightPalette
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
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

/**
 * Rubrique Sport (facultative) : accueil unifié (pastilles de ligues, « En direct », « À venir », classement court)
 * tiré du guide et des catégories de la source de l'utilisateur (aucun service externe). OK sur un
 * direct lance la chaîne avec le même contexte que le Guide et le Direct (élément LIVE + file de zapping).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SportScreen(
    onPlay: (url: String, title: String) -> Unit,
    onGoSettings: () -> Unit,
    vm: SportViewModel = hiltViewModel(),
    lvm: LeaguesViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val st by vm.state.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val clock = rememberEpgClock()
    val touch = LocalTouch.current
    var pinFor by remember { mutableStateOf<ChannelEntity?>(null) }
    // Rappels posés pendant cette visite : le bouton passe à « Rappel activé » (pas de doublon au second appui).
    var reminded by remember { mutableStateOf(emptySet<String>()) }

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
    val onRemind: (SportEntry) -> Unit = { e ->
        val key = entryKey(e)
        if (key !in reminded) {
            reminded = reminded + key
            vm.addReminder(e.channel, e.prog); askNotifications(); Toaster.ok(D.remindSet)
        }
    }
    val onRecord: (SportEntry) -> Unit = { e ->
        vm.record(e.channel, e.prog) { r ->
            when (r) {
                is ScheduleResult.Scheduled -> Toaster.ok(D.recScheduled(r.count))
                ScheduleResult.NoSpace -> Toaster.err(D.recNoSpace)
                is ScheduleResult.Conflict -> Toaster.err(com.ultratv.tv.nativeapp.i18n.recConflict(D, r.maxConnections))
                ScheduleResult.NothingToSchedule -> Toaster.show(D.recNothing)
            }
        }
    }

    // Accueil unifié : directs et programmes du guide, scores et classement de la ligue choisie (« Tout » = tout le guide).
    var all by rememberSaveable { mutableStateOf(true) }
    var fullTable by rememberSaveable { mutableStateOf(false) }
    var customizing by rememberSaveable { mutableStateOf(false) }
    val lg by lvm.state.collectAsStateWithLifecycle()
    val sheet by lvm.sheet.collectAsStateWithLifecycle()
    val hub = HubState(
        lg = lg, all = all,
        onAll = { all = true }, onSelect = { k -> all = false; lvm.select(k) },
        onOpenMatch = { m -> lvm.openMatch(m) }, onOpenCard = lvm::openCard, onNoSpoiler = lvm::setNoSpoiler, onRevealTable = lvm::revealTable,
        onFullTable = { fullTable = true }, onCustomize = { customizing = true },
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalTeamBadges provides badges) {
        Box(Modifier.fillMaxSize().background(Ux.Bg)) {
            if (touch) SportTouchContent(st, D, clock, reminded, play, onRemind, onRecord, onGoSettings, hub = hub)
            else SportTvContent(st, D, clock, reminded, play, onRemind, onRecord, onGoSettings, hub = hub)
        }
        // Feuille de match (plein écran) : OK sur un match du direct ou de « À venir ».
        sheet?.let { s ->
            val live = lg.matches.firstOrNull { it.id == s.match.id } ?: lg.liveAll.values.firstNotNullOfOrNull { l -> l.firstOrNull { it.id == s.match.id } } ?: s.match
            MatchSheetScreen(
                D, live, s.sheet, s.loading, lg.noSpoiler, s.match.id in lg.revealed,
                channels = remember(live, st.entries, s.channels) {
                    com.ultratv.tv.nativeapp.data.sport.SportMatching.orderChannels(
                        (s.channels + LeagueChannels.channelsFor(live, st.entries)).distinctBy { it.channel.id },
                    )
                },
                league = s.league, programme = s.programme,
                onReveal = { lvm.reveal(s.match.id) }, onPlay = { c -> lvm.closeMatch(); play(c) }, onClose = lvm::closeMatch,
            )
        }
        if (fullTable || customizing) StandingsScreen(D, lg, LeagueActions.of(lvm), customize = customizing, onClose = { fullTable = false; customizing = false })
    }

    pinFor?.let { c -> PinPromptDialog(title = c.title, onUnlocked = { pinFor = null; vm.play(c) { onPlay(c.streamUrl, c.title) } }, onCancel = { pinFor = null }) }
}

internal fun entryKey(e: SportEntry) = "${e.channel.id}-${e.prog.startMs}"

/** Nombre d'autres chaînes qui diffusent le même événement que [e] (0 = aucune). */
private fun alsoCount(st: SportState, e: SportEntry): Int = st.alsoOn[SportMatching.entryKey(e)]?.size ?: 0

/** Lignes du dialogue « Aussi sur » : numéro, chaîne et badges (qualité · langue) ; libellés uniques (clé de liste). */
internal fun alsoOnOptions(others: List<SportEntry>): List<Pair<Long, String>> {
    val seen = HashSet<String>()
    return others.map { e ->
        val c = e.channel
        val badges = SportMatching.badgesOf(c)
        var label = (if (c.num > 0) "${c.num} · " else "") + c.title + (if (badges.isNotEmpty()) "  [$badges]" else "")
        var n = 2
        while (!seen.add(label)) label = "${c.title} #${n++}"
        c.id to label
    }
}

internal fun kindLabel(D: DesignStrings, k: SportKind): String = when (k) {
    SportKind.FOOTBALL -> D.sportKindFootball
    SportKind.BASKET -> D.sportKindBasket
    SportKind.TENNIS -> D.sportKindTennis
    SportKind.COMBAT -> D.sportKindCombat
    SportKind.MOTOR -> D.sportKindMotor
    SportKind.RUGBY -> D.sportKindRugby
}

internal fun kindOf(e: SportEntry): SportKind? = SportKind.ofProgramme(e.prog.title, e.prog.description)

/** Un programme à venir avec son jour (liste à plat de « À venir »). */
internal data class UpcomingItem(val dayStartMs: Long, val entry: SportEntry)

/** Jour court d'une ligne « À venir » : « Ce soir »-style de la maquette → Aujourd'hui / Demain / « sam. 14 ». */
internal fun shortDay(D: DesignStrings, dayStartMs: Long, nowMs: Long, locale: Locale): String = when (SportLogic.dayOffset(dayStartMs, nowMs)) {
    0 -> D.sportToday
    1 -> D.sportTomorrow
    else -> SimpleDateFormat("EEE d", locale).format(Date(dayStartMs)).replaceFirstChar { it.titlecase(locale) }
}

/** Pastille d'action : repos = bouton, [active] (rappel posé) = teinte « en cours » ; focus = fond de focus. */
@Composable
internal fun SportPill(label: String, onClick: () -> Unit, active: Boolean = false, icon: String? = null) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(50), bg = if (active) Ux.NowTint else Ux.Button, focusedBg = Ux.Cta,
        focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.height(44.designT),
    ) { f ->
        Row(Modifier.padding(horizontal = 18.design).height(44.designT), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.design)) {
            val ink = if (f) Ux.TextOnLight else if (active) Ux.AccentInk else Ux.Text
            if (icon != null) DIcon(icon, 18.design, ink, strokeWidth = 2.2f)
            Text(label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1)
        }
    }
}

// ───────────────────────── Cartes (TV, tactile et accueil) ─────────────────────────

/** Texte de la pastille d'une carte « direct » : minute de jeu pour les sports à minutes, sinon « En direct ». */
private fun liveBadgeText(D: DesignStrings, e: SportEntry, kind: SportKind?, nowMs: Long): String {
    // Pas de minute de jeu : le début d'un programme du guide n'est pas le coup d'envoi (avant-match, plateau),
    // la minute calculée était fausse (« 109' » pour un match en cours). La progression du programme reste sur la carte.
    return D.homeLiveNow
}

/** Carte d'un direct (maquette) : compétition + minute, équipes, logo · numéro · nom de la chaîne, progression. */
@Composable
internal fun SportLiveCard(e: SportEntry, clock: State<Long>, width: Dp, alsoCount: Int = 0, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val D = LocalDs.current
    val c = e.channel
    val line = remember(e.prog.title) { matchLineOf(e.prog.title) }
    val kind = remember(e.prog.title, e.prog.description) { kindOf(e) }
    val comp = (line.competition ?: kind?.let { kindLabel(D, it) } ?: EpgClock.range(e.prog.startMs, e.prog.endMs)).uppercase()
    // Sur le fond de focus (clair en sombre, encre en clair), l'intitulé reprend l'accent de l'autre palette.
    val focusedComp = if (Ux.palette.light) DarkPalette.accentText else LightPalette.accentText
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(24.design), bg = Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.03f, ringWidth = 0.dp,
        modifier = Modifier.width(width).height(250.designT),
    ) { f ->
        Column(Modifier.fillMaxSize().padding(20.design), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(comp, color = if (f) focusedComp else Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.spx, letterSpacing = 1.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.design))
                Text(
                    liveBadgeText(D, e, kind, clock.value), color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.spx, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Ux.Accent).padding(horizontal = 10.design, vertical = 4.design),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
                val teamColor = if (f) Ux.TextOnLight else Ux.Text
                if (line.home != null) {
                    TeamLine(line.home, teamColor)
                    TeamLine(line.away.orEmpty(), teamColor)
                } else Text(line.title, color = teamColor, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.spx, lineHeight = 32.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                LogoBox(c.logo, c.title, Modifier.width(40.design).height(40.design), radius = 11, pad = 4, bg = if (f) Color(0xFFE4E4E7) else Ux.Tile, decodeSize = DpSize(40.design, 40.design))
                Text(
                    (if (c.num > 0 && com.ultratv.tv.nativeapp.ui.common.LocalChannelDisplay.current.showNumbers) "${c.num} · " else "") + c.title,
                    color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                // Appui long : les autres chaînes du même événement (« Aussi sur »).
                if (alsoCount > 0) Text(D.sportAlsoOnN(alsoCount), color = if (f) Ux.OnFocus2 else Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.spx, maxLines = 1)
            }
            EpgProgressBar(e.prog.startMs, e.prog.endMs, clock, heightDp = 4f, track = if (f) Ux.TextOnLight.copy(alpha = 0.12f) else Ux.Text.copy(alpha = 0.10f))
        }
    }
}

/** Nom d'équipe d'une carte « direct », précédé de son écusson s'il est connu. */
@Composable
private fun TeamLine(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
        TeamBadge(name, 32.design)
        Text(name, color = color, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Les deux écussons d'une rencontre (liste « À venir ») ; rien si aucun n'est connu. */
@Composable
private fun TeamBadgePair(home: String?, away: String?, size: Dp) {
    val badges = LocalTeamBadges.current
    val known = listOf(home, away).any { n -> com.ultratv.tv.nativeapp.data.sport.TeamBadgeLogic.keyOf(n)?.let { it in badges } == true }
    if (!known) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.design), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size)) { TeamBadge(home, size) }
        Box(Modifier.size(size)) { TeamBadge(away, size) }
    }
}

/**
 * Écusson d'une équipe (TheSportsDB via le Worker), décodé petit et sans animation. Inconnu, pas encore résolu ou image en
 * échec : rien n'est affiché.
 */
@Composable
internal fun TeamBadge(teamName: String?, size: Dp) {
    val key = com.ultratv.tv.nativeapp.data.sport.TeamBadgeLogic.keyOf(teamName) ?: return
    val url = LocalTeamBadges.current[key] ?: return
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    val ctx = LocalContext.current
    val request = remember(url) { coil3.request.ImageRequest.Builder(ctx).data(url).size(96, 96).build() }
    coil3.compose.AsyncImage(
        model = request, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = Modifier.size(size), onError = { failed = true },
    )
}

/** Carte d'une chaîne de sport : logo, nom, programme en cours si on le connaît. */
@Composable
private fun SportChannelCard(c: ChannelEntity, now: String?, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.03f, ringWidth = 0.dp,
        modifier = modifier.height(88.designT),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(14.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
            LogoBox(c.logo, c.title, Modifier.width(48.design).height(48.design), radius = 13, pad = 4, bg = if (f) Color(0xFFE4E4E7) else Ux.Tile, decodeSize = DpSize(48.design, 48.design))
            Column(Modifier.weight(1f)) {
                Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) Text(now, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 14.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────── Accueil ─────────────────────────

/** Rangée « Sport en direct » de l'accueil TV (n'existe que si la rubrique est activée et qu'un direct de sport est en cours). */
@Composable
fun SportHomeSection(rowModifier: Modifier, contentPadding: PaddingValues, onPlayChannel: (ChannelEntity) -> Unit, onSeeAll: () -> Unit, vm: SportViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    if (st.live.isEmpty()) return
    val D = LocalDs.current
    val clock = rememberEpgClock()
    androidx.compose.runtime.CompositionLocalProvider(LocalTeamBadges provides badges) { Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
        Text(D.sportHomeRow, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LazyRow(rowModifier, horizontalArrangement = Arrangement.spacedBy(18.design), contentPadding = contentPadding) {
            items(st.live.take(HOME_MAX), key = { "h${it.channel.id}" }, contentType = { "sport" }) { e -> SportLiveCard(e, clock, 408.design) { onPlayChannel(e.channel) } }
            item(key = "see-all", contentType = "see-all") {
                FocusSurface(onClick = onSeeAll, shape = RoundedCornerShape(24.design), bg = Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.03f, ringWidth = 0.dp, modifier = Modifier.width(220.design).height(250.designT)) { f ->
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(D.seeAll, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(10.design))
                        DIcon(Icons.Arrow, 26.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.4f)
                    }
                }
            }
        }
    } }
}

private const val HOME_MAX = 12

package com.ultratv.tv.nativeapp.ui.player.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.LiveBadge
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.ControlPill
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.mobile.PlayerPill
import com.ultratv.tv.nativeapp.ui.mobile.PlayerRoundButton

/** Chaîne regardée : logo (sinon son numéro en pastille), numéro et nom. */
data class OsdChannelInfo(val logo: String?, val number: Int?, val name: String, val badge: String? = null, val quality: String? = null, val hdr: String? = null)

/** Barre de progression de l'étage haut : heures de début / fin, avancement 0..1, « reste 34 min ». */
data class OsdProgress(
    val startLabel: String, val endLabel: String, val fraction: Float, val remainingLabel: String? = null,
    /** Fenêtre du différé : repère « DIRECT » en bout de barre et poignée. */
    val directMark: String? = null,
)

/** Programme en cours (guide) ; null = pas de guide : le nom de la chaîne sert de titre. */
data class OsdProgrammeInfo(
    val title: String, val description: String?, val timeRange: String?, val progress: OsdProgress?, val thenLine: String?,
)

/** Pastille d'état : « EN DIRECT » ou « EN DIFFÉRÉ · −12:40 » ([timeshift]). */
data class OsdBadge(val text: String, val timeshift: Boolean)

/** Chaîne récente (historique du profil). [key] sert à retrouver la chaîne côté lecteur. */
data class OsdRecent(val key: String, val logo: String?, val number: Int?, val name: String, val now: String?)

/** Tout ce que l'OSD affiche. Pur : aucun accès au lecteur, à la base ni aux ressources. */
data class LiveOsdState(
    val channel: OsdChannelInfo,
    val programme: OsdProgrammeInfo?,
    val badge: OsdBadge,
    val clock: String,
    val actions: List<OsdAction>,
    val recents: List<OsdRecent>,
    val recentsTitle: String,
    /** OSD complet (récents + barre) ; faux = simple bandeau d'information (étage haut seul + [hint]). */
    val expanded: Boolean = true,
    val hint: String? = null,
    /** Interface v2 (TV) : ruban de la soirée (vide = masqué), pastille Favori, mots traduits (« MAINTENANT », « RÉCENTES »). */
    val ribbon: List<OsdRibbonSlot> = emptyList(),
    val favorite: Boolean = false,
    val nowWord: String = "",
    val favoriteWord: String = "",
    val recentsShort: String = "",
    /** Carte « ENSUITE » (null = masquée) et ses mots traduits. */
    val next: OsdNextInfo? = null,
    val nextWord: String = "",
    val remindWord: String = "",
    val recordWord: String = "",
)

/** Cibles de focus de l'OSD : premier bouton de la barre, première carte des récents. */
class LiveOsdFocus {
    val bar = FocusRequester()
    val recents = FocusRequester()
}

private val PILL_BG = Color(0xF22C2C34)
private val PILL_EDGE = Color(0x38FFFFFF)

/** Tailles TV (maquette 1920, px de conception) ou tactile (dp) selon le mode. */
private class Sz(val touch: Boolean) {
    fun d(px: Int, touchDp: Int): Dp = if (touch) touchDp.dp else px.design
    fun s(px: Int, touchSp: Int): TextUnit = if (touch) touchSp.sp else px.spx
}

/**
 * OSD du Direct en trois étages (voir l'issue du lot « OSD Direct ») :
 *  1. haut : chaîne, programme (titre, description, horaires, progression), « Ensuite », horloge, pastille d'état ;
 *  2. milieu : « Chaînes récentes » (OK = zapper) ;
 *  3. bas : barre de contrôles défilante, un appui par fonction, puis les raccourcis (Ma liste, Films…).
 * Pur : reçoit un état et des callbacks. Télécommande : le focus va d'abord à la barre ([initialStage]) ; Haut passe aux
 * récents, Bas revient à la barre (recherche de focus de Compose, l'étage du haut n'est pas focalisable). Tactile : même
 * contenu, étage haut en haut, récents et pilules défilantes en bas.
 *
 * @param onStage appelé quand le focus entre dans un étage (sert à Retour : récents → barre → fermeture)
 * @param onBack tactile : bouton de retour de l'étage haut (null = pas de bouton)
 * @param topEnd tactile : contenu additionnel à droite de l'étage haut (ex. verrou d'orientation)
 */
@Composable
fun LiveOsd(
    state: LiveOsdState,
    focus: LiveOsdFocus,
    onAction: (OsdActionId) -> Unit,
    onRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialStage: OsdStage = OsdStage.CONTROLS,
    onStage: (OsdStage) -> Unit = {},
    backDescription: String = "",
    onBack: (() -> Unit)? = null,
    topEnd: (@Composable () -> Unit)? = null,
    onNextAction: (OsdNextAction) -> Unit = {},
) {
    val touch = LocalTouch.current
    val sz = Sz(touch)
    if (state.expanded) LaunchedEffect(Unit) {
        val target = if (initialStage == OsdStage.RECENTS && state.recents.isNotEmpty()) focus.recents else focus.bar
        // Les éléments des LazyRow ne sont composés qu'à la mesure : la cible n'existe pas encore au premier essai.
        repeat(30) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    // TV : nouvelle disposition (OsdTv.kt), toujours sur la palette sombre (la surcouche est posée sur la vidéo).
    if (!touch) {
        androidx.compose.runtime.CompositionLocalProvider(com.ultratv.tv.nativeapp.ui.design.LocalUxPalette provides com.ultratv.tv.nativeapp.ui.design.DarkPalette) {
            Box(modifier.fillMaxSize()) { TvOsd(state, focus, onAction, onRecent, onStage, onNextAction) }
        }
        return
    }
    Box(modifier.fillMaxSize()) {
        TopStage(state, sz, backDescription, onBack, topEnd, Modifier.align(Alignment.TopStart))
        if (state.expanded) Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE60A0A0C)))).padding(top = sz.d(60, 24)),
            ) {
                if (state.recents.isNotEmpty()) RecentsRow(state, focus, sz, onRecent, onStage)
                ControlBar(state, focus, sz, onAction, onStage)
            }
        } else if (state.hint != null && !touch) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                state.hint, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1,
                modifier = Modifier.padding(bottom = 54.design).clip(RoundedCornerShape(30.design)).background(Color(0xCC0A0A0C)).padding(horizontal = 28.design, vertical = 14.design),
            )
        }
    }
}

// ───────────────────────── Étage haut ─────────────────────────

@Composable
private fun TopStage(state: LiveOsdState, sz: Sz, backDescription: String, onBack: (() -> Unit)?, topEnd: (@Composable () -> Unit)?, modifier: Modifier) {
    val ch = state.channel
    val prog = state.programme
    val bg = if (sz.touch) Color(0xB80A0A0C) else Color(0xD10A0A0C)
    Row(
        modifier.fillMaxWidth().heightIn(min = sz.d(200, 0)).background(bg)
            .padding(horizontal = sz.d(96, 12), vertical = sz.d(40, 8)),
        horizontalArrangement = Arrangement.spacedBy(sz.d(32, 12)),
        verticalAlignment = Alignment.Top,
    ) {
        if (sz.touch && onBack != null) PlayerRoundButton(com.ultratv.tv.nativeapp.ui.mobile.MobileIcons.Back, backDescription, onBack)
        ChannelMark(ch, sz)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sz.d(8, 3))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sz.d(14, 8))) {
                if (state.badge.timeshift) Text(
                    state.badge.text, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = sz.s(22, 11), letterSpacing = 1.2.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(sz.d(8, 4))).background(Ux.Surface2).padding(horizontal = sz.d(14, 8), vertical = sz.d(6, 2)),
                ) else LiveBadge(state.badge.text)
                // Un seul titre : avec un programme du guide la chaîne passe en petit ; sans guide son nom EST le titre.
                if (prog != null) Text(
                    channelLine(ch), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = sz.s(22, 12), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                ch.badge?.let { b ->
                    Text(b, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = sz.s(22, 11),
                        modifier = Modifier.clip(RoundedCornerShape(sz.d(6, 4))).background(Ux.Surface2).padding(horizontal = sz.d(8, 6), vertical = sz.d(2, 1)))
                }
            }
            Text(
                prog?.title ?: ch.name, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = sz.s(44, 18), lineHeight = sz.s(48, 22),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            prog?.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = sz.s(24, 12), lineHeight = sz.s(32, 16), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = sz.d(1200, 600)))
            }
            prog?.progress?.let { p -> ProgressLine(prog.timeRange, p, sz) }
            prog?.thenLine?.let {
                Text(it, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = sz.s(24, 12), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sz.d(16, 8)), verticalAlignment = Alignment.Top) {
            Text(state.clock, color = Color(0xFFE4E4E7), fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = sz.s(36, 16), maxLines = 1)
            if (sz.touch) topEnd?.invoke()
        }
    }
}

private fun channelLine(ch: OsdChannelInfo): String = listOfNotNull(ch.number?.takeIf { it > 0 }?.toString(), ch.name).joinToString(" · ")

/** Logo de la chaîne ; sans logo, son numéro (ou ses initiales) dans une pastille. */
@Composable
private fun ChannelMark(ch: OsdChannelInfo, sz: Sz) {
    if (!ch.logo.isNullOrBlank()) {
        LogoBox(ch.logo, ch.name, Modifier.width(sz.d(168, 64)).height(sz.d(104, 44)), radius = 14, pad = 8, bg = Color(0x33FFFFFF))
    } else {
        Box(
            Modifier.width(sz.d(168, 64)).height(sz.d(104, 44)).clip(RoundedCornerShape(sz.d(14, 8))).background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            val text = ch.number?.takeIf { it > 0 }?.toString() ?: ch.name.take(2).uppercase()
            Text(text, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = sz.s(48, 20), maxLines = 1)
        }
    }
}

/** « 20:00 – 21:30 », barre de progression, « reste 34 min ». */
@Composable
private fun ProgressLine(range: String?, p: OsdProgress, sz: Sz) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sz.d(20, 8)), modifier = Modifier.widthIn(max = sz.d(1100, 600))) {
        val left = range ?: listOf(p.startLabel, p.endLabel).filter { it.isNotEmpty() }.joinToString(" – ")
        if (left.isNotEmpty()) Text(left, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = sz.s(22, 11), maxLines = 1, softWrap = false)
        BoxWithConstraints(Modifier.weight(1f).height(sz.d(28, 12))) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(sz.d(10, 4)).clip(RoundedCornerShape(sz.d(5, 2))).background(Ux.Line))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(p.fraction.coerceIn(0f, 1f)).height(sz.d(10, 4)).clip(RoundedCornerShape(sz.d(5, 2))).background(Ux.Accent))
            if (p.directMark != null) Box(
                Modifier.align(Alignment.CenterStart).offset(x = maxWidth * p.fraction.coerceIn(0f, 1f) - sz.d(14, 6)).size(sz.d(28, 12)).clip(CircleShape).background(Color.White),
            )
        }
        p.directMark?.let { Text(it, color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = sz.s(22, 11), maxLines = 1) }
        p.remainingLabel?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = sz.s(22, 11), maxLines = 1, softWrap = false) }
    }
}

// ───────────────────────── Étage milieu : chaînes récentes ─────────────────────────

@Composable
private fun RecentsRow(state: LiveOsdState, focus: LiveOsdFocus, sz: Sz, onRecent: (String) -> Unit, onStage: (OsdStage) -> Unit) {
    Column(
        Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onStage(OsdStage.RECENTS) },
        verticalArrangement = Arrangement.spacedBy(sz.d(14, 4)),
    ) {
        Text(
            state.recentsTitle, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = sz.s(22, 11), maxLines = 1,
            modifier = Modifier.padding(horizontal = sz.d(96, 12)),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(sz.d(20, 8)),
            contentPadding = PaddingValues(horizontal = sz.d(96, 12), vertical = sz.d(10, 2)),
        ) {
            items(state.recents.size, key = { state.recents[it].key }) { i ->
                val r = state.recents[i]
                RecentCard(r, sz, if (i == 0) Modifier.focusRequester(focus.recents) else Modifier, onClick = { onRecent(r.key) })
            }
        }
    }
}

@Composable
private fun RecentCard(r: OsdRecent, sz: Sz, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(sz.d(18, 12)), bg = Ux.Surface2, ringWidth = sz.d(5, 2), modifier = modifier.width(sz.d(268, 150)).height(sz.d(136, 60))) { f ->
        Column(Modifier.fillMaxSize().padding(sz.d(16, 8)), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sz.d(12, 6))) {
                LogoBox(r.logo, r.name, Modifier.width(sz.d(64, 28)).height(sz.d(40, 20)), radius = 8, pad = 4, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface)
                if ((r.number ?: 0) > 0) Text(r.number.toString(), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = sz.s(22, 11), maxLines = 1)
                if (sz.touch) Text(r.name, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!sz.touch) Text(r.name, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.now.orEmpty(), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = sz.s(22, 10), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ───────────────────────── Étage bas : barre de contrôles ─────────────────────────

@Composable
private fun ControlBar(state: LiveOsdState, focus: LiveOsdFocus, sz: Sz, onAction: (OsdActionId) -> Unit, onStage: (OsdStage) -> Unit) {
    val bg = if (sz.touch) Color(0xB80A0A0C) else Color(0xF20A0A0C)
    LazyRow(
        Modifier.fillMaxWidth().background(bg).onFocusChanged { if (it.hasFocus) onStage(OsdStage.CONTROLS) },
        horizontalArrangement = Arrangement.spacedBy(sz.d(14, 8)),
        contentPadding = PaddingValues(start = sz.d(96, 12), end = sz.d(96, 12), top = sz.d(28, 6), bottom = sz.d(54, 8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(state.actions.size, key = { state.actions[it].id.name }) { i ->
            val a = state.actions[i]
            if (a.separatorBefore) Row {
                Box(Modifier.padding(end = sz.d(14, 8)).width(sz.d(2, 1)).height(sz.d(40, 20)).background(Ux.Line))
                ActionPill(a, sz, if (i == 0) Modifier.focusRequester(focus.bar) else Modifier) { onAction(a.id) }
            } else ActionPill(a, sz, if (i == 0) Modifier.focusRequester(focus.bar) else Modifier) { onAction(a.id) }
        }
    }
}

@Composable
private fun ActionPill(a: OsdAction, sz: Sz, modifier: Modifier, onClick: () -> Unit) {
    if (sz.touch) {
        val label = listOfNotNull(a.label, a.detail).joinToString(" · ")
        PlayerPill(ControlPill(label, a.icon, onClick, a.active, a.fillIcon))
        return
    }
    // Pilule lisible depuis le canapé : fond opaque, libellé blanc 24 px gras ; focus = fond clair + texte foncé + anneau accent ;
    // [active] (favori, veille armée, replay…) = fond accent visible sans focus.
    val bg = if (a.active) Ux.Accent else PILL_BG
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(32.design), bg = bg, ringWidth = 5.design, modifier = modifier.height(64.designT)) { f ->
        val ink = when { f -> Ux.TextOnLight; a.active -> Ux.OnAccent; else -> Color.White }
        Row(
            Modifier.then(if (!f && !a.active) Modifier.border(2.design, PILL_EDGE, RoundedCornerShape(32.design)) else Modifier)
                .padding(horizontal = 24.design).height(64.designT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (a.icon.isNotEmpty()) {
                DIcon(a.icon, 26.design, ink, strokeWidth = 2.4f, fill = a.fillIcon)
                Spacer(Modifier.width(12.design))
            }
            Text(a.label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, softWrap = false)
            a.detail?.let { Text("  ·  $it", color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, softWrap = false) }
        }
    }
}

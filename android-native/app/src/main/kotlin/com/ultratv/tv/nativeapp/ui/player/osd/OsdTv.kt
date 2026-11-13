package com.ultratv.tv.nativeapp.ui.player.osd

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.common.LocalLowRam
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.LocalAnimations
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * OSD du Direct, disposition TV de la nouvelle interface (maquette « Main ») : identité de la chaîne en haut à gauche,
 * horloge en haut à droite ; en bas, rangée des récentes, programme, ruban de la soirée, dock de commandes.
 * La surcouche est posée sur la vidéo : elle utilise TOUJOURS la palette sombre, quel que soit le thème choisi.
 * Mêmes touches et même logique que l'ancienne disposition ([LiveOsdLogic] inchangée) ; seul le rendu change.
 */
private val P = DarkPalette

/** Fonds translucides de la maquette (posés sur la vidéo). */
private val CHIP_BG = Color(0xE61C2029)
private val DOCK_BG = Color(0x1FF4F5F7)
private val PILL_EDGE = Color(0x59F4F5F7)
private val RIBBON_PAST = Color(0x0FF4F5F7)
private val RIBBON_NEXT = Color(0x1AF4F5F7)
private val RIBBON_FILL = Color(0x47FF3B47)
private val RIBBON_NOW_LABEL = Color(0xFFFFB3B8)

private fun letter(px: Float) = (px / 2f).sp

@Composable
internal fun TvOsd(
    state: LiveOsdState, focus: LiveOsdFocus, onAction: (OsdActionId) -> Unit, onRecent: (String) -> Unit, onStage: (OsdStage) -> Unit,
    onNextAction: (OsdNextAction) -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        // Voile haut très léger : lisibilité de l'identité sur n'importe quelle image.
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(260.design).background(Brush.verticalGradient(listOf(Color(0x990A0C10), Color.Transparent))))
        TvIdentity(state, Modifier.align(Alignment.TopStart).padding(start = 72.design, top = 56.design))
        Text(
            state.clock, color = P.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 72.design, top = 56.design),
        )
        if (state.expanded) {
            // Voile du bas : 660 px à 74 %, lisière douce en haut (un bord net couperait l'image en deux).
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(660.design)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.14f to Color(0xBD0A0C10), 1f to Color(0xBD0A0C10))),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 72.design, end = 72.design, bottom = 56.design),
                verticalArrangement = Arrangement.spacedBy(26.design),
            ) {
                if (state.recents.isNotEmpty()) TvRecents(state, focus, onRecent, onStage)
                if (state.programme != null || state.next != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(40.design)) {
                    Box(Modifier.weight(1f)) { state.programme?.let { TvProgramme(state, it) } }
                    state.next?.let { NextCard(state, it, onNextAction, onStage) }
                }
                state.hint?.let {
                    Text(it, color = P.text3, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.ribbon.isNotEmpty()) TvRibbon(state.ribbon)
                TvDock(state, focus, onAction, onStage)
            }
        } else if (state.hint != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                state.hint, color = P.text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1,
                modifier = Modifier.padding(bottom = 54.design).clip(RoundedCornerShape(30.design)).background(Color(0xCC0A0C10)).padding(horizontal = 28.design, vertical = 14.design),
            )
        }
    }
}

// ───────────────────────── Identité (haut gauche) ─────────────────────────

@Composable
private fun TvIdentity(state: LiveOsdState, modifier: Modifier) {
    val ch = state.channel
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.design)) {
        ch.number?.takeIf { it > 0 }?.let {
            Text(it.toString(), color = P.text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 64.spx, lineHeight = 64.spx, letterSpacing = letter(-2f), maxLines = 1)
        }
        if (!ch.logo.isNullOrBlank()) LogoBox(ch.logo, ch.name, Modifier.size(84.design), radius = 22, pad = 10, bg = P.text)
        else Box(Modifier.size(84.design).clip(RoundedCornerShape(22.design)).background(P.text), contentAlignment = Alignment.Center) {
            Text(OsdText.initials(ch.name), color = P.focusText, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 26.spx, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
            Text(ch.name, color = P.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 900.design))
            Row(horizontalArrangement = Arrangement.spacedBy(10.design), verticalAlignment = Alignment.CenterVertically) {
                if (state.badge.timeshift) Text(
                    state.badge.text, color = P.text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx, letterSpacing = letter(1.5f), maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(P.button).padding(horizontal = 14.design, vertical = 5.design),
                ) else LivePill(state.badge.text)
                ch.quality?.let { OutlinePill(it) }
                OsdText.hdrLabel(ch.hdr)?.let { OutlinePill(it) }
                ch.badge?.let { OutlinePill(it) }
                if (state.favorite && state.favoriteWord.isNotBlank()) OutlinePill(state.favoriteWord)
            }
        }
    }
}

/** Pastille « EN DIRECT » : fond accent, point blanc qui respire (isolé dans [LiveDot] : l'OSD ne se recompose pas à chaque image). */
@Composable
private fun LivePill(text: String) {
    Row(
        Modifier.clip(CircleShape).background(Ux.Accent).padding(horizontal = 14.design, vertical = 5.design),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.design),
    ) {
        LiveDot(9.design, Color.White)
        Text(text, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx, letterSpacing = letter(1.5f), maxLines = 1)
    }
}

@Composable
private fun OutlinePill(text: String) {
    Text(
        text, color = P.text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1,
        modifier = Modifier.border(0.75.dp, PILL_EDGE, CircleShape).padding(horizontal = 12.design, vertical = 5.design),
    )
}

/**
 * Point « en direct » qui respire. État d'animation lu uniquement dans la couche graphique (graphicsLayer) : aucune
 * recomposition, seul le dessin de ce point est relancé. Fixe si les animations sont coupées ou sur une box à faible mémoire.
 */
@Composable
internal fun LiveDot(size: Dp, color: Color, modifier: Modifier = Modifier) {
    val still = LocalAnimations.current.off || LocalLowRam.current
    if (still) {
        Box(modifier.size(size).clip(CircleShape).background(color))
        return
    }
    val alpha = rememberInfiniteTransition(label = "liveDot").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "liveDotAlpha",
    )
    Box(modifier.size(size).graphicsLayer { this.alpha = alpha.value }.clip(CircleShape).background(color))
}

// ───────────────────────── Récentes ─────────────────────────

@Composable
private fun TvRecents(state: LiveOsdState, focus: LiveOsdFocus, onRecent: (String) -> Unit, onStage: (OsdStage) -> Unit) {
    val shown = state.recents.take(OsdText.RECENTS_SHOWN)
    Column(
        Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onStage(OsdStage.RECENTS) }
            // Bas (ou Retour, voir LiveOsdLogic.backEffect) : retour à la barre de commandes. Gauche / Droite : la rangée.
            .focusProperties { down = focus.bar },
        verticalArrangement = Arrangement.spacedBy(12.design),
    ) {
        Text(
            state.recentsShort.ifBlank { state.recentsTitle.uppercase() }, color = P.text3, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 18.spx, letterSpacing = letter(1.5f), maxLines = 1,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.design)) {
            shown.forEachIndexed { i, r ->
                RecentChip(r, if (i == 0) Modifier.focusRequester(focus.recents) else Modifier) { onRecent(r.key) }
            }
            // Cases vides : les puces gardent la largeur d'un cinquième de la rangée.
            repeat(OsdText.RECENTS_SHOWN - shown.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RowScope.RecentChip(r: OsdRecent, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(18.design), bg = CHIP_BG, focusedBg = P.focusFill, focusedScale = 1f, ringWidth = 0.dp,
        modifier = modifier.weight(1f),
    ) { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.design, vertical = 10.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
            LogoBox(r.logo, r.name, Modifier.size(52.design), radius = 14, pad = 6, bg = if (f) P.focusText else P.tile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ink = if (f) P.focusText else P.text
                    // Numéro et nom dans deux textes : le nom seul reste trouvable (accessibilité, tests) et s'abrège seul.
                    if ((r.number ?: 0) > 0) Text("${r.number} · ", color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, softWrap = false)
                    Text(r.name, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Text(r.now.orEmpty(), color = if (f) P.focusText2 else P.text3, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────── Programme ─────────────────────────

@Composable
private fun TvProgramme(state: LiveOsdState, prog: OsdProgrammeInfo) {
    Column(Modifier.widthIn(max = 1100.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
        Text(
            OsdText.nowLine(state.nowWord, prog.timeRange, prog.progress?.remainingLabel), color = P.accentText, fontFamily = Manrope,
            fontWeight = FontWeight.Bold, fontSize = 20.spx, letterSpacing = letter(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(prog.title, color = P.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 56.spx, lineHeight = 59.spx, letterSpacing = letter(-1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        prog.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = P.text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 35.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Carte « ENSUITE · hh:mm – hh:mm » : programme suivant + « Me rappeler » / « Enregistrer ». */
@Composable
private fun NextCard(state: LiveOsdState, next: OsdNextInfo, onNextAction: (OsdNextAction) -> Unit, onStage: (OsdStage) -> Unit) {
    Column(
        // Retour depuis ces boutons : retour à la barre (même effet que depuis les récentes).
        Modifier.width(560.design).clip(RoundedCornerShape(24.design)).background(CHIP_BG).padding(horizontal = 26.design, vertical = 22.design)
            .onFocusChanged { if (it.hasFocus) onStage(OsdStage.RECENTS) },
        verticalArrangement = Arrangement.spacedBy(8.design),
    ) {
        Text(
            listOf(state.nextWord, next.range).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = P.text3, fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold, fontSize = 16.spx, letterSpacing = letter(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(next.title, color = P.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        next.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = P.text2, fontFamily = Manrope, fontSize = 18.spx, lineHeight = 26.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 6.design), horizontalArrangement = Arrangement.spacedBy(10.design)) {
            NextButton(state.remindWord) { onNextAction(OsdNextAction.REMIND) }
            NextButton(state.recordWord) { onNextAction(OsdNextAction.RECORD) }
        }
    }
}

@Composable
private fun NextButton(label: String, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(23.design), bg = DOCK_BG, focusedBg = P.focusFill, focusedScale = 1f, ringWidth = 0.dp,
        modifier = Modifier.height(46.design),
    ) { f ->
        Box(Modifier.height(46.design).padding(horizontal = 20.design), contentAlignment = Alignment.Center) {
            Text(label, color = if (f) P.focusText else P.text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx, maxLines = 1, softWrap = false)
        }
    }
}

// ───────────────────────── Ruban de la soirée ─────────────────────────

@Composable
private fun TvRibbon(slots: List<OsdRibbonSlot>) {
    Row(Modifier.fillMaxWidth().height(64.design), horizontalArrangement = Arrangement.spacedBy(6.design)) {
        for (s in slots) RibbonCell(s, Modifier.weight(s.weight).fillMaxHeight())
    }
}

@Composable
private fun RibbonCell(s: OsdRibbonSlot, modifier: Modifier) {
    val shape = RoundedCornerShape(14.design)
    val now = s.kind == RibbonKind.NOW
    val bg = when (s.kind) { RibbonKind.PAST -> RIBBON_PAST; RibbonKind.NOW -> P.nowTint; RibbonKind.NEXT -> RIBBON_NEXT }
    Box(modifier.clip(shape).background(bg).then(if (now) Modifier.border(2.design, Ux.Accent, shape) else Modifier)) {
        if (now && s.fraction > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(s.fraction).background(RIBBON_FILL))
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.design, vertical = 10.design), verticalArrangement = Arrangement.spacedBy(2.design)) {
            Text(
                s.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Manrope, fontSize = 15.spx,
                fontWeight = if (now) FontWeight.ExtraBold else FontWeight.Bold,
                color = when (s.kind) { RibbonKind.PAST -> P.muted; RibbonKind.NOW -> RIBBON_NOW_LABEL; RibbonKind.NEXT -> P.text3 },
            )
            Text(
                s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Manrope, fontSize = 18.spx,
                fontWeight = if (s.kind == RibbonKind.NEXT || s.kind == RibbonKind.PAST) FontWeight.SemiBold else FontWeight.Bold,
                color = if (s.kind == RibbonKind.PAST) P.muted else P.text,
            )
        }
    }
}

// ───────────────────────── Dock de commandes ─────────────────────────

@Composable
private fun TvDock(state: LiveOsdState, focus: LiveOsdFocus, onAction: (OsdActionId) -> Unit, onStage: (OsdStage) -> Unit) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Lu dans un dérivé : seule la présence / l'absence du fondu recompose, pas chaque défilement.
    val more by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { listState.canScrollForward } }
    val fade = com.ultratv.tv.nativeapp.ui.design.startToEndBrush(0f to Color.Black, 0.93f to Color.Black, 1f to Color.Transparent)
    // Pleine largeur ; défile quand il y a plus de boutons que de place (le bouton focalisé est ramené à l'écran par le LazyRow).
    LazyRow(
        Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onStage(OsdStage.CONTROLS) }
            // Fondu léger sur le bord de fin, seulement s'il reste des boutons hors écran.
            .then(if (more) Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent { drawContent(); drawRect(fade, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn) } else Modifier),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.design), verticalAlignment = Alignment.CenterVertically,
    ) {
        items(state.actions.size, key = { state.actions[it].id.name }) { i ->
            val a = state.actions[i]
            val mod = if (i == 0) Modifier.focusRequester(focus.bar) else Modifier
            if (a.separatorBefore) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 12.design).width(2.design).height(32.design).background(P.line))
                DockButton(a, mod) { onAction(a.id) }
            } else DockButton(a, mod) { onAction(a.id) }
        }
    }
}

/** Bouton rond ; seul celui qui a le focus montre son libellé (fond accent). Actif (favori, veille…) : icône accent. */
@Composable
private fun DockButton(a: OsdAction, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(30.design), bg = DOCK_BG, focusedBg = Ux.Accent, focusedScale = 1f, ringWidth = 0.dp,
        modifier = modifier.height(60.design),
    ) { f ->
        val iconOnly = a.icon.isNotEmpty()
        val ink = when { f -> Color.White; a.active -> P.accentText; else -> P.text }
        Row(
            // Libellé lu par l'accessibilité même quand il n'est pas affiché (au focus, le texte visible le porte).
            Modifier.semantics { if (!f && iconOnly) contentDescription = a.label }.height(60.design).then(if (f || !iconOnly) Modifier.padding(start = 20.design, end = if (f) 24.design else 20.design) else Modifier.width(60.design)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (f || !iconOnly) Arrangement.spacedBy(10.design) else Arrangement.Center,
        ) {
            if (iconOnly) DIcon(a.icon, 24.design, ink, strokeWidth = 2f, fill = a.fillIcon)
            // Sans icône (−30 / +30 du différé) le libellé tient lieu d'icône ; sinon il n'apparaît qu'au focus.
            if (f || !iconOnly) Text(a.label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1, softWrap = false)
            if (f) a.detail?.let { Text("· $it", color = Color(0xCCFFFFFF), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.spx, maxLines = 1, softWrap = false) }
        }
    }
}

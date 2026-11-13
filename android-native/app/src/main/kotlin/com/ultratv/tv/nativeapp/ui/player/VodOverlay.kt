package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.Strings
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.ThumbImage
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import kotlinx.coroutines.delay

/**
 * Surcouche des films et épisodes (télécommande), disposition de la nouvelle interface (maquettes « Lecteur film » et
 * « Lecteur série ») : titre et pastilles techniques en haut, horloge à droite ; en bas, barre de lecture (partie tamponnée,
 * repères d'intro / de générique, bulle de temps pendant un saut), temps écoulé / restant / heure de fin, boutons ronds à
 * gauche (seul celui qui a le focus montre son libellé) et pastilles à droite. Posée sur la vidéo : TOUJOURS la palette sombre.
 *
 * Performance : [pos], [dur] et [buffered] sont des LAMBDAS lues seulement par les petits composants qui les affichent (texte du
 * temps, tracé de la barre dans la phase de dessin) : une seconde de lecture ne recompose que ces textes, jamais la rangée
 * de boutons ni les pastilles. Aucun flou ; l'unique animation (aucune) laisse la place au rendu statique.
 */
private val P = DarkPalette

private val DOCK_BG = Color(0x1FF4F5F7)
private val PILL_EDGE = Color(0x59F4F5F7)
private val TRACK = Color(0x2EF4F5F7)
private val BUFFERED = Color(0x4DF4F5F7)
private val MARK = Color(0x73FF8088)
private val CARD_BG = Color(0xEB1C2029)
private val TOP_SCRIM = Brush.verticalGradient(listOf(Color(0x990A0C10), Color.Transparent))
private val BOTTOM_SCRIM = Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color(0xBD0A0C10), 1f to Color(0xBD0A0C10))

private fun letter(px: Float) = (px / 2f).sp

/** Libellés des boutons de la surcouche (tirés des chaînes existantes, plus le nom de la barre de progression). */
internal class VodLabels(
    val pause: String, val play: String, val back10: String, val fwd10: String, val restart: String,
    val nextEpisode: String, val previousEpisode: String,
    val tracks: String, val subtitles: String, val speed: String, val format: String, val sleep: String,
    val loading: String, val endsAt: (String) -> String, val seekBar: String = "",
)

internal fun vodLabels(X: PlayerExtraStrings, S: Strings, D: DesignStrings) = VodLabels(
    pause = X.statusPaused, play = X.statusPlaying, back10 = "−10 s", fwd10 = "+10 s", restart = X.fromStart,
    nextEpisode = X.nextEpisode, previousEpisode = X.previousEpisode,
    tracks = S.playerTracks, subtitles = X.subsPill, speed = D.speed, format = S.playerAspect, sleep = X.sleepPill,
    loading = X.statusLoading, endsAt = { t -> X.endsAt(t) }, seekBar = X.seekBar,
)

@Composable
internal fun VodOverlay(
    title: String, badges: TechBadges, pos: () -> Long, dur: () -> Long, speed: Float,
    playing: Boolean, loading: Boolean, hasPrev: Boolean, hasNext: Boolean, sleepActive: Boolean,
    pauseFocus: FocusRequester, labels: VodLabels,
    onPrev: () -> Unit, onSeek: (Long) -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    onSubs: () -> Unit, onSleep: () -> Unit, onOptions: () -> Unit, onAudio: () -> Unit,
    kicker: String = "", episode: Boolean = false, buffered: () -> Long = { -1L },
    marks: List<Pair<Float, Float>> = emptyList(), onRestart: () -> Unit = {}, onDisplay: () -> Unit = onOptions,
    // Défilement à la télécommande (SeekScrubLogic) : focus de la barre, position visée (ms, < 0 = aucune) et écart affiché.
    barFocus: FocusRequester = remember { FocusRequester() }, onBarFocus: (Boolean) -> Unit = {},
    scrubTarget: () -> Long = { -1L }, scrubDelta: () -> String = { "" },
) {
    // Bulle de temps : visible un court instant après un saut, à l'endroit où l'on arrive.
    var preview by remember { mutableStateOf<Long?>(null) }
    var previewTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(previewTick) { if (previewTick > 0) { delay(1_400); preview = null } }
    val seek: (Long) -> Unit = { d ->
        val total = dur()
        val t = (pos() + d).coerceAtLeast(0L).let { if (total > 0) it.coerceAtMost(total) else it }
        preview = t
        previewTick++
        onSeek(d)
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(260.design).background(TOP_SCRIM))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (episode) 440.design else 400.design).background(BOTTOM_SCRIM))
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 72.design, end = 72.design, top = 56.design),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.design),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.design)) {
                if (kicker.isNotBlank()) Text(
                    kicker, color = P.accentText, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx,
                    letterSpacing = letter(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title, color = P.text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 48.spx, lineHeight = 54.spx,
                    letterSpacing = letter(-1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.design), verticalAlignment = Alignment.CenterVertically) {
                badges.quality?.let { OutlinePill(it) }
                badges.hdr?.let { OutlinePill(it) }
                badges.codec?.let { OutlinePill(it) }
                badges.audio?.let { OutlinePill(it) }
                WallClock()
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 72.design, end = 72.design, bottom = 56.design),
            verticalArrangement = Arrangement.spacedBy(24.design),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
                Box(Modifier.fillMaxWidth()) {
                    SeekBar(pos, dur, buffered, marks, scrubTarget, barFocus, onBarFocus, labels.seekBar)
                    ScrubBubble(dur, scrubTarget, scrubDelta)
                    preview?.let { t ->
                        val d = dur()
                        val f = if (d > 0) (t.toFloat() / d).coerceIn(0f, 1f) else 0f
                        val shape = RoundedCornerShape(14.design)
                        Box(
                            Modifier.align(BiasAlignment(2f * f - 1f, -1f)).offset(y = (-76).design).clip(shape).background(P.tile)
                                .border(2.design, P.text, shape).padding(horizontal = 22.design, vertical = 12.design),
                        ) {
                            Text(VodOverlayLogic.clock(t), color = P.text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ClockText({ VodOverlayLogic.clock(pos()) }, P.text, FontWeight.Bold)
                    CenterInfo(pos, dur, speed, loading, playing, labels)
                    ClockText({ if (dur() > 0) VodOverlayLogic.clock(dur()) else "" }, P.text, FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
                RoundButton(
                    if (playing) PAUSE else PLAY, if (playing) labels.pause else labels.play, fill = !playing,
                    modifier = Modifier.focusRequester(pauseFocus), onClick = onToggle,
                )
                RoundButton(BACK10, labels.back10) { seek(-10_000) }
                RoundButton(FWD10, labels.fwd10) { seek(10_000) }
                if (episode) {
                    if (hasPrev) RoundButton(PREV, labels.previousEpisode, onClick = onPrev)
                    if (hasNext) RoundButton(NEXT, labels.nextEpisode, onClick = onNext)
                } else RoundButton(RESTART, labels.restart, onClick = onRestart)
                Spacer(Modifier.weight(1f))
                DockPill(TRACKS, labels.tracks, onClick = onAudio)
                DockPill(SUBS, labels.subtitles, onClick = onSubs)
                DockPill(SPEED, labels.speed, onClick = onDisplay)
                if (!episode) DockPill(FIT, labels.format, onClick = onDisplay)
                DockPill(MOON, labels.sleep, active = sleepActive, onClick = onSleep)
            }
        }
    }
}

@Composable
private fun OutlinePill(text: String) {
    Text(
        text, color = P.text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1,
        modifier = Modifier.border(0.75.dp, PILL_EDGE, CircleShape).padding(horizontal = 14.design, vertical = 6.design),
    )
}

/** Heure courante (relue toutes les 15 s) : seule cette fonction se recompose. */
@Composable
private fun WallClock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Text(
        com.ultratv.tv.nativeapp.ui.common.EpgClock.wall(now), color = P.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 1,
        modifier = Modifier.padding(start = 14.design),
    )
}

/** Texte d'un temps : la lambda est lue ICI, donc seule cette fonction se recompose chaque seconde. */
@Composable
private fun ClockText(text: () -> String, color: Color, weight: FontWeight) {
    Text(text(), color = color, fontFamily = Manrope, fontWeight = weight, fontSize = 19.spx, maxLines = 1)
}

/** « −1:34:12 · Se termine à 23:16 » (heure de fin relue toutes les 15 s, pause comprise) ; « Chargement… » pendant la mise en mémoire. */
@Composable
private fun CenterInfo(pos: () -> Long, dur: () -> Long, speed: Float, loading: Boolean, playing: Boolean, labels: VodLabels) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playing) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    ClockText({
        if (loading) labels.loading
        else listOf(
            VodOverlayLogic.remaining(pos(), dur()),
            VodOverlayLogic.endsAtMs(now, pos(), dur(), speed)?.let { labels.endsAt(com.ultratv.tv.nativeapp.ui.common.EpgClock.wall(it)) }.orEmpty(),
        ).filter { it.isNotEmpty() }.joinToString(" · ")
    }, P.text3, FontWeight.Bold)
}

/** Barre tracée en phase de DESSIN : la position n'est lue que là, aucune recomposition. */
@Composable
private fun SeekBar(
    pos: () -> Long, dur: () -> Long, buffered: () -> Long, marks: List<Pair<Float, Float>>,
    scrubTarget: () -> Long, barFocus: FocusRequester, onBarFocus: (Boolean) -> Unit, description: String,
) {
    val fill = Ux.Accent
    var focused by remember { mutableStateOf(false) }
    Box(
        // Focalisable : Gauche / Droite y font défiler la position (touches lues par le lecteur) ; Haut / Bas vont aux boutons.
        Modifier.fillMaxWidth().height(34.design).focusRequester(barFocus)
            .onFocusChanged { focused = it.isFocused; onBarFocus(it.isFocused) }.semantics { contentDescription = description }.focusable().drawBehind {
            val d = dur()
            // En lecture de droite à gauche (arabe) la barre est miroir : le début est à droite.
            val rtl = layoutDirection == LayoutDirection.Rtl
            fun x(frac: Float) = if (rtl) size.width * (1f - frac) else size.width * frac
            fun seg(a: Float, b: Float): Pair<Float, Float> = if (rtl) x(b) to size.width * (b - a) else x(a) to size.width * (b - a)
            val f = if (d > 0) (pos().toFloat() / d).coerceIn(0f, 1f) else 0f
            val b = if (d > 0) (buffered().toFloat() / d).coerceIn(0f, 1f) else 0f
            val h = (if (focused) 14.design else 10.design).toPx(); val y = (size.height - h) / 2; val r = CornerRadius(h / 2)
            drawRoundRect(TRACK, Offset(0f, y), Size(size.width, h), r)
            if (b > f) seg(0f, b).let { (ox, w) -> drawRoundRect(BUFFERED, Offset(ox, y), Size(w, h), r) }
            for ((s, e) in marks) {
                val w = (e - s).coerceAtLeast(0f)
                if (w > 0f) seg(s, e).let { (ox, sw) -> drawRect(MARK, Offset(ox, y), Size(sw, h)) }
            }
            if (f > 0f) seg(0f, f).let { (ox, w) -> drawRoundRect(fill, Offset(ox, y), Size(w, h), r) }
            val rad = (if (focused) 15.design else 13.design).toPx()
            drawCircle(Color.White, radius = rad, center = Offset(x(f).coerceIn(rad, size.width - rad), size.height / 2))
            // Curseur « cible » du défilement : anneau accent, et trait entre la position actuelle et la cible.
            val t = scrubTarget()
            if (t >= 0 && d > 0) {
                val tf = (t.toFloat() / d).coerceIn(0f, 1f)
                seg(minOf(f, tf), maxOf(f, tf)).let { (ox, w) -> drawRect(fill.copy(alpha = 0.55f), Offset(ox, y), Size(w, h)) }
                val tr = 17.design.toPx(); val cx = x(tf).coerceIn(tr, size.width - tr)
                drawCircle(Color.White, radius = tr, center = Offset(cx, size.height / 2))
                drawCircle(fill, radius = tr - 4.design.toPx(), center = Offset(cx, size.height / 2))
            }
        },
    )
}

/** Bulle au-dessus de la barre pendant un défilement : heure visée et écart (« 1:34:12  +2:30 »). */
@Composable
private fun BoxScope.ScrubBubble(dur: () -> Long, scrubTarget: () -> Long, scrubDelta: () -> String) {
    val t = scrubTarget()
    if (t < 0) return
    val d = dur()
    val f = if (d > 0) (t.toFloat() / d).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(14.design)
    Box(
        Modifier.align(BiasAlignment(2f * f - 1f, -1f)).offset(y = (-76).design).clip(shape).background(P.tile)
            .border(2.design, Ux.Accent, shape).padding(horizontal = 22.design, vertical = 12.design),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.design), verticalAlignment = Alignment.CenterVertically) {
            Text(VodOverlayLogic.clock(t), color = P.text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1)
            Text(scrubDelta(), color = P.accentText, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1)
        }
    }
}

/** Bouton rond ; seul celui qui a le focus montre son libellé (fond accent). */
@Composable
private fun RoundButton(icon: String, label: String, modifier: Modifier = Modifier, fill: Boolean = false, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(32.design), bg = DOCK_BG, focusedBg = Ux.Accent, focusedScale = 1f, ringWidth = 0.dp,
        modifier = modifier.height(64.design),
    ) { f ->
        val ink = if (f) Color.White else P.text
        Row(
            // Libellé lu par l'accessibilité même quand il n'est pas affiché (au focus, le texte visible le porte).
            Modifier.semantics { if (!f) contentDescription = label }.height(64.design)
                .then(if (f) Modifier.padding(start = 22.design, end = 26.design) else Modifier.width(64.design)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (f) Arrangement.spacedBy(10.design) else Arrangement.Center,
        ) {
            DIcon(icon, 26.design, ink, strokeWidth = 2f, fill = fill)
            if (f) Text(label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1, softWrap = false)
        }
    }
}

/** Pastille de droite (Pistes, Sous-titres…) : icône + libellé toujours visibles ; active = icône accent. */
@Composable
private fun DockPill(icon: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(28.design), bg = DOCK_BG, focusedBg = Ux.Accent, focusedScale = 1f, ringWidth = 0.dp,
        modifier = Modifier.height(56.design),
    ) { f ->
        val ink = if (f) Color.White else if (active) P.accentText else P.text
        Row(
            Modifier.height(56.design).padding(horizontal = 20.design),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design),
        ) {
            DIcon(icon, 24.design, ink, strokeWidth = 2f)
            Text(label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Carte « Épisode suivant dans N s » : vignette, titre, résumé et barre du compte à rebours. Informative (non focalisable) :
 * le passage à l'épisode suivant reste celui de la fin de lecture / du bouton existant.
 */
@Composable
internal fun NextEpisodeCard(tag: String, label: String, title: String, summary: String?, image: String?, fraction: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.width(620.design).clip(RoundedCornerShape(24.design)).background(CARD_BG).padding(16.design),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design),
    ) {
        Box(Modifier.width(200.design).height(112.design)) {
            ThumbImage(image, tag, Modifier.fillMaxSize(), radius = 14, decodeSize = DpSize(100.dp, 56.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.design)) {
            Text(label, color = P.accentText, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.spx, letterSpacing = letter(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = P.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!summary.isNullOrBlank()) Text(summary, color = P.text3, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(4.design).clip(RoundedCornerShape(2.design)).background(DOCK_BG)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Ux.Accent))
            }
        }
    }
}

private const val PLAY = "M8 5l11 7-11 7z"
private const val PAUSE = "M8 5v14M16 5v14"
private const val PREV = "M18 5l-9 7 9 7zM6 5v14"
private const val NEXT = "M6 5l9 7-9 7zM18 5v14"
private const val BACK10 = "M11 17l-5-5 5-5M18 17l-5-5 5-5"
private const val FWD10 = "M13 7l5 5-5 5M6 7l5 5-5 5"
private const val RESTART = "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5"
private const val TRACKS = "M4 6h16M4 12h10M4 18h6"
private const val SUBS = "M4 6h16v12H4zM8 14h3M13 14h3"
private const val SPEED = "M12 12l4-4M4 18a9 9 0 1 1 16 0"
private const val FIT = "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
private const val MOON = "M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z"

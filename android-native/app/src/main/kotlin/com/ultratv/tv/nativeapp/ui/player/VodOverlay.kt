package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import kotlinx.coroutines.delay

/**
 * Surcouche épurée des films et épisodes (télécommande) : titre et pastilles techniques en haut ; en bas, barre fine,
 * temps écoulé / restant, rangée de boutons ronds et ligne d'état. Le direct garde sa propre surcouche.
 *
 * Performance : [pos] et [dur] sont des LAMBDAS lues seulement par les petits composants qui les affichent (texte du
 * temps, tracé de la barre dans la phase de dessin) : une seconde de lecture ne recompose que ces textes, jamais la rangée
 * de boutons ni les pastilles. Aucun flou, aucune animation, aucun dégradé recalculé.
 */
@Composable
internal fun VodOverlay(
    title: String, badges: TechBadges, pos: () -> Long, dur: () -> Long, speed: Float,
    playing: Boolean, loading: Boolean, hasPrev: Boolean, hasNext: Boolean, sleepActive: Boolean,
    pauseFocus: FocusRequester, X: PlayerExtraStrings,
    onPrev: () -> Unit, onSeek: (Long) -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    onSubs: () -> Unit, onSleep: () -> Unit, onOptions: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().background(TOP_SCRIM).padding(start = 96.design, end = 96.design, top = 44.design, bottom = 56.design),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.design),
        ) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, lineHeight = 52.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!badges.isEmpty) Row(horizontalArrangement = Arrangement.spacedBy(12.design), verticalAlignment = Alignment.CenterVertically) {
                badges.quality?.let { Badge(it) }
                badges.codec?.let { Badge(it) }
                badges.audio?.let { Badge(it) }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(BOTTOM_SCRIM).padding(start = 96.design, end = 96.design, top = 56.design, bottom = 40.design),
            verticalArrangement = Arrangement.spacedBy(12.design),
        ) {
            ProgressBar(pos, dur)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ClockText({ VodOverlayLogic.clock(pos()) }, Ux.Text, FontWeight.Bold)
                ClockText({ VodOverlayLogic.remaining(pos(), dur()) }, Ux.Text2, FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.design), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(72, PREV, enabled = hasPrev, onClick = onPrev)
                    RoundBtn(72, BACK10, label = "10", onClick = { onSeek(-10_000) })
                    BigPlayButton(playing, pauseFocus, onToggle)
                    RoundBtn(72, FWD10, label = "10", onClick = { onSeek(10_000) })
                    RoundBtn(72, NEXT, enabled = hasNext, onClick = onNext)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.design), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(72, SUBS, onClick = onSubs)
                    RoundBtn(72, MOON, active = sleepActive, onClick = onSleep)
                    RoundBtn(72, SLIDERS, onClick = onOptions)
                }
            }
            StatusLine(pos, dur, speed, if (loading) X.statusLoading else if (playing) X.statusPlaying else X.statusPaused, X, playing)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(text, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.design)).background(Color(0x59FFFFFF)).padding(horizontal = 16.design, vertical = 6.design))
}

/** Texte d'un temps : la lambda est lue ICI, donc seule cette fonction se recompose chaque seconde. */
@Composable
private fun ClockText(text: () -> String, color: Color, weight: FontWeight) {
    Text(text(), color = color, fontFamily = Manrope, fontWeight = weight, fontSize = 26.spx, maxLines = 1)
}

/** Barre fine tracée en phase de DESSIN : la position n'est lue que là, aucune recomposition. */
@Composable
private fun ProgressBar(pos: () -> Long, dur: () -> Long) {
    val track = Ux.Line; val fill = Ux.Accent
    Box(
        Modifier.fillMaxWidth().height(24.design).drawBehind {
            val d = dur()
            val f = if (d > 0) (pos().toFloat() / d).coerceIn(0f, 1f) else 0f
            val h = 6.design.toPx(); val y = (size.height - h) / 2
            drawRoundRect(track, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
            if (f > 0f) drawRoundRect(fill, Offset(0f, y), Size(size.width * f, h), CornerRadius(h / 2))
            drawCircle(Color.White, radius = 11.design.toPx(), center = Offset((size.width * f).coerceIn(11.design.toPx(), size.width - 11.design.toPx()), size.height / 2))
        },
    )
}

/** Ligne d'état : action en cours à gauche, heure de fin à droite (relue toutes les 15 s, pour la pause aussi). */
@Composable
private fun StatusLine(pos: () -> Long, dur: () -> Long, speed: Float, action: String, X: PlayerExtraStrings, playing: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playing) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(action, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
        ClockText({
            VodOverlayLogic.endsAtMs(now, pos(), dur(), speed)?.let { X.endsAt(VodOverlayLogic.endClock(it, is24)) }.orEmpty()
        }, Ux.Text3, FontWeight.Normal)
    }
}

/** Bouton rond ; désactivé = estompé et hors du parcours du focus. */
@Composable
private fun RoundBtn(sizePx: Int, icon: String, enabled: Boolean = true, active: Boolean = false, label: String? = null, onClick: () -> Unit) {
    if (!enabled) {
        Box(Modifier.size(sizePx.design).clip(CircleShape).background(Color(0x33FFFFFF)).alpha(0.4f), contentAlignment = Alignment.Center) {
            DIcon(icon, 30.design, Ux.Text, fill = icon == PREV || icon == NEXT)
        }
        return
    }
    FocusSurface(onClick = onClick, shape = CircleShape, bg = if (active) Ux.Accent else Color(0x8C26262D), modifier = Modifier.size(sizePx.design)) { f ->
        val ink = if (f) Ux.TextOnLight else if (active) Ux.OnAccent else Ux.Text
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DIcon(icon, 32.design, ink, fill = icon == PREV || icon == NEXT)
            if (label != null) Text(label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.spx, maxLines = 1)
        }
    }
}

@Composable
private fun BigPlayButton(playing: Boolean, focus: FocusRequester, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, bg = Ux.Cta, modifier = Modifier.size(104.design).focusRequester(focus)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (playing) DIcon("M7 4h3.5v16H7zM13.5 4H17v16h-3.5z", 40.design, Ux.TextOnLight, fill = true) else DIcon("M7 4v16l13-8z", 40.design, Ux.TextOnLight, fill = true)
        }
    }
}

private val TOP_SCRIM = Brush.verticalGradient(listOf(Color(0xE60A0A0C), Color(0x000A0A0C)))
private val BOTTOM_SCRIM = Brush.verticalGradient(listOf(Color(0x000A0A0C), Color(0xF20A0A0C)), startY = 0f)
private const val PREV = "M6 5v14M19 5L9 12l10 7z"
private const val NEXT = "M18 5v14M5 5l10 7-10 7z"
private const val BACK10 = "M3 12a9 9 0 1 0 3-6.7M3 4v5h5"
private const val FWD10 = "M21 12a9 9 0 1 1-3-6.7M21 4v5h-5"
private const val SUBS = "M3 6h18v12H3zM7 11h3M12 11h5M7 15h6"
private const val MOON = "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"
private const val SLIDERS = "M4 7h10M18 7h2M4 17h2M10 17h10M14 5v4M8 15v4"

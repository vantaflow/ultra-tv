package com.ultratv.tv.nativeapp.ui.sync

import com.ultratv.tv.nativeapp.ui.common.responsiveWidth
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.SyncPart
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.KeyHint
import com.ultratv.tv.nativeapp.ui.design.LogoMark
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import java.text.NumberFormat

/** Écran de première synchronisation (maquette Chargement.dc.html). */
@Composable
fun FirstSyncScreen(ui: FirstSyncUi, onWatchLive: () -> Unit, onRetry: () -> Unit, onFixSource: () -> Unit) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val compact = touch && com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current < 600f
    Column(
        Modifier.fillMaxSize().background(Ux.Bg)
            .then(if (touch) Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 12.dp) else Modifier.padding(horizontal = 96.design, vertical = 54.design)),
    ) {
        Row(Modifier.fillMaxWidth().height(72.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark()
                Spacer(Modifier.width(16.design))
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) { Row {
Text("ULTRA ", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, letterSpacing = 1.7.sp, color = Ux.Text)
                Text("TV", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 28.spx, letterSpacing = 1.7.sp, color = Ux.Text3)
}}
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 700.design)) {
                DIcon(Icons.Monitor, 26.design, Ux.Text2)
                Spacer(Modifier.width(14.design))
                Text(
                    "${ui.providerName} · ${kindLabel(S.wiz, ui.kind)}", color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val mainCol: @Composable (Modifier) -> Unit = { mainModifier ->
            Column(mainModifier, verticalArrangement = Arrangement.spacedBy(if (touch) 24.dp else 44.design)) {
                Column(verticalArrangement = Arrangement.spacedBy(18.design)) {
                    Text(D.firstSync, color = Ux.Accent, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = 3.sp, maxLines = 1)
                    Text(D.preparing, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 30.sp else 72.spx, lineHeight = if (touch) 34.sp else 76.spx, letterSpacing = (-1).spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(D.preparingBody, color = Ux.Text2, fontFamily = Manrope, fontSize = if (touch) 15.sp else 30.spx, lineHeight = if (touch) 22.sp else 42.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("${ui.percent} %", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 32.sp else 64.spx, maxLines = 1)
                        ui.etaSeconds?.let { Text(D.etaAbout.format(etaText(D, it)), color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1) }
                    }
                    Box(Modifier.fillMaxWidth().height(14.design).clip(RoundedCornerShape(7.design)).background(Ux.Surface2)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(ui.percent / 100f).clip(RoundedCornerShape(7.design)).background(Ux.Accent))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.design)) {
                    if (ui.failure != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
                            Text(S.sync.messageFor(ui.failure), color = Ux.Err, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(20.design)) {
                                val r = remember { FocusRequester() }
                                var f by remember { mutableStateOf(false) }
                                RequestInitialFocus(r, hasFocus = { f })
                                PillButton(D.retry, onRetry, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(r).onFocusChanged { f = it.isFocused })
                                PillButton(D.openSourceSettings, onFixSource)
                            }
                        }
                    } else {
                        WatchLiveCta(enabled = ui.liveReady, onClick = onWatchLive)
                        if (!ui.liveReady) Text(D.availableWhenReady, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 30.spx, maxLines = 2, modifier = Modifier.widthIn(max = 300.design))
                    }
                }
            }
        }
        val stepsCol: @Composable (Modifier) -> Unit = { m -> Column(m, verticalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 16.design)) { ui.steps.forEach { StepCard(it, D) } } }
        if (compact) Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            mainCol(Modifier.fillMaxWidth()); stepsCol(Modifier.fillMaxWidth())
        } else Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 40.dp else 120.design)) {
            mainCol(Modifier.responsiveWidth(820)); stepsCol(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(48.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(D.nextOpenInstant, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(24.design))
            if (!touch) KeyHint("OK", D.hintWatch)
        }
    }
}

private fun kindLabel(W: com.ultratv.tv.nativeapp.i18n.WizardStrings, kind: String) = when (kind) {
    "XTREAM" -> W.kindXtream; "M3U" -> W.kindM3u; "M3U_LOCAL" -> W.kindM3uFile; else -> ""
}

private fun countText(D: DesignStrings, part: SyncPart, n: String) = when (part) {
    SyncPart.LIVE -> D.countChannels; SyncPart.VOD -> D.countMovies; SyncPart.SERIES -> D.countSeries; SyncPart.EPG -> D.countProgrammes
}.format(n)

private fun etaText(D: DesignStrings, seconds: Int): String =
    if (seconds < 60) D.etaLessThanMinute else D.etaMinutes.format((seconds + 59) / 60)

/** CTA « Regarder le direct » : focalisé par défaut ; grisé (mais focalisable) tant que les chaînes ne sont pas prêtes. */
@Composable
private fun WatchLiveCta(enabled: Boolean, onClick: () -> Unit) {
    val D = LocalDs.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    RequestInitialFocus(requester, hasFocus = { focused })
    FocusSurface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(44.design),
        bg = if (enabled) Ux.Cta else Ux.Surface,
        focusedBg = if (enabled) Ux.Cta else Ux.Surface2,
        modifier = Modifier.height(88.design).focusRequester(requester).onFocusChanged { focused = it.isFocused },
    ) { f ->
        val ink = if (enabled) Ux.TextOnLight else Ux.Text3
        Row(Modifier.padding(horizontal = 48.design).height(88.design), verticalAlignment = Alignment.CenterVertically) {
            DIcon(Icons.Play, 26.design, ink, fill = true)
            Spacer(Modifier.width(14.design))
            Text(D.watchLive, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1)
        }
    }
}

@Composable
private fun StepCard(step: StepUi, D: DesignStrings) {
    val nf = remember { NumberFormat.getIntegerInstance() }
    val done = step.state == StepState.DONE
    val active = step.state == StepState.ACTIVE
    val error = step.state == StepState.ERROR
    val (label, icon) = when (step.part) {
        SyncPart.LIVE -> D.stepLive to Icons.Live
        SyncPart.VOD -> D.stepMovies to Icons.Movies
        SyncPart.SERIES -> D.stepSeries to Icons.Series
        SyncPart.EPG -> D.stepGuide to Icons.Guide
    }
    val meta = when {
        error -> D.stateFailed
        step.state == StepState.WAIT -> D.stateWaiting
        step.count != null -> countText(D, step.part, nf.format(step.count))
        done -> if (step.total != null) countText(D, step.part, nf.format(step.total)) else D.stateDone
        else -> ""
    }
    val ring = when { active -> Ux.Accent; error -> Color(0xFFFF5A5A); else -> Color.Transparent }
    Row(
        Modifier.fillMaxWidth().height(120.design).clip(RoundedCornerShape(24.design))
            .background(if (active || error) Ux.Surface else Ux.SurfaceDeep)
            .then(if (active || error) Modifier.border(2.design, ring, RoundedCornerShape(24.design)) else Modifier)
            .padding(horizontal = 32.design),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(60.design).clip(CircleShape).background(when { done -> Ux.Surface2; active -> Ux.Accent; error -> Color(0xFF5A1A1E); else -> Ux.Surface }),
            contentAlignment = Alignment.Center,
        ) { DIcon(if (done) Icons.Check else icon, 30.design, if (done || active || error) Ux.White else Ux.Muted, strokeWidth = 2.5f) }
        Spacer(Modifier.width(24.design))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.design)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(label, color = if (step.state == StepState.WAIT) Ux.Text2 else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.design))
                Text(meta, color = if (active) Ux.Text else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(8.design).clip(RoundedCornerShape(4.design)).background(Ux.Surface2)) {
                if (done) Box(Modifier.fillMaxSize().background(Ux.Text))
                else if (active) IndeterminateSegment()
            }
        }
    }
}

/** Total inconnu pendant le flux : segment qui défile de gauche à droite (rien d'inventé). Statique et discret en low-RAM. */
@Composable
private fun IndeterminateSegment() {
    val lowRam = com.ultratv.tv.nativeapp.ui.common.LocalLowRam.current
    if (lowRam) { Box(Modifier.fillMaxSize().background(Ux.Surface2)); return }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "seg")
    val x by t.animateFloat(-0.3f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.LinearEasing)), label = "x")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(4.design))) {
        Box(Modifier.fillMaxHeight().width(maxWidth * 0.3f).offset(x = maxWidth * x).background(Ux.Accent))
    }
}

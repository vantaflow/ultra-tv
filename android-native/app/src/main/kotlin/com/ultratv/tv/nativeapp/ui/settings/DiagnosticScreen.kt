package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.adaptive.NetQuality
import com.ultratv.tv.nativeapp.adaptive.NetType
import com.ultratv.tv.nativeapp.adaptive.Tier
import com.ultratv.tv.nativeapp.adaptive.UiEffects
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/** Diagnostic (maquette Diagnostic.dc.html) : ce que l'app a mesuré (appareil, connexion) et les réglages automatiques appliqués. */
@Composable
fun DiagnosticScreen(panes: SettingsPanesViewModel = hiltViewModel()) {
    val D = LocalDs.current
    val ad by panes.adaptive.state.collectAsState()
    val p by panes.state.collectAsState()
    val i = ad.device.info
    val n = ad.net
    fun gb(mb: Int) = "%.1f".format(java.util.Locale.ROOT, mb / 1024f).removeSuffix(".0")
    val codecs = listOfNotNull(i.h264?.let { "H.264" }, i.hevc?.let { "HEVC" }, i.vp9?.let { "VP9" }, i.av1?.let { "AV1" }).joinToString(" · ").ifEmpty { "—" }
    val manualAny = p.playerEngine != "auto" || p.decoderMode != "auto" || p.bufferPreset != "auto"
    Column(Modifier.fillMaxSize().padding(start = 72.design, end = 96.design, top = 54.design), verticalArrangement = Arrangement.spacedBy(32.design)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
                Text(D.diagnostic, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 1)
                Text("Ultra TV ${com.ultratv.tv.nativeapp.BuildConfig.VERSION_NAME} · ${D.diagnosticHint}", color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                PillButton(D.reevaluate, { panes.adaptive.reevaluate() }, bg = Ux.Surface2)
                PillButton(D.backToAuto, { panes.backToAuto() }, bg = Ux.Surface2)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.design)) {
            Card(Modifier.weight(1f), D.device, when (ad.device.tier) { Tier.LOW -> D.tierLow; Tier.MID -> D.tierMid; Tier.HIGH -> D.tierHigh }, Ux.Muted2, listOf(
                D.memory to "${gb(i.ramMb)} Go · " + (if (i.lowRamFlag || i.ramMb < 1800) D.weak else D.comfortable),
                D.processor to "${i.cores} " + D.cores + " · " + (if (i.is64Bit) "64" else "32") + " bits",
                D.hwDecoders to codecs, D.screen to "${i.screenWidth}×${i.screenHeight}", "Android" to "${i.sdk}",
            ))
            Card(Modifier.weight(1f), D.connection, when (ad.quality) { NetQuality.POOR -> D.qPoor; NetQuality.FAIR -> D.qFair; NetQuality.GOOD -> D.qGood; NetQuality.EXCELLENT -> D.qExcellent }, Color(0xFF15803D), listOf(
                D.type to when (n.type) { NetType.ETHERNET -> "Ethernet"; NetType.WIFI -> "Wi-Fi"; NetType.MOBILE -> D.mobile; NetType.OTHER -> "—"; NetType.NONE -> "—" },
                D.measuredRate to (n.measuredKbps?.let { "%.0f Mb/s".format(java.util.Locale.ROOT, it / 1000f) } ?: "—"),
                D.latency to (n.ttfbMs?.let { "$it ms" } ?: "—"), D.cuts to "${n.rebuffersRecent}", D.metered to if (n.metered) D.yes else D.no,
            ))
            val a = ad.auto
            Card(Modifier.weight(1f), D.autoSettings, if (manualAny) D.manualOverride else D.activeBadge, Ux.Accent, listOf(
                D.playerRow to "${if (p.playerEngine == "vlc") "VLC" else "ExoPlayer"} · ${if (p.decoderMode == "sw") D.software else D.hardware}".lowercase().replaceFirstChar { it.uppercase() },
                D.bufferRow to a.bufferPreset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                D.maxQuality to "${a.maxVideoHeight}p",
                D.animations to when (a.uiEffects) { UiEffects.FULL -> D.full; UiEffects.REDUCED -> D.reduced; UiEffects.NONE -> D.none },
                D.guideKept to "−${a.epgBackHours} h / +${a.epgForwardHours} h",
            ))
        }
    }
}

@Composable
private fun Card(modifier: Modifier, title: String, badge: String, badgeColor: Color, rows: List<Pair<String, String>>) {
    Column(modifier.clip(RoundedCornerShape(28.design)).background(Ux.Surface).padding(32.design), verticalArrangement = Arrangement.spacedBy(12.design)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1)
            Box(Modifier.clip(RoundedCornerShape(20.design)).background(badgeColor).padding(horizontal = 16.design, vertical = 6.design)) { Text(badge.uppercase(), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1) }
        }
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.design), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, modifier = Modifier.weight(1f))
                Text(v, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

package com.ultratv.tv.nativeapp.ui.player.timeshift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.player.PlayerExtraStrings

/** « −mm:ss » ou « −h:mm:ss ». */
fun behindLabel(sec: Int): String {
    val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
    return "−" + if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Pastille d'en-tête « EN DIFFÉRÉ · −12:40 » (maquette Timeshift) à la place du badge EN DIRECT. */
@Composable
fun TimeshiftBadge(behindSec: Int, X: PlayerExtraStrings) {
    Text(
        X.delayed + " · " + behindLabel(behindSec), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = 1.2.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.design)).background(Ux.Surface2).padding(horizontal = 14.design, vertical = 6.design),
    )
}

/**
 * Pied de lecteur en pause du direct (maquette Timeshift) : barre avec repère DIRECT, −30 / pause / +30, « Revenir au direct ».
 * La fenêtre va de « maintenant − fenêtre » à « maintenant ».
 */
@Composable
fun TimeshiftFooter(
    snap: TimeshiftSnapshot, playing: Boolean, X: PlayerExtraStrings, pauseFocus: FocusRequester,
    onToggle: () -> Unit, onJump: (Int) -> Unit, onLive: () -> Unit,
) {
    val now = System.currentTimeMillis()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xE00A0A0C)).padding(start = 96.design, end = 96.design, top = 40.design, bottom = 54.design),
            verticalArrangement = Arrangement.spacedBy(26.design),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
                Text(EpgClock.wall(now - snap.windowSec * 1000L), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
                BoxWithConstraints(Modifier.weight(1f).height(40.design)) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(12.design).clip(RoundedCornerShape(6.design)).background(Ux.Line))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(snap.fraction).height(12.design).clip(RoundedCornerShape(6.design)).background(Ux.Accent))
                    Box(Modifier.align(Alignment.CenterStart).offset(x = maxWidth * snap.fraction - 16.design).size(32.design).clip(CircleShape).background(Color.White))
                    Text(X.directMark, color = Ux.Accent, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, letterSpacing = 0.7.sp, maxLines = 1, modifier = Modifier.align(Alignment.TopEnd))
                }
                Text(EpgClock.wall(now), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.design), verticalAlignment = Alignment.CenterVertically) {
                    JumpButton("−30") { onJump(-30) }
                    FocusSurface(onClick = onToggle, shape = CircleShape, bg = Ux.Cta, modifier = Modifier.size(96.design).focusRequester(pauseFocus)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (playing) DIcon("M7 4h3.5v16H7zM13.5 4H17v16h-3.5z", 36.design, Ux.TextOnLight, fill = true) else DIcon("M7 4v16l13-8z", 36.design, Ux.TextOnLight, fill = true)
                        }
                    }
                    JumpButton("+30") { onJump(30) }
                }
                PillButton(X.backToLive, onLive, bg = Ux.Accent, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun JumpButton(label: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, bg = Ux.Surface2, modifier = Modifier.size(72.design)) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1)
        }
    }
}

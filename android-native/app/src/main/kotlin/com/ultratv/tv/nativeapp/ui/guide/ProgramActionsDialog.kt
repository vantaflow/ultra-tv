package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.actRecord
import com.ultratv.tv.nativeapp.i18n.actRecordHint
import com.ultratv.tv.nativeapp.i18n.actRecordSeries
import com.ultratv.tv.nativeapp.i18n.actRecordSeriesHint
import com.ultratv.tv.nativeapp.i18n.actRecordThis
import com.ultratv.tv.nativeapp.i18n.actRemind
import com.ultratv.tv.nativeapp.i18n.actRemindHint
import com.ultratv.tv.nativeapp.i18n.actReplay
import com.ultratv.tv.nativeapp.i18n.actReplayHint
import com.ultratv.tv.nativeapp.i18n.actReplayNone
import com.ultratv.tv.nativeapp.i18n.actWatchChannel
import com.ultratv.tv.nativeapp.i18n.actWatchChannelHint
import com.ultratv.tv.nativeapp.i18n.recConnectionBusy
import com.ultratv.tv.nativeapp.i18n.replayBadge
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/** Ce que le dialogue propose pour un programme à un instant donné (logique pure, testée). */
data class ProgramActionState(
    val replayEnabled: Boolean,
    val remindEnabled: Boolean,
    val recordEnabled: Boolean,
)

fun programActionState(prog: EpgEntity, replayUrl: String?, nowMs: Long): ProgramActionState = ProgramActionState(
    replayEnabled = replayUrl != null,
    remindEnabled = prog.startMs > nowMs,
    recordEnabled = prog.endMs > nowMs,
)

/** Dialogue « programme » du guide (maquette GuideActions) : Revoir, Regarder la chaîne, Me rappeler, Enregistrer. */
@Composable
fun ProgramActionsDialog(
    channel: ChannelEntity,
    prog: EpgEntity,
    state: ProgramActionState,
    D: DesignStrings,
    recordingRunning: Boolean,
    onReplay: () -> Unit,
    onWatch: () -> Unit,
    onRemind: () -> Unit,
    onRecord: (wholeSeries: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var choosingRecord by remember { mutableStateOf(false) }
    // Le choix « ce programme / série » remplace les 4 boutons : sans focus explicite, il filerait dans la barre latérale.
    val recordFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    ModalFocusScope(onBack = { if (choosingRecord) choosingRecord = false else onDismiss() }, modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.width(1040.design).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(52.design), verticalArrangement = Arrangement.spacedBy(22.design)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                if (channel.catchupDays > 0) Text(D.replayBadge(channel.catchupDays), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(8.design)).background(Ux.Surface2).padding(horizontal = 12.design, vertical = 6.design))
                Text("${channel.title} · ${EpgClock.hm(prog.startMs)} – ${EpgClock.hm(prog.endMs)}", color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(prog.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            prog.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 36.spx, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (recordingRunning) Text(D.recConnectionBusy, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
            if (!choosingRecord) {
                Column(Modifier.padding(top = 8.design), verticalArrangement = Arrangement.spacedBy(14.design)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.design)) {
                        ActionButton(D.actReplay, if (state.replayEnabled) D.actReplayHint else D.actReplayNone, Icons.Play, state.replayEnabled, Modifier.weight(1f), onReplay)
                        ActionButton(D.actWatchChannel, D.actWatchChannelHint, Icons.Live, true, Modifier.weight(1f), onWatch)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.design)) {
                        ActionButton(D.actRemind, D.actRemindHint, BELL, state.remindEnabled, Modifier.weight(1f), onRemind)
                        ActionButton(D.actRecord, D.actRecordHint, Icons.Record, state.recordEnabled, Modifier.weight(1f)) { choosingRecord = true }
                    }
                }
            } else {
                // Dans la couche modale : le focus est pris APRÈS la composition du bouton (sinon il part dans la barre latérale).
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    repeat(20) { if (runCatching { recordFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; kotlinx.coroutines.delay(50) }
                }
                Row(Modifier.padding(top = 8.design), horizontalArrangement = Arrangement.spacedBy(14.design)) {
                    ActionButton(D.actRecordThis, "${EpgClock.hm(prog.startMs)} – ${EpgClock.hm(prog.endMs)}", Icons.Record, true, Modifier.weight(1f).focusRequester(recordFocus)) { onRecord(false) }
                    ActionButton(D.actRecordSeries, D.actRecordSeriesHint, Icons.Record, true, Modifier.weight(1f)) { onRecord(true) }
                }
            }
        }
    }
}

private const val BELL = "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 0 0 4 0"

@Composable
private fun ActionButton(label: String, hint: String, icon: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = { if (enabled) onClick() }, shape = RoundedCornerShape(20.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1f,
        modifier = modifier.height(92.design).alpha(if (enabled) 1f else 0.45f),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            DIcon(icon, 28.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.2f)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 23.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

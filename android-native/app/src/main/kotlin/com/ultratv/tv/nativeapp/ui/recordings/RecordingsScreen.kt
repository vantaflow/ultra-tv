package com.ultratv.tv.nativeapp.ui.recordings

import android.os.StatFs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.RecordingEntity
import com.ultratv.tv.nativeapp.data.recording.RecordingRepository
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.recScheduledBadge
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val repo: RecordingRepository,
    private val scheduler: com.ultratv.tv.nativeapp.data.recording.RecordingScheduler,
) : ViewModel() {
    val items: StateFlow<List<RecordingEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    /** « Arrêter » : conserve ce qui est déjà enregistré. */
    fun stop(id: Long) {
        viewModelScope.launch { repo.stop(id) }
    }

    /** « Annuler » un enregistrement programmé : désarme l'alarme et supprime la ligne. */
    fun cancelScheduled(id: Long) {
        viewModelScope.launch { scheduler.cancel(id) }
    }

    /** Action de la ligne selon son état : arrêter (en cours), annuler (programmé), supprimer (échec). */
    fun act(r: RecordingEntity) = when (r.status) {
        "running" -> stop(r.id)
        "scheduled" -> cancelScheduled(r.id)
        else -> remove(r.id)
    }
}

/** Enregistrements (maquette) : stockage en tête, « en cours et programmés » en lignes de 120 px, « terminés » en grille 4×16:9. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsScreen(
    onPlayLocal: (filePath: String, title: String) -> Unit,
    vm: RecordingsViewModel = hiltViewModel(),
) {
    val list by vm.items.collectAsState()
    val S = LocalStrings.current
    val D = LocalDs.current
    val ctx = LocalContext.current
    // En cours d'abord, puis les programmés par heure de début, puis le reste (file, échecs).
    val active = list.filter { it.status != "done" }.sortedWith(compareBy({ rank(it.status) }, { it.scheduledStartMs }))
    val done = list.filter { it.status == "done" }
    val used = list.sumOf { if (it.status == "done") it.totalBytes.coerceAtLeast(it.downloadedBytes) else it.downloadedBytes }
    val free = remember(list) { runCatching { StatFs((ctx.getExternalFilesDir(null) ?: ctx.filesDir).path).availableBytes }.getOrDefault(0L) }
    val total = used + free

    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val widthDp = com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current
    Column(
        Modifier.fillMaxSize().background(Ux.Bg).then(if (touch) Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp) else Modifier.padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design)),
        verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 28.design),
    ) {
        @Composable fun meter() {
            Column(if (touch) Modifier.fillMaxWidth() else Modifier.width(520.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(D.storageUsed, color = Ux.Text2, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1)
                    Text(D.storageOf(formatBytes(used), formatBytes(total)), color = Ux.Text2, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(10.design).clip(RoundedCornerShape(5.design)).background(Ux.Surface2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f).background(Ux.Text))
                }
            }
        }
        if (touch) {
            Text(S.recordingsTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 1)
            meter()
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle(S.recordingsTitle, 48)
            meter()
        }
        if (list.isEmpty()) {
            Text(S.recordingsEmpty, color = Ux.Text3, fontFamily = Manrope, fontSize = 26.spx)
            return@Column
        }
        if (active.isNotEmpty()) {
            GroupLabel(D.recActive)
            LazyColumn(Modifier.heightIn(max = 380.design), verticalArrangement = Arrangement.spacedBy(14.design)) {
                items(active.size) { i -> ActiveRow(active[i], S, D) { vm.remove(active[i].id) } }
            }
        }
        if (done.isNotEmpty()) {
            GroupLabel(D.recDone)
            LazyVerticalGrid(GridCells.Fixed(if (touch) com.ultratv.tv.nativeapp.ui.mobile.gridColumns(widthDp - 40f - (if (widthDp >= 600f) 88f else 0f), 160f) else 4), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 24.design), verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 24.design)) {
                items(done, key = { it.id }) { r ->
                    FocusSurface(
                        onClick = { onPlayLocal("file://${r.filePath}", r.title) }, onLongClick = { vm.remove(r.id) },
                        shape = RoundedCornerShape(18.design), bg = Color.Transparent, focusedBg = Color.Transparent, ringWidth = 5.design, focusedScale = 1f,
                    ) { _ ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
                            ThumbImage(null, r.title, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 18)
                            Text(r.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(r.completedAt ?: r.createdAt))
                            Text("$date · ${formatBytes(r.totalBytes)}", color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRow(r: RecordingEntity, S: com.ultratv.tv.nativeapp.i18n.Strings, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, onAction: () -> Unit) {
    val running = r.status == "running"
    val scheduled = r.status == "scheduled"
    val failed = r.status == "failed" || r.status == "error"
    val fraction = if (r.totalBytes > 0) (r.downloadedBytes.toFloat() / r.totalBytes).coerceIn(0f, 1f) else 0f
    val meta = when {
        scheduled -> scheduledMeta(r)
        running -> "${(fraction * 100).toInt()} % · ${formatBytes(r.downloadedBytes)} / ${formatBytes(r.totalBytes)}"
        failed -> S.recordingStatusFailed        // jamais le message brut : il peut contenir une URL de flux
        r.status == "cancelled" -> S.recordingStatusCancelled
        else -> S.recordingStatusQueued
    }
    FocusSurface(onClick = onAction, shape = RoundedCornerShape(20.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(120.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.design)) {
            Box(
                Modifier.width(92.design).height(44.design).clip(RoundedCornerShape(10.design)).background(if (running) Ux.Accent else if (failed) Ux.Muted2 else Ux.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (running) D.recBadge else if (scheduled) D.recScheduledBadge else if (failed) D.recFailedBadge else D.recQueuedBadge, color = if (running || failed) Ux.White else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.design)) {
                Text(r.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressLine(fraction, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.design)), heightPx = 6, track = if (f) Ux.OnFocus2.copy(alpha = 0.4f) else Ux.Surface2)
            }
            Text(if (running) D.recStop else if (failed) S.delete else D.recCancel, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
        }
    }
}

private fun rank(status: String) = when (status) { "running" -> 0; "queued" -> 1; "scheduled" -> 2; else -> 3 }

/** « 20:30 – 22:30 · Chaîne » avec la date quand ce n'est pas aujourd'hui. */
private fun scheduledMeta(r: RecordingEntity): String {
    val start = Date(r.scheduledStartMs)
    val sameDay = DateFormat.getDateInstance(DateFormat.SHORT).format(start) == DateFormat.getDateInstance(DateFormat.SHORT).format(Date())
    val tf = DateFormat.getTimeInstance(DateFormat.SHORT)
    val day = if (sameDay) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(start) + " "
    return day + tf.format(start) + " – " + tf.format(Date(r.scheduledEndMs)) + (r.channelName?.let { " · $it" } ?: "")
}

/** Taille lisible (Ko / Mo / Go) sans dépendre de la locale pour l'unité. */
fun formatBytes(b: Long): String = when {
    b <= 0 -> "—"
    b < 1024L * 1024 -> "${(b / 1024).coerceAtLeast(1)} Ko"
    b < 1024L * 1024 * 1024 -> "${b / (1024 * 1024)} Mo"
    else -> String.format(java.util.Locale.getDefault(), "%.1f Go", b / (1024.0 * 1024 * 1024))
}

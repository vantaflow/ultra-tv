package com.ultratv.tv.nativeapp.ui.downloads

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.downloads.DownloadEntity
import com.ultratv.tv.nativeapp.data.downloads.DownloadRepository
import com.ultratv.tv.nativeapp.data.downloads.DownloadState
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.GroupLabel
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.SectionTitle
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.recordings.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Octets occupés par les téléchargements (terminés : taille finale ; en cours : déjà écrits). */
fun usedBytes(list: List<DownloadEntity>): Long =
    list.sumOf { if (it.state == DownloadState.COMPLETED) it.totalBytes.coerceAtLeast(it.doneBytes) else it.doneBytes }

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository,
    private val playback: PlaybackContext,
) : ViewModel() {
    data class Ui(val active: List<DownloadEntity>, val done: List<DownloadEntity>, val used: Long, val free: Long)

    /** Lecture de la base SEULEMENT (aucun réseau) : l'écran fonctionne hors ligne. */
    val ui: StateFlow<Ui?> = repo.observeAll().map { list ->
        Ui(
            active = list.filter { it.state != DownloadState.COMPLETED }.sortedWith(compareBy({ rank(it.state) }, { it.createdAt })),
            done = list.filter { it.state == DownloadState.COMPLETED },
            used = usedBytes(list),
            free = withContext(Dispatchers.IO) { repo.freeBytes() }.let { if (it == Long.MAX_VALUE) 0L else it },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pause(d: DownloadEntity) = viewModelScope.launch { repo.pause(d.id) }
    fun resume(d: DownloadEntity) = viewModelScope.launch { repo.resume(d.id) }
    fun retry(d: DownloadEntity) = viewModelScope.launch { repo.retry(d.id) }
    fun delete(d: DownloadEntity) = viewModelScope.launch { repo.delete(d.id) }

    /**
     * Lecture du fichier local. Le contexte de lecture garde l'URL DISTANTE du catalogue (historique, reprise, Trakt
     * identiques à une lecture en ligne ; le lecteur la remplace par le fichier via OfflineMedia) ; si le titre a
     * disparu du catalogue, le fichier lui-même.
     */
    fun play(d: DownloadEntity, s: DownloadStrings, onPlay: (url: String, title: String) -> Unit) {
        viewModelScope.launch {
            val local = File(d.filePath)
            val exists = withContext(Dispatchers.IO) { local.isFile }
            if (!exists) { Toaster.err(s.storageError); return@launch }
            val remote = withContext(Dispatchers.IO) { runCatching { repo.currentUrl(d) }.getOrNull() }
            val url = remote?.takeIf { it.isNotBlank() } ?: "file://${local.absolutePath}"
            playback.set(
                PlaybackContext.Item(
                    providerId = d.providerId, kind = d.kind, remoteId = d.remoteId, title = d.title, poster = d.poster,
                    streamUrl = url, parentRemoteId = d.parentRemoteId,
                ),
            )
            onPlay(url, d.title)
        }
    }

    private fun rank(state: String) = when (state) {
        DownloadState.RUNNING -> 0
        DownloadState.QUEUED -> 1
        DownloadState.PAUSED -> 2
        else -> 3
    }
}

/** Téléchargements (TV et mobile) : stockage en tête, « En cours » puis « Disponibles hors ligne ». */
@Composable
fun DownloadsScreen(onPlay: (url: String, title: String) -> Unit, vm: DownloadsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val s = rememberDownloadStrings()
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val state = ui
    Column(
        Modifier.fillMaxSize().background(Ux.Bg).then(
            if (touch) Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp) else Modifier.padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design),
        ),
        verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 28.design),
    ) {
        val meter: @Composable () -> Unit = {
            val used = state?.used ?: 0L
            val free = state?.free ?: 0L
            val total = used + free
            Column(if (touch) Modifier.fillMaxWidth() else Modifier.width(560.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.storageUsed, color = Ux.Text2, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1)
                    Text(s.storageFree(formatBytes(used), formatBytes(free)), color = Ux.Text2, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(10.design).clip(RoundedCornerShape(5.design)).background(Ux.Surface2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f).background(Ux.Accent))
                }
            }
        }
        if (touch) {
            Text(s.navTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 1)
            meter()
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle(s.navTitle, 48)
            meter()
        }
        if (state == null) return@Column
        if (state.active.isEmpty() && state.done.isEmpty()) {
            Text(s.empty, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 14.sp else 26.spx)
            return@Column
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 14.design)) {
            if (state.active.isNotEmpty()) {
                item(key = "h-active") { GroupLabel(s.sectionActive) }
                items(state.active, key = { it.id }) { d -> DownloadRow(d, s, touch, vm, onPlay) }
            }
            if (state.done.isNotEmpty()) {
                item(key = "h-done") { Box(Modifier.padding(top = if (touch) 8.dp else 16.design)) { GroupLabel(s.sectionDone) } }
                items(state.done, key = { it.id }) { d -> DownloadRow(d, s, touch, vm, onPlay) }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadEntity, s: DownloadStrings, touch: Boolean, vm: DownloadsViewModel, onPlay: (String, String) -> Unit) {
    val done = d.state == DownloadState.COMPLETED
    val pct = percentOf(d)
    val meta = when (d.state) {
        DownloadState.COMPLETED -> formatBytes(d.totalBytes.coerceAtLeast(d.doneBytes))
        DownloadState.RUNNING -> listOfNotNull(pct?.let { "$it %" }, "${formatBytes(d.doneBytes)} / ${formatBytes(d.totalBytes.takeIf { it > 0 } ?: -1)}").joinToString(" · ")
        DownloadState.QUEUED -> s.queued
        DownloadState.PAUSED -> listOfNotNull(s.paused, pct?.let { "$it %" }).joinToString(" · ")
        DownloadState.FAILED -> s.error(d.error)
        else -> ""
    }
    val primary: () -> Unit = when (d.state) {
        DownloadState.COMPLETED -> { { vm.play(d, s, onPlay) } }
        DownloadState.RUNNING, DownloadState.QUEUED -> { { vm.pause(d) } }
        DownloadState.PAUSED -> { { vm.resume(d) } }
        else -> { { vm.retry(d) } }
    }
    val primaryLabel = when (d.state) {
        DownloadState.COMPLETED -> s.play
        DownloadState.RUNNING, DownloadState.QUEUED -> s.pause
        DownloadState.PAUSED -> s.resume
        else -> s.retry
    }
    val h = if (touch) 88.dp else 132.design
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 20.design)) {
        FocusSurface(onClick = primary, shape = RoundedCornerShape(if (touch) 14.dp else 20.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.weight(1f).height(h)) { f ->
            Row(Modifier.fillMaxSize().padding(horizontal = if (touch) 10.dp else 20.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 24.design)) {
                // Affiche en cache seulement (kind = null : pas de recherche TMDB, l'écran doit marcher hors ligne).
                PosterImage(d.poster, d.title, Modifier.height(if (touch) 68.dp else 104.design).aspectRatio(2f / 3f), radius = 10, kind = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (touch) 4.dp else 8.design)) {
                    Text(d.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 15.sp else 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(meta, color = if (f) Ux.OnFocus2 else if (d.state == DownloadState.FAILED) Ux.AccentInk else Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!done) ProgressLine((pct ?: 0) / 100f, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.design)), heightPx = 6, track = if (f) Ux.OnFocus2.copy(alpha = 0.4f) else Ux.Surface2)
                }
                Text(primaryLabel, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 13.sp else 22.spx, maxLines = 1)
            }
        }
        // Supprimer (terminé) ou annuler (en cours) : ligne + fichiers.
        PillButton(if (done) s.delete else s.cancel, onClick = { vm.delete(d) }, heightPx = if (touch) 48 else 72, hPadPx = if (touch) 20 else 32, fontPx = if (touch) 22 else 24)
    }
}

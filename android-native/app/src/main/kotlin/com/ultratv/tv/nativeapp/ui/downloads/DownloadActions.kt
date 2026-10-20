package com.ultratv.tv.nativeapp.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.downloads.DownloadConnectionPolicy
import com.ultratv.tv.nativeapp.data.downloads.DownloadEntity
import com.ultratv.tv.nativeapp.data.downloads.DownloadKind
import com.ultratv.tv.nativeapp.data.downloads.DownloadRepository
import com.ultratv.tv.nativeapp.data.downloads.DownloadState
import com.ultratv.tv.nativeapp.data.downloads.EnqueueResult
import com.ultratv.tv.nativeapp.data.downloads.OfflineMedia
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Libellés de la langue de l'application (comme LocalDs, langue « Système » déjà résolue). */
@Composable
fun rememberDownloadStrings(): DownloadStrings {
    val lang = LocalDs.current.lang
    return remember(lang) { DownloadStrings(lang) }
}

object DownloadIcons {
    const val Download = "M12 4v12M7 11l5 5 5-5M5 20h14"
    const val Done = "M5 12l5 5 9-10"
    const val Pause = "M8 5v14M16 5v14"
    const val Retry = "M3 12a9 9 0 1 0 3-6.7M3 4v5h5"
}

/** Libellé d'un bouton « Télécharger » selon l'état de l'élément (fonction pure, testée). */
fun downloadLabel(d: DownloadEntity?, s: DownloadStrings): String = when (d?.state) {
    null -> s.download
    DownloadState.COMPLETED -> s.downloaded
    DownloadState.RUNNING -> percentOf(d)?.let { "$it %" } ?: s.sectionActive
    DownloadState.QUEUED -> s.queued
    DownloadState.PAUSED -> s.paused
    DownloadState.FAILED -> s.retry
    else -> s.download
}

/** Pourcentage téléchargé, ou null si la taille n'est pas (encore) connue. */
fun percentOf(d: DownloadEntity): Int? = if (d.totalBytes > 0) (d.doneBytes * 100 / d.totalBytes).toInt().coerceIn(0, 100) else null

fun downloadIcon(d: DownloadEntity?): String = when (d?.state) {
    DownloadState.COMPLETED -> DownloadIcons.Done
    DownloadState.RUNNING, DownloadState.QUEUED -> DownloadIcons.Pause
    DownloadState.FAILED -> DownloadIcons.Retry
    else -> DownloadIcons.Download
}

@HiltViewModel
class DownloadActionsViewModel @Inject constructor(
    private val repo: DownloadRepository,
    private val limits: com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore,
) : ViewModel() {
    fun item(providerId: Long, kind: String, remoteId: String): Flow<DownloadEntity?> = repo.observeItem(providerId, kind, remoteId)

    fun forSeries(series: SeriesEntity?): Flow<Map<String, DownloadEntity>> =
        if (series == null) flowOf(emptyMap()) else repo.observeForSeries(series.providerId, series.remoteId).map { l -> l.associateBy { it.remoteId } }

    private val running: StateFlow<Boolean> = repo.observeRunning().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun downloadMovie(m: MovieEntity, s: DownloadStrings) {
        viewModelScope.launch { report(repo.enqueueMovie(m), s); warnIfPlaying(m.providerId, s) }
    }

    fun downloadEpisode(series: SeriesEntity, ep: EpisodeEntity, s: DownloadStrings) {
        viewModelScope.launch { report(repo.enqueueEpisode(series, ep), s); warnIfPlaying(series.providerId, s) }
    }

    fun downloadSeason(series: SeriesEntity, eps: List<EpisodeEntity>, s: DownloadStrings) {
        viewModelScope.launch {
            val (added, refusal) = repo.enqueueEpisodes(series, eps)
            when {
                added > 0 -> { Toaster.ok(s.seasonQueued(added)); refusal?.let { report(it, s) } }
                refusal != null -> report(refusal, s)
                else -> Toaster.show(s.alreadyQueued)
            }
            if (added > 0) warnIfPlaying(series.providerId, s)
        }
    }

    /** Appui sur un élément déjà connu : pause / reprise / nouvel essai ; terminé = simple rappel. */
    fun act(d: DownloadEntity, s: DownloadStrings) {
        viewModelScope.launch {
            when (d.state) {
                DownloadState.RUNNING, DownloadState.QUEUED -> repo.pause(d.id)
                DownloadState.PAUSED -> repo.resume(d.id)
                DownloadState.FAILED -> repo.retry(d.id)
                else -> Toaster.show(s.downloaded)
            }
        }
    }

    /**
     * Avant une lecture EN LIGNE : un téléchargement en cours occupe peut-être la seule connexion de l'abonnement.
     * Rien à signaler si l'élément est lu depuis son fichier téléchargé.
     */
    fun warnBeforePlay(providerId: Long, kind: String, remoteId: String, s: DownloadStrings) {
        if (OfflineMedia.localPath(providerId, kind, remoteId) != null) return
        if (DownloadConnectionPolicy.warnOnPlay(running.value, limits.maxConnections(providerId))) Toaster.show(s.connectionBusyDownloading)
    }

    private fun warnIfPlaying(providerId: Long, s: DownloadStrings) {
        if (DownloadConnectionPolicy.warnOnDownload(com.ultratv.tv.nativeapp.ui.design.Ux.playerActive, limits.maxConnections(providerId))) {
            Toaster.show(s.connectionWhilePlaying)
        }
    }

    private suspend fun report(r: EnqueueResult, s: DownloadStrings) = when (r) {
        EnqueueResult.QUEUED -> Toaster.ok(s.queuedToast)
        EnqueueResult.ALREADY -> Toaster.show(s.alreadyQueued)
        EnqueueResult.NOT_DOWNLOADABLE -> Toaster.err(s.notDownloadable)
        EnqueueResult.NO_SPACE -> Toaster.err(s.noSpace)
        EnqueueResult.SOURCE_MISSING -> Toaster.err(s.sourceMissing)
    }
}

/** Bouton « Télécharger » de la fiche film (pilule TV ; tuile d'action sur mobile, voir [MovieDownloadTile]). */
@Composable
fun MovieDownloadPill(movie: MovieEntity, vm: DownloadActionsViewModel = hiltViewModel()) {
    val s = rememberDownloadStrings()
    val flow = remember(movie.providerId, movie.remoteId) { vm.item(movie.providerId, DownloadKind.MOVIE, movie.remoteId) }
    val d by flow.collectAsState(initial = null)
    PillButton(
        downloadLabel(d, s), onClick = { d?.let { vm.act(it, s) } ?: vm.downloadMovie(movie, s) },
        heightPx = 72, fontPx = 26, iconPath = downloadIcon(d),
    )
}

@Composable
fun MovieDownloadTile(movie: MovieEntity, modifier: Modifier, vm: DownloadActionsViewModel = hiltViewModel()) {
    val s = rememberDownloadStrings()
    val flow = remember(movie.providerId, movie.remoteId) { vm.item(movie.providerId, DownloadKind.MOVIE, movie.remoteId) }
    val d by flow.collectAsState(initial = null)
    com.ultratv.tv.nativeapp.ui.mobile.MobileActionTile(
        downloadIcon(d), downloadLabel(d, s), { d?.let { vm.act(it, s) } ?: vm.downloadMovie(movie, s) }, modifier,
        active = d?.state == DownloadState.COMPLETED,
    )
}

/** Petit bouton rond d'une ligne d'épisode : icône selon l'état, pourcentage pendant le téléchargement. */
@Composable
fun EpisodeDownloadButton(series: SeriesEntity, ep: EpisodeEntity, d: DownloadEntity?, vm: DownloadActionsViewModel = hiltViewModel()) {
    val s = rememberDownloadStrings()
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val size = if (touch) 44.dp else 72.design
    val label = downloadLabel(d, s)
    FocusSurface(
        onClick = { d?.let { vm.act(it, s) } ?: vm.downloadEpisode(series, ep, s) },
        shape = CircleShape, bg = if (d?.state == DownloadState.COMPLETED) Ux.Accent else Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1.05f,
        modifier = Modifier.size(size).semantics { contentDescription = label },
    ) { f ->
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            val pct = d?.takeIf { it.state == DownloadState.RUNNING }?.let { percentOf(it) }
            val ink = if (f) Ux.TextOnLight else if (d?.state == DownloadState.COMPLETED) Ux.OnAccent else Ux.Text
            if (pct != null) Text("$pct", color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1)
            else DIcon(downloadIcon(d), if (touch) 20.dp else 30.design, ink, strokeWidth = 2.2f)
        }
    }
}

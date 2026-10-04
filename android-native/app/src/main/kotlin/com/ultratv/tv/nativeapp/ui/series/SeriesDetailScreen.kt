package com.ultratv.tv.nativeapp.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.FavoriteButton
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*

/** Fiche série (maquette SerieDetail) : colonne gauche 560 px, saisons en onglets et épisodes 16:9 à droite. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    onPlayEpisode: (url: String, title: String) -> Unit,
    onBack: () -> Unit = {},
    vm: SeriesDetailViewModel = hiltViewModel(),
) {
    val s by vm.series.collectAsState()
    val eps by vm.episodes.collectAsState()
    val loading by vm.loading.collectAsState()
    val watched by vm.watched.collectAsState()
    LaunchedEffect(seriesId) { vm.load(seriesId) }

    val series = s
    val S = LocalStrings.current
    val D = LocalDs.current
    if (series == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(S.detailLoading, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx) }
        return
    }
    val seasons = remember(eps) { eps.map { it.season }.distinct().sorted() }
    val progress = remember(watched) { watched.associateBy { it.remoteId } }
    // Épisode à reprendre : le dernier regardé non terminé, sinon le suivant du dernier terminé, sinon le tout premier.
    val target: EpisodeEntity? = remember(eps, watched) { resumeTarget(eps, watched) }
    val resuming = target != null && progress[target.remoteId]?.let { it.positionMs > 0 } == true
    var season by remember(series.id) { mutableStateOf<Int?>(null) }
    val activeSeason = season ?: target?.season ?: seasons.firstOrNull()
    val shown = remember(eps, activeSeason) { eps.filter { it.season == activeSeason } }

    val playRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    RequestInitialFocus(playRequester, hasFocus = { focused }, key = target?.id ?: series.id)

    Row(
        Modifier.fillMaxSize().background(Ux.Bg).padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design),
        horizontalArrangement = Arrangement.spacedBy(64.design),
    ) {
        Column(Modifier.width(560.design).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.design)) {
            BackLink(S.seriesTitle, onBack)
            PosterImage(series.poster, series.name, Modifier.width(300.design).aspectRatio(2f / 3f), radius = 22)
            Text(series.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 56.spx, lineHeight = 59.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val bits = listOfNotNull(series.year?.toString(), if (seasons.isNotEmpty()) D.seasonsCount(seasons.size) else null, series.genre?.takeIf { it.isNotBlank() })
            Row(horizontalArrangement = Arrangement.spacedBy(14.design)) {
                bits.forEachIndexed { i, b ->
                    if (i > 0) Text("·", color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
                    Text(b, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            series.plot?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 33.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(20.design), verticalAlignment = Alignment.CenterVertically) {
                if (target != null) PillButton(
                    if (resuming) D.resumeEpisode(target.season, target.episode) else D.playEpisodeLabel(target.season, target.episode),
                    onClick = { vm.playEpisode(series.name, series.remoteId, series.providerId, target, onPlayEpisode) },
                    heightPx = 76, hPadPx = 40, fontPx = 26, weight = FontWeight.Bold, iconPath = Icons.Play, iconFill = true, bg = Ux.Cta,
                    modifier = Modifier.focusRequester(playRequester).onFocusChanged { focused = it.isFocused },
                )
                FavoriteButton(kind = "SERIES", remoteId = series.remoteId)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 44.design), verticalArrangement = Arrangement.spacedBy(20.design)) {
            if (seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                items(seasons, key = { it }) { n ->
                    val sel = n == activeSeason
                    FocusSurface(
                        onClick = { season = n }, shape = RoundedCornerShape(28.design), bg = if (sel) Ux.Cta else Ux.Surface,
                        modifier = Modifier.height(56.design),
                    ) { f ->
                        Box(Modifier.height(56.design).padding(horizontal = 28.design), contentAlignment = Alignment.Center) {
                            Text(D.seasonLabel(n), color = if (f || sel) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
                        }
                    }
                }
            }
            when {
                shown.isEmpty() && loading -> Text(S.detailLoading, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx)
                shown.isEmpty() -> Text(S.seriesNoEpisodes, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx)
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.design)) {
                    items(shown, key = { it.id }) { ep ->
                        EpisodeRow(ep, cleanEpisodeTitle(ep.title, series.name, series.title), progress[ep.remoteId], D) { vm.playEpisode(series.name, series.remoteId, series.providerId, ep, onPlayEpisode) }
                    }
                }
            }
        }
    }
}

/** Dernier épisode commencé et inachevé ; à défaut le premier non vu ; à défaut le premier. */
fun resumeTarget(eps: List<EpisodeEntity>, watched: List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>): EpisodeEntity? {
    if (eps.isEmpty()) return null
    val byRid = eps.associateBy { it.remoteId }
    watched.firstOrNull { it.positionMs > 0 && (it.durationMs == 0L || it.positionMs < it.durationMs - 60_000) }
        ?.let { byRid[it.remoteId] }?.let { return it }
    val sorted = eps.sortedWith(compareBy({ it.season }, { it.episode }))
    val seen = watched.map { it.remoteId }.toSet()
    return sorted.firstOrNull { it.remoteId !in seen } ?: sorted.first()
}

/** Titre d'épisode lisible : retire le nom de la série et le marqueur SxxExx que beaucoup de sources y répètent. */
fun cleanEpisodeTitle(raw: String, seriesName: String, seriesTitle: String): String {
    var t = raw
    for (n in listOf(seriesName, seriesTitle)) if (n.isNotBlank()) t = t.replace(n, "", ignoreCase = true)
    t = t.replace(Regex("\\bS\\d{1,2}\\s?E\\d{1,3}\\b", RegexOption.IGNORE_CASE), "")
    return t.trim().trim('-', '–', '·', ':', ' ').trim()
}

@Composable
internal fun BackLink(label: String, onBack: () -> Unit) {
    FocusSurface(onClick = onBack, shape = RoundedCornerShape(22.design), bg = androidx.compose.ui.graphics.Color.Transparent, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.height(48.design)) { f ->
        Row(Modifier.height(48.design).padding(horizontal = 12.design), verticalAlignment = Alignment.CenterVertically) {
            DIcon(Icons.Chevron, 20.design, if (f) Ux.TextOnLight else Ux.Text3, strokeWidth = 2.5f)
            Spacer(Modifier.width(8.design))
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1)
        }
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeEntity, title: String, h: com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity?, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, onClick: () -> Unit) {
    val dur = h?.durationMs ?: 0L
    val pos = h?.positionMs ?: 0L
    val done = dur > 0 && pos >= dur - 60_000
    val fraction = if (done) 1f else if (dur > 0) pos.toFloat() / dur else 0f
    val meta = when {
        done -> D.watchedLabel
        dur > 0 && pos > 0 -> D.remainingMin(((dur - pos) / 60_000L).toInt().coerceAtLeast(1))
        else -> ""
    }
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(150.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 20.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
            Box(Modifier.width(240.design).aspectRatio(16f / 9f)) {
                ThumbImage(ep.image, title.ifEmpty { ep.title }, Modifier.fillMaxSize(), radius = 14)
                if (fraction > 0f) ProgressLine(fraction, Modifier.align(Alignment.BottomStart).fillMaxWidth(), heightPx = 6, track = androidx.compose.ui.graphics.Color(0x66000000))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.design)) {
                Text(if (title.isEmpty()) D.episodeShort(ep.episode) else "${D.episodeShort(ep.episode)} · $title", color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) Text(meta, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 19.spx, maxLines = 1)
                ep.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 19.spx, lineHeight = 27.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

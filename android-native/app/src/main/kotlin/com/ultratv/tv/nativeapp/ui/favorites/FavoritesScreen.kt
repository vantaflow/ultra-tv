package com.ultratv.tv.nativeapp.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
    private val epgDao: EpgDao,
) : ViewModel() {

    private val providers = providerRepo.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val pid = providers.map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }

    val channels: StateFlow<List<ChannelEntity>> = pid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else catalog.favoriteChannels(id, 500)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Programme en cours par chaîne (id de chaîne → programme). */
    val nowPlaying: StateFlow<Map<Long, EpgEntity>> = channels.map { list ->
        if (list.isEmpty()) emptyMap() else {
            val now = System.currentTimeMillis()
            list.map { it.id }.chunked(500).flatMap { epgDao.rangeForChannels(it, now - 30 * 60_000, now + 60_000) }
                .filter { it.startMs <= now && it.endMs > now }.associateBy { it.channelId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val movies: StateFlow<List<MovieEntity>> = pid.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(emptyList())
        catalog.favoritesByKind(id, "MOVIE").flatMapLatest { favs ->
            catalog.movies(id).map { list ->
                val ids = favs.map { it.remoteId }.toSet()
                list.filter { it.remoteId in ids }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val series: StateFlow<List<SeriesEntity>> = pid.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(emptyList())
        catalog.favoritesByKind(id, "SERIES").flatMapLatest { favs ->
            catalog.seriesList(id).map { list ->
                val ids = favs.map { it.remoteId }.toSet()
                list.filter { it.remoteId in ids }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(kind: String, providerId: Long, remoteId: String) {
        viewModelScope.launch { catalog.setFavorite(providerId, kind, remoteId, false) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onOpenMovie: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onPlayChannel: (String, String) -> Unit = { _, _ -> },
    onBrowseLive: () -> Unit = {},
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val channels by vm.channels.collectAsState()
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val now by vm.nowPlaying.collectAsState()
    val S = LocalStrings.current
    val D = LocalDs.current
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf(
        D.favTab(S.navLive.let { D.channelsWord }, channels.size),
        D.favTab(S.moviesTitle, movies.size),
        D.favTab(S.seriesTitle, series.size),
    )

    Column(Modifier.fillMaxSize().background(Ux.Bg).padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design), verticalArrangement = Arrangement.spacedBy(32.design)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle(S.favorites, 48)
            Row(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                labels.forEachIndexed { i, l ->
                    val sel = i == tab
                    FocusSurface(onClick = { tab = i }, shape = RoundedCornerShape(28.design), bg = if (sel) Ux.Cta else Ux.Surface, modifier = Modifier.height(56.design)) { f ->
                        Box(Modifier.height(56.design).padding(horizontal = 28.design), contentAlignment = Alignment.Center) {
                            Text(l, color = if (f || sel) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
                        }
                    }
                }
            }
        }
        val empty = when (tab) { 0 -> channels.isEmpty(); 1 -> movies.isEmpty(); else -> series.isEmpty() }
        if (empty) {
            StateCard(D.emptyFavTitle, D.emptyFavBody, Icons.Heart, D.browseLive, onPrimary = onBrowseLive, badge = Ux.Surface2, modifier = Modifier.width(820.design))
        } else if (tab == 0) {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.design), verticalArrangement = Arrangement.spacedBy(24.design), contentPadding = PaddingValues(vertical = 8.design)) {
                items(channels, key = { it.id }) { c ->
                    ChannelTile(c, now[c.id], onClick = { onPlayChannel(c.streamUrl, c.name) }, onLong = { vm.remove("LIVE", c.providerId, c.remoteId) })
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(6), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.design), verticalArrangement = Arrangement.spacedBy(20.design), contentPadding = PaddingValues(vertical = 8.design)) {
                if (tab == 1) items(movies, key = { it.id }) { m -> VodCard(m.title, m.poster, Modifier, onClick = { onOpenMovie(m.id) }) }
                else items(series, key = { it.id }) { s -> VodCard(s.title, s.poster, Modifier, onClick = { onOpenSeries(s.id) }) }
            }
        }
        Text(D.favHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(c: ChannelEntity, now: EpgEntity?, onClick: () -> Unit, onLong: () -> Unit) {
    val fraction = if (now != null && now.endMs > now.startMs) ((System.currentTimeMillis() - now.startMs).toFloat() / (now.endMs - now.startMs)).coerceIn(0f, 1f) else 0f
    FocusSurface(onClick = onClick, onLongClick = onLong, shape = RoundedCornerShape(22.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1.04f, modifier = Modifier.height(196.design)) { f ->
        Column(Modifier.fillMaxSize().padding(24.design), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                LogoBox(c.logo, c.title, Modifier.width(88.design).height(56.design), radius = 12, pad = 6, bg = if (f) Ux.Surface else Ux.Surface2)
                if (c.seq > 0) Text("${c.seq}", color = if (f) Ux.OnFocus2 else Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now?.title.orEmpty(), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 19.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProgressLine(fraction, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.design)), heightPx = 6, track = if (f) Ux.OnFocus2.copy(alpha = 0.4f) else Ux.Surface2)
        }
    }
}

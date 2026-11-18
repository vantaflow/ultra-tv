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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
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
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.m3u.M3uExport
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
    private val hiddenStore: com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore,
    private val lockedStore: com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore,
    private val categoryDao: com.ultratv.tv.nativeapp.data.db.CategoryDao,
    private val zapQueue: com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue,
    private val playback: com.ultratv.tv.nativeapp.data.repo.PlaybackContext,
) : ViewModel() {

    private val providers = providerRepo.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Choix « Toutes les sources » du profil courant (mémorisé par profil, sur cet appareil). */
    val allSources: StateFlow<Boolean> = profiles.currentFavAll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Vrai quand le profil a au moins deux sources visibles : seulement alors la puce « Toutes les sources » est proposée. */
    val canMix: StateFlow<Boolean> = combine(providers, profiles.currentSources) { ps, names ->
        com.ultratv.tv.nativeapp.data.profile.FavoritesScope.canMix(com.ultratv.tv.nativeapp.data.profile.ProfileSourcesRules.visible(ps, names).size)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Sources lues : la source active, ou (vue mélangée) toutes les sources visibles du profil. */
    private val pids: Flow<List<Long>> = combine(providers, profiles.currentSources, profiles.currentFavAll) { ps, names, chosen ->
        com.ultratv.tv.nativeapp.data.profile.FavoritesScope.providerIds(ps, names, chosen)
    }.distinctUntilChanged()

    /** Nom de la source de chaque élément, seulement en vue mélangée (null = une seule source : aucune pastille). */
    val sourceNames: StateFlow<Map<Long, String>?> = combine(providers, pids) { ps, ids ->
        com.ultratv.tv.nativeapp.data.profile.FavoritesScope.sourceNames(ps, ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Au plus 500 chaînes au total (requête bornée, jamais le catalogue) ; en vue mélangée, catégories masquées par le profil retirées.
    val channels: StateFlow<List<ChannelEntity>> = pids.flatMapLatest { ids ->
        when {
            ids.isEmpty() -> flowOf(emptyList())
            ids.size == 1 -> catalog.favoriteChannels(ids[0], 500)
            else -> combine(catalog.favoriteChannelsIn(ids, 500), hiddenStore.hidden) { l, h ->
                com.ultratv.tv.nativeapp.data.profile.FavoritesScope.dropHidden(l, h)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Programme en cours par chaîne (id de chaîne → programme). */
    val nowPlaying: StateFlow<Map<Long, EpgEntity>> = channels.map { list ->
        if (list.isEmpty()) emptyMap() else {
            val now = System.currentTimeMillis()
            list.map { it.id }.chunked(500).flatMap { epgDao.rangeForChannels(it, now - 30 * 60_000, now + 60_000) }
                .filter { it.startMs <= now && it.endMs > now }.associateBy { it.channelId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Seuls les favoris sont lus (avant : les 180 000 films chargés puis filtrés, à chaque écriture de la synchro).
    private fun <T> perProvider(ids: List<Long>, one: (Long) -> Flow<List<T>>): Flow<List<T>> =
        if (ids.isEmpty()) flowOf(emptyList()) else combine(ids.map(one)) { parts -> parts.toList().flatten() }

    val movies: StateFlow<List<MovieEntity>> = pids.flatMapLatest { ids ->
        perProvider(ids) { id -> catalog.favoritesByKind(id, "MOVIE").flatMapLatest { favs -> catalog.moviesByRemoteIds(id, favs.map { it.remoteId }) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val series: StateFlow<List<SeriesEntity>> = pids.flatMapLatest { ids ->
        perProvider(ids) { id -> catalog.favoritesByKind(id, "SERIES").flatMapLatest { favs -> catalog.seriesByRemoteIds(id, favs.map { it.remoteId }) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAllSources(on: Boolean) { viewModelScope.launch { profiles.setFavAll(on) } }

    /**
     * Contenu M3U de TOUTES les chaînes favorites des sources affichées (pas seulement les 500 affichées), ou null s'il n'y a rien
     * d'exportable. Lu à la demande, hors du fil principal.
     */
    suspend fun exportM3u(): Pair<String, Int>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val ids = pids.first().ifEmpty { return@withContext null }
        val names = ids.associateWith { id -> catalog.categories(id, "LIVE").first().associate { it.remoteId to it.name } }
        val list = if (ids.size == 1) catalog.favoriteChannels(ids[0], 1_000_000).first() else catalog.favoriteChannelsIn(ids, 1_000_000).first()
        val entries = list.map {
            M3uExport.Entry(it.name, it.streamUrl, it.epgChannelId, it.logo, it.categoryId?.let { c -> names[it.providerId]?.get(c) }, it.num)
        }
        val n = M3uExport.count(entries)
        if (n == 0) null else M3uExport.build(entries) to n
    }

    /** La chaîne exige-t-elle le code parental (chaîne ou catégorie verrouillée) ? Lu au moment du clic. */
    suspend fun needsPin(c: ChannelEntity): Boolean {
        val cats = categoryDao.forProviderKind(c.providerId, "LIVE").filter { it.locked }.map { it.remoteId }.toSet()
        return com.ultratv.tv.nativeapp.data.parental.ParentalGate.isBlocked(c, lockedStore.locked.first(), cats, emptySet())
    }

    /**
     * Avant d'ouvrir le lecteur : en vue mélangée, la file de zapping devient la liste affichée (haut / bas passent d'une source
     * à l'autre) et le contexte de lecture porte la source de la chaîne. Vue d'une seule source : comportement inchangé.
     */
    fun prepare(c: ChannelEntity, shown: List<ChannelEntity>) {
        if (sourceNames.value == null) return
        zapQueue.set(shown, c)
        playback.set(com.ultratv.tv.nativeapp.data.repo.PlaybackContext.Item(c.providerId, "LIVE", c.remoteId, c.title, c.logo, c.streamUrl))
    }

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
    val allSources by vm.allSources.collectAsState()
    val canMix by vm.canMix.collectAsState()
    val srcNames by vm.sourceNames.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Chaîne verrouillée (elle-même ou sa catégorie) : code parental demandé avant d'ouvrir le lecteur.
    var pinFor by remember { androidx.compose.runtime.mutableStateOf<ChannelEntity?>(null) }
    val play: (ChannelEntity) -> Unit = { c ->
        scope.launch {
            if (vm.needsPin(c)) pinFor = c else { vm.prepare(c, channels); onPlayChannel(c.streamUrl, c.name) }
        }
    }
    val S = LocalStrings.current
    val D = LocalDs.current
    var tab by remember { mutableIntStateOf(0) }
    // Onglet 0 « Tout » (par défaut) : tous les favoris classés par section ; puis chaînes, films, séries.
    val labels = listOf(
        D.favTab(D.catAll, channels.size + movies.size + series.size),
        D.favTab(S.navLive.let { D.channelsWord }, channels.size),
        D.favTab(S.moviesTitle, movies.size),
        D.favTab(S.seriesTitle, series.size),
    )

    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val widthDp = com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current
    val usable = widthDp - 40f - (if (widthDp >= 600f) 88f else 0f)
    Column(
        Modifier.fillMaxSize().background(Ux.Bg).then(if (touch) Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp) else Modifier.padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design)),
        verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 32.design),
    ) {
        @Composable fun tabs() {
            Row(horizontalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 12.design), modifier = if (touch) Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier) {
                // Puce « Toutes les sources » : seulement avec 2 sources visibles ou plus, choix mémorisé par profil.
                if (canMix) FocusSurface(onClick = { vm.setAllSources(!allSources) }, shape = RoundedCornerShape(if (touch) 20.dp else 28.design), bg = if (allSources) Ux.Cta else Ux.Surface, modifier = Modifier.height(if (touch) 40.dp else 56.design)) { f ->
                    Box(Modifier.height(if (touch) 40.dp else 56.design).padding(horizontal = if (touch) 16.dp else 28.design), contentAlignment = Alignment.Center) {
                        Text(D.favAllSources, color = if (f || allSources) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (allSources) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (touch) 13.sp else 22.spx, maxLines = 1)
                    }
                }
                labels.forEachIndexed { i, l ->
                    val sel = i == tab
                    FocusSurface(onClick = { tab = i }, shape = RoundedCornerShape(if (touch) 20.dp else 28.design), bg = if (sel) Ux.Cta else Ux.Surface, modifier = Modifier.height(if (touch) 40.dp else 56.design)) { f ->
                        Box(Modifier.height(if (touch) 40.dp else 56.design).padding(horizontal = if (touch) 16.dp else 28.design), contentAlignment = Alignment.Center) {
                            Text(l, color = if (f || sel) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (touch) 13.sp else 22.spx, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (touch) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { SectionTitle(S.favorites, 56); com.ultratv.tv.nativeapp.ui.mobile.SearchAction() }; tabs() }
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle(S.favorites, 48)
            tabs()
        }
        val empty = when (tab) { 0 -> channels.isEmpty() && movies.isEmpty() && series.isEmpty(); 1 -> channels.isEmpty(); 2 -> movies.isEmpty(); else -> series.isEmpty() }
        if (empty) {
            StateCard(D.emptyFavTitle, D.emptyFavBody, Icons.Heart, D.browseLive, onPrimary = onBrowseLive, badge = Ux.Surface2, modifier = if (touch) Modifier.fillMaxWidth() else Modifier.width(820.design))
        } else if (tab == 0) {
            // Grille commune : chaînes sur `cc` colonnes, affiches sur `vc` colonnes → total cc × vc, titres pleine largeur.
            val cc = if (touch) com.ultratv.tv.nativeapp.ui.mobile.gridColumns(usable, 170f) else 4
            val vc = if (touch) com.ultratv.tv.nativeapp.ui.mobile.gridColumns(usable, 110f) else 6
            LazyVerticalGrid(GridCells.Fixed(cc * vc), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 20.design), verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 20.design), contentPadding = PaddingValues(vertical = 8.design)) {
                fun header(title: String, n: Int) = item(key = "h-$title", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text("$title  $n", color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 16.sp else 30.spx, modifier = Modifier.padding(top = if (touch) 8.dp else 16.design))
                }
                if (channels.isNotEmpty()) {
                    header(D.channelsWord, channels.size)
                    items(channels, key = { "c${it.id}" }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(vc) }) { c ->
                        ChannelTile(c, now[c.id], srcNames?.get(c.providerId), onClick = { play(c) }, onLong = { vm.remove("LIVE", c.providerId, c.remoteId) })
                    }
                }
                if (movies.isNotEmpty()) {
                    header(S.moviesTitle, movies.size)
                    items(movies, key = { "m${it.id}" }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(cc) }) { m -> VodCard(m.title, m.poster, Modifier, badge = srcNames?.get(m.providerId), onClick = { onOpenMovie(m.id) }) }
                }
                if (series.isNotEmpty()) {
                    header(S.seriesTitle, series.size)
                    items(series, key = { "s${it.id}" }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(cc) }) { x -> VodCard(x.title, x.poster, Modifier, badge = srcNames?.get(x.providerId), onClick = { onOpenSeries(x.id) }) }
                }
            }
        } else if (tab == 1) {
            LazyVerticalGrid(GridCells.Fixed(if (touch) com.ultratv.tv.nativeapp.ui.mobile.gridColumns(usable, 170f) else 4), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 24.design), verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 24.design), contentPadding = PaddingValues(vertical = 8.design)) {
                items(channels.distinctBy { it.id }, key = { it.id }) { c ->
                    ChannelTile(c, now[c.id], srcNames?.get(c.providerId), onClick = { play(c) }, onLong = { vm.remove("LIVE", c.providerId, c.remoteId) })
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(if (touch) com.ultratv.tv.nativeapp.ui.mobile.gridColumns(usable, 110f) else 6), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 20.design), verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 20.design), contentPadding = PaddingValues(vertical = 8.design)) {
                if (tab == 2) items(movies.distinctBy { it.id }, key = { it.id }) { m -> VodCard(m.title, m.poster, Modifier, badge = srcNames?.get(m.providerId), onClick = { onOpenMovie(m.id) }) }
                else items(series.distinctBy { it.id }, key = { it.id }) { s -> VodCard(s.title, s.poster, Modifier, badge = srcNames?.get(s.providerId), onClick = { onOpenSeries(s.id) }) }
            }
        }
        pinFor?.let { ch ->
            com.ultratv.tv.nativeapp.ui.parental.PinPromptDialog(
                title = ch.title,
                onUnlocked = { pinFor = null; vm.prepare(ch, channels); onPlayChannel(ch.streamUrl, ch.name) },
                onCancel = { pinFor = null },
            )
        }
        if (channels.isNotEmpty() && tab <= 1) FavoritesExportButton(vm)
        Text(D.favHint, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, modifier = if (touch) Modifier.padding(bottom = 8.dp) else Modifier)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(c: ChannelEntity, now: EpgEntity?, source: String?, onClick: () -> Unit, onLong: () -> Unit) {
    val fraction = if (now != null && now.endMs > now.startMs) ((System.currentTimeMillis() - now.startMs).toFloat() / (now.endMs - now.startMs)).coerceIn(0f, 1f) else 0f
    FocusSurface(onClick = onClick, onLongClick = onLong, shape = RoundedCornerShape(22.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1.04f, modifier = Modifier.height(if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) 112.dp else 196.design)) { f ->
        Column(Modifier.fillMaxSize().padding(24.design), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                LogoBox(c.logo, c.title, Modifier.width(88.design).height(56.design), radius = 12, pad = 6, bg = if (f) Ux.Surface else Ux.Surface2)
                if (c.seq > 0 && com.ultratv.tv.nativeapp.ui.common.LocalChannelDisplay.current.showNumbers) Text("${c.seq}", color = if (f) Ux.OnFocus2 else Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Vue mélangée : la source précède le programme en cours.
                Text(listOfNotNull(source?.takeIf { it.isNotEmpty() }, now?.title).joinToString(" · "), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProgressLine(fraction, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.design)), heightPx = 6, track = if (f) Ux.OnFocus2.copy(alpha = 0.4f) else Ux.Surface2)
        }
    }
}

package com.ultratv.tv.nativeapp.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.map
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.CategoryCount
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.prettyCategoryName
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class CatalogKind { MOVIES, SERIES }

/** Carte de la grille : seulement des champs RÉELS de la source. */
data class PosterItem(val id: Long, val title: String, val poster: String?, val year: Int?, val rating: Double?, val lang: String = "")

data class CategoryChip(val remoteId: String, val name: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogGridViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
    private val hiddenStore: HiddenCategoriesStore,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
) : ViewModel() {
    private val kind = MutableStateFlow(CatalogKind.MOVIES)
    fun bind(k: CatalogKind) { kind.value = k }

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected
    fun select(remoteId: String?) { _selected.value = remoteId }

    private val pid = providerRepo.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    /** Puces : « Tous » + les catégories NON vides (compteurs SQL), hors catégories masquées. */
    val chips: StateFlow<List<CategoryChip>> = combine(pid, kind, hiddenStore.hidden) { p, k, h -> Triple(p, k, h) }
        .flatMapLatest { (p, k, hidden) ->
            if (p == null) flowOf(emptyList())
            else {
                val kindName = if (k == CatalogKind.MOVIES) "MOVIE" else "SERIES"
                val counts: Flow<List<CategoryCount>> = if (k == CatalogKind.MOVIES) movieDao.observeCategoryCounts(p) else seriesDao.observeCategoryCounts(p)
                combine(catalog.categories(p, kindName), counts) { cats: List<CategoryEntity>, cnt ->
                    val nonEmpty = cnt.filter { it.n > 0 }.mapNotNull { it.categoryId }.toSet()
                    cats.filter { it.remoteId in nonEmpty && hiddenStore.keyFor(kindName, p, it.remoteId) !in hidden }
                        .map { CategoryChip(it.remoteId, prettyCategoryName(it.name)) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _langView = kotlinx.coroutines.flow.MutableStateFlow(com.ultratv.tv.nativeapp.data.repo.LangView.ALL)
    val langView: StateFlow<com.ultratv.tv.nativeapp.data.repo.LangView> = _langView
    fun toggleLang(code: String) { _langView.value = _langView.value.toggle(code) }
    fun clearLangView() { _langView.value = com.ultratv.tv.nativeapp.data.repo.LangView.ALL }
    val langCounts: StateFlow<List<com.ultratv.tv.nativeapp.data.db.LangCount>> = combine(pid, kind) { p, k -> p to k }
        .flatMapLatest { (p, k) -> if (p == null) flowOf(emptyList()) else if (k == CatalogKind.MOVIES) movieDao.observeLangCounts(p) else seriesDao.observeLangCounts(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: Flow<PagingData<PosterItem>> = combine(pid, kind, _selected, _langView) { p, k, c, lv -> arrayOf<Any?>(p, k, c, lv) }
        .distinctUntilChanged()
        .flatMapLatest { arr ->
            @Suppress("UNCHECKED_CAST") val p = arr[0] as Long?; val k = arr[1] as CatalogKind; val cat = arr[2] as String?; val lv = arr[3] as com.ultratv.tv.nativeapp.data.repo.LangView
            if (p == null) flowOf(PagingData.empty())
            else if (k == CatalogKind.MOVIES) Pager(PagingConfig(pageSize = 42, prefetchDistance = 28, initialLoadSize = 84, enablePlaceholders = false)) {
                if (cat == null) movieDao.pagedAll(p, lv.useLang, lv.langs) else movieDao.pagedForCategory(p, cat, lv.useLang, lv.langs)
            }.flow.map { pd -> pd.map { PosterItem(it.id, it.title, it.poster, it.year, it.rating, it.lang) } }
            else Pager(PagingConfig(pageSize = 42, prefetchDistance = 28, initialLoadSize = 84, enablePlaceholders = false)) {
                if (cat == null) seriesDao.pagedAll(p, lv.useLang, lv.langs) else seriesDao.pagedForCategory(p, cat, lv.useLang, lv.langs)
            }.flow.map { pd -> pd.map { PosterItem(it.id, it.title, it.poster, it.year, it.rating, it.lang) } }
        }.cachedIn(viewModelScope)
}

/**
 * Films / Séries (maquette Films.dc.html) : titre, puces de catégories, grille d'affiches 2:3 sur
 * 7 colonnes. Paginée : seules les affiches visibles existent en mémoire (180 000 films sur la source réelle).
 */
@Composable
fun CatalogGridScreen(kind: CatalogKind, onOpen: (Long) -> Unit) {
    val vm: CatalogGridViewModel = hiltViewModel(key = kind.name)
    LaunchedEffect(kind) { vm.bind(kind) }
    val D = LocalDs.current
    val chips by vm.chips.collectAsState()
    val selected by vm.selected.collectAsState()
    val items = vm.items.collectAsLazyPagingItems()
    val langView by vm.langView.collectAsState()
    val langCounts by vm.langCounts.collectAsState()
    var langPanel by remember { mutableStateOf(false) }
    val title = if (kind == CatalogKind.MOVIES) D.moviesTitle else D.seriesTitle

    Column(Modifier.fillMaxSize().padding(start = 72.design, end = 96.design, top = 54.design), verticalArrangement = Arrangement.spacedBy(32.design)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 1)
            com.ultratv.tv.nativeapp.ui.common.LangPill(langView, onClick = { langPanel = true })
        }
        if (langPanel) com.ultratv.tv.nativeapp.ui.common.LangViewPanel(langCounts, langView, onToggle = { vm.toggleLang(it) }, onClear = { vm.clearLangView() }, onDismiss = { langPanel = false })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.design)) {
            item(key = "all") { Chip(D.allChip, selected == null) { vm.select(null) } }
            items(chips, key = { it.remoteId }, contentType = { "chip" }) { c -> Chip(c.name, selected == c.remoteId) { vm.select(c.remoteId) } }
        }
        if (items.itemCount == 0) {
            if (chips.isEmpty() && com.ultratv.tv.nativeapp.ui.common.NoDataStateCard()) return@Column
            Text(if (kind == CatalogKind.MOVIES) D.noMovies else D.noSeries, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
            return@Column
        }
        val first = remember { FocusRequester() }
        var firstFocused by remember { mutableStateOf(false) }
        RequestInitialFocus(first, hasFocus = { firstFocused }, key = selected)
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(28.design),
            verticalArrangement = Arrangement.spacedBy(28.design),
            contentPadding = PaddingValues(top = 12.design, bottom = 54.design),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count = items.itemCount, key = items.itemKey { it.id }, contentType = { "poster" }) { i ->
                val it = items[i]
                if (it != null) PosterCell(it, if (i == 0) Modifier.focusRequester(first).onFocusChanged { f -> firstFocused = f.isFocused } else Modifier) { onOpen(it.id) }
                else androidx.compose.foundation.layout.Spacer(Modifier.height(1.design))
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(28.design), bg = if (selected) Ux.Cta else Ux.Surface, modifier = Modifier.height(56.design)) { f ->
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 28.design).height(56.design), contentAlignment = Alignment.Center) {
            Text(label, color = if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Affiche 2:3 (focus : ×1,06 + anneau), titre 22 gras sur 1 ligne, année · note 18. */
@Composable
private fun PosterCell(item: PosterItem, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.design)) {
        FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.Surface, ringWidth = 5.design, focusedBg = Color0xE4E4E7, modifier = Modifier.fillMaxWidth().aspectRatio23()) {
            Box(Modifier.fillMaxSize()) {
                PosterImage(item.poster, item.title, Modifier.fillMaxSize(), radius = 18)
                com.ultratv.tv.nativeapp.ui.common.LangBadge(item.lang, modifier = Modifier.align(Alignment.TopStart).padding(10.design))
            }
        }
        Text(item.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val meta = listOfNotNull(item.year?.toString(), item.rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) }).joinToString(" · ")
        Text(meta, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(22.design))
    }
}

private val Color0xE4E4E7 = androidx.compose.ui.graphics.Color(0xFFE4E4E7)
private fun Modifier.aspectRatio23() = this.aspectRatio(2f / 3f)

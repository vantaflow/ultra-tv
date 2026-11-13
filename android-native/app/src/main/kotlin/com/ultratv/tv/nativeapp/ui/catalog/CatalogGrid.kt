package com.ultratv.tv.nativeapp.ui.catalog

import com.ultratv.tv.nativeapp.i18n.traktWatched
import com.ultratv.tv.nativeapp.data.repo.atMostEvery
import com.ultratv.tv.nativeapp.data.repo.snapshotWhile
import com.ultratv.tv.nativeapp.ui.common.RowBleed
import com.ultratv.tv.nativeapp.ui.common.rowBleedStart
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.ultratv.tv.nativeapp.data.db.GenreDao
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.GenreDetector
import com.ultratv.tv.nativeapp.data.repo.GenreGrouping
import com.ultratv.tv.nativeapp.i18n.catalogCategoriesTitle
import com.ultratv.tv.nativeapp.i18n.catalogColumnsName
import com.ultratv.tv.nativeapp.i18n.catalogEmptyDetecting
import com.ultratv.tv.nativeapp.i18n.catalogLayoutName
import com.ultratv.tv.nativeapp.i18n.catalogSourceName
import com.ultratv.tv.nativeapp.i18n.categoriesCount
import com.ultratv.tv.nativeapp.i18n.genreDetecting
import com.ultratv.tv.nativeapp.i18n.genreHint
import com.ultratv.tv.nativeapp.i18n.genreName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

/** Carte de la grille : seulement des champs RÉELS de la source. Immuable : une cellule inchangée est sautée à la recomposition. */
@androidx.compose.runtime.Immutable
data class PosterItem(val id: Long, val title: String, val poster: String?, val year: Int?, val rating: Double?, val lang: String = "", val watched: Boolean = false, val progress: Float = 0f)

/** Catégorie affichée : [count] titres ; [isGenre] = genre automatique (le nom est alors localisé à l'affichage, [remoteId] est le code du genre). */
data class CategoryChip(val remoteId: String, val name: String, val count: Int = 0, val isGenre: Boolean = false)

/** Catégorie réellement affichée : [cat] null = « Tous » (rangées) ; [source] = d'où viennent les catégories chargées. */
private data class Target(val cat: String?, val source: CategorySource)

private const val ROW_SIZE = 20
/** Le choix d'une catégorie suit le focus de la liste, après cette pause : un défilement rapide ne lance pas une requête par ligne. */
private const val PREVIEW_DELAY_MS = 280L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogGridViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
    private val hiddenStore: HiddenCategoriesStore,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val trakt: com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository,
    syncBus: com.ultratv.tv.nativeapp.data.repo.SyncStatusBus,
    private val prefs: UserPreferencesStore,
    private val genreDao: GenreDao,
    private val genreDetector: GenreDetector,
    private val titleKeys: com.ultratv.tv.nativeapp.data.repo.TitleSortKeys,
) : ViewModel() {
    private val kind = MutableStateFlow(CatalogKind.MOVIES)
    fun bind(k: CatalogKind) { kind.value = k }
    private fun kindName(k: CatalogKind) = if (k == CatalogKind.MOVIES) "MOVIE" else "SERIES"

    /** Liste + grille seulement hors tactile ; en tactile l'écran reste en rangées quel que soit le réglage mémorisé. */
    private val splitCapable = MutableStateFlow(true)
    fun setSplitCapable(v: Boolean) { splitCapable.value = v }

    /** Réglages mémorisés (source, affichage, colonnes) ; null tant que les préférences ne sont pas lues. */
    val layoutPrefs: StateFlow<CatalogLayoutPrefs?> = prefs.flow
        .map { CatalogLayoutPrefs(CategorySource.parse(it.catalogSource), CatalogLayout.parse(it.catalogLayout), it.catalogColumns) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val sourceFlow = layoutPrefs.filterNotNull().map { it.source }.distinctUntilChanged()

    /** Tri et filtres de la grille. Le tri est mémorisé ; la note et la période restent propres à la visite. */
    private val _filter = MutableStateFlow(CatalogFilter.DEFAULT)
    val filter: StateFlow<CatalogFilter> = _filter
    private val filterReady = MutableStateFlow(false)
    fun setFilter(f: CatalogFilter) {
        val sortChanged = f.sort != _filter.value.sort
        _filter.value = f
        if (sortChanged) viewModelScope.launch { prefs.setCatalogSort(f.sortCode) }
    }

    fun setSource(s: CategorySource) { _selected.value = null; viewModelScope.launch { prefs.setCatalogSource(s.key) } }
    fun setLayout(l: CatalogLayout) { viewModelScope.launch { prefs.setCatalogLayout(l.key) } }
    fun setColumns(c: Int) { viewModelScope.launch { prefs.setCatalogColumns(c) } }

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected
    fun select(remoteId: String?) { previewJob?.cancel(); _selected.value = remoteId }

    private var previewJob: Job? = null
    /** Sélection qui suit le focus de la liste (avec une courte pause, voir [PREVIEW_DELAY_MS]). */
    fun preview(remoteId: String) {
        if (_selected.value == remoteId) { previewJob?.cancel(); return }
        previewJob?.cancel()
        previewJob = viewModelScope.launch { delay(PREVIEW_DELAY_MS); _selected.value = remoteId }
    }

    init {
        viewModelScope.launch {
            val code = prefs.flow.first().catalogSort
            _filter.value = _filter.value.copy(sort = CatalogFilter.Sort.entries.firstOrNull { it.code == code } ?: CatalogFilter.Sort.PROVIDER)
            filterReady.value = true
        }
    }

    /** Synchro en cours : compteurs et rangées lus une seule fois au lieu d'être relancés à chaque lot inséré. */
    private val syncing = syncBus.status.map { it != null }.distinctUntilChanged()

    private val pid = providerRepo.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    init {
        // Genres automatiques : calcul lancé (sans bloquer) quand la source de catégories l'utilise, et relancé à la fin de
        // chaque synchro — incrémental, seuls les titres nouveaux ou enrichis depuis sont traités.
        viewModelScope.launch {
            combine(pid, kind, sourceFlow, syncing) { p, k, s, sy -> listOf<Any?>(p, k, s, sy) }.collect { a ->
                val p = a[0] as Long?; val k = a[1] as CatalogKind
                if (p != null && a[2] == CategorySource.GENRES && a[3] == false) genreDetector.ensure(p, kindName(k))
            }
        }
    }

    /** Avancement de la détection des genres pour l'écran affiché (null = rien en cours). */
    val genreProgress: StateFlow<GenreDetector.Progress?> = combine(genreDetector.progress, kind) { m, k -> m[kindName(k)] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Catégories : genres automatiques (comptes en une requête GROUP BY, plus fournis d'abord, « Autres » en dernier) ou
     * catégories NON vides du fournisseur (compteurs SQL), hors catégories masquées.
     * null = pas encore chargées (rien n'est affiché de faux pendant ce temps : ni « aucun film », ni grille à plat).
     */
    private val chipsFull: StateFlow<Pair<CategorySource, List<CategoryChip>>?> = combine(pid, kind, hiddenStore.hidden, sourceFlow) { p, k, h, s -> listOf<Any?>(p, k, h, s) }
        .flatMapLatest { a ->
            @Suppress("UNCHECKED_CAST") val p = a[0] as Long?; val k = a[1] as CatalogKind; val hidden = a[2] as Set<String>; val src = a[3] as CategorySource
            val kindName = kindName(k)
            if (p == null) flowOf(src to emptyList())
            else if (src == CategorySource.GENRES) {
                genreDao.observeCounts(p, kindName).atMostEvery(1_000).snapshotWhile(syncing).map { cnt ->
                    val live = cnt.filter { it.n > 0 && it.categoryId != null }
                    src to GenreGrouping.sortedByCount(live, { it.categoryId!! }, { it.n }).map { val id = it.categoryId!!; CategoryChip(id, id, it.n, isGenre = true) }
                }
            } else {
                val counts: Flow<List<CategoryCount>> = if (k == CatalogKind.MOVIES) movieDao.observeCategoryCounts(p).atMostEvery(1_000).snapshotWhile(syncing) else seriesDao.observeCategoryCounts(p).atMostEvery(1_000).snapshotWhile(syncing)
                combine(catalog.categories(p, kindName), counts) { cats: List<CategoryEntity>, cnt ->
                    val byId = cnt.filter { it.n > 0 && it.categoryId != null }.associate { it.categoryId!! to it.n }
                    src to cats.filter { it.remoteId in byId && hiddenStore.keyFor(kindName, p, it.remoteId) !in hidden }
                        .map { CategoryChip(it.remoteId, prettyCategoryName(it.name), byId.getValue(it.remoteId)) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chips: StateFlow<List<CategoryChip>?> = chipsFull.map { it?.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Catégorie réellement affichée. Liste + grille : toujours une catégorie (la sélection, sinon la première) ; rangées :
     * la sélection, null = « Tous ». null (Target) tant que les catégories ne sont pas chargées.
     */
    private val target: Flow<Target?> = combine(chipsFull, _selected, layoutPrefs, splitCapable) { cf, sel, lp, cap ->
        if (cf == null || lp == null) null
        else if (cap && lp.layout == CatalogLayout.SPLIT) Target(sel?.takeIf { s -> cf.second.any { it.remoteId == s } } ?: cf.second.firstOrNull()?.remoteId, cf.first)
        else Target(sel, cf.first)
    }.distinctUntilChanged()

    val effectiveSelected: StateFlow<String?> = target.map { it?.cat }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _langView = kotlinx.coroutines.flow.MutableStateFlow(com.ultratv.tv.nativeapp.data.repo.LangView.ALL)
    val langView: StateFlow<com.ultratv.tv.nativeapp.data.repo.LangView> = _langView
    fun toggleLang(code: String) { _langView.value = _langView.value.toggle(code) }
    fun clearLangView() { _langView.value = com.ultratv.tv.nativeapp.data.repo.LangView.ALL }
    val langCounts: StateFlow<List<com.ultratv.tv.nativeapp.data.db.LangCount>> = combine(pid, kind) { p, k -> p to k }
        .flatMapLatest { (p, k) -> if (p == null) flowOf(emptyList()) else if (k == CatalogKind.MOVIES) movieDao.observeLangCounts(p).atMostEvery(1_000).snapshotWhile(syncing) else seriesDao.observeLangCounts(p).atMostEvery(1_000).snapshotWhile(syncing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Rangée d'une catégorie (ou d'un genre) pour la vue « Tous » (les 20 plus récents), MISE EN CACHE par catégorie : une rangée
     * qui sort puis revient à l'écran en défilant retrouve sa dernière valeur tout de suite (avant : nouvelle requête,
     * squelette puis clignotement). Pendant une synchro, au plus une relecture par seconde.
     */
    private val rowCache = java.util.concurrent.ConcurrentHashMap<String, StateFlow<List<PosterItem>?>>()
    fun rowItems(cat: String): StateFlow<List<PosterItem>?> = rowCache.getOrPut(cat) {
        combine(pid, kind, sourceFlow) { p, k, s -> Triple(p, k, s) }.flatMapLatest { (p, k, s) ->
            val genre = s == CategorySource.GENRES
            if (p == null) flowOf(emptyList())
            else if (k == CatalogKind.MOVIES) (if (genre) movieDao.observeGenreRow(p, cat, ROW_SIZE) else movieDao.observeRow(p, cat, ROW_SIZE)).atMostEvery(1_000).snapshotWhile(syncing).map { l -> val lib = trakt.library.value; l.map { PosterItem(it.id, it.title, it.poster, it.year, it.rating, watched = lib.isMovieWatched(it.title, it.year)) } }
            else (if (genre) seriesDao.observeGenreRow(p, cat, ROW_SIZE) else seriesDao.observeRow(p, cat, ROW_SIZE)).atMostEvery(1_000).snapshotWhile(syncing).map { l -> l.map { PosterItem(it.id, it.title, it.poster, it.year, it.rating) } }
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), null)
    }

    private val readyFilter: Flow<CatalogFilter?> = combine(_filter, filterReady) { f, ok -> if (ok) f else null }

    val items: Flow<PagingData<PosterItem>> = combine(pid, kind, target, _langView, readyFilter) { p, k, t, lv, f -> listOf<Any?>(p, k, t, lv, f) }
        .distinctUntilChanged()
        .flatMapLatest { arr ->
            val p = arr[0] as Long?; val k = arr[1] as CatalogKind; val t = arr[2] as Target?; val lv = arr[3] as com.ultratv.tv.nativeapp.data.repo.LangView
            val f = arr[4] as CatalogFilter?
            // Tri A–Z : clés sans article recalculées une fois pour les titres déjà en base (ensuite immédiat).
            if (f?.sort == CatalogFilter.Sort.TITLE) titleKeys.ensure()
            val cat = t?.cat
            val genre = if (t?.source == CategorySource.GENRES) cat else null
            if (p == null || t == null || f == null) flowOf(PagingData.empty())
            else if (k == CatalogKind.MOVIES) Pager(PagingConfig(pageSize = 42, prefetchDistance = 28, initialLoadSize = 84, enablePlaceholders = false)) {
                if (genre != null) when (f.sort) {
                    CatalogFilter.Sort.RATING -> movieDao.pagedGenreByRating(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    CatalogFilter.Sort.TITLE -> movieDao.pagedGenreByTitle(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    CatalogFilter.Sort.RECENT -> movieDao.pagedGenreByRecent(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    CatalogFilter.Sort.PROVIDER -> movieDao.pagedGenreByProvider(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                }
                else if (f.narrows) when {
                    cat != null -> movieDao.pagedFilteredInCategory(p, cat, lv.useLang, lv.langs, f.sortCode, f.minRating, f.yearFrom, f.yearTo)
                    f.sort == CatalogFilter.Sort.RATING -> movieDao.pagedFilteredByRating(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    f.sort == CatalogFilter.Sort.RECENT -> movieDao.pagedFilteredByRecent(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    f.sort == CatalogFilter.Sort.TITLE -> movieDao.pagedFilteredByTitle(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    else -> movieDao.pagedFilteredByProvider(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                }
                else if (cat == null) movieDao.pagedAll(p, lv.useLang, lv.langs) else movieDao.pagedForCategory(p, cat, lv.useLang, lv.langs)
            }.flow.map { pd -> val lib = trakt.library.value; pd.map { PosterItem(it.id, it.title, it.poster, it.year, it.rating, it.lang, lib.isMovieWatched(it.title, it.year)) } }
            else Pager(PagingConfig(pageSize = 42, prefetchDistance = 28, initialLoadSize = 84, enablePlaceholders = false)) {
                if (genre != null) when (f.sort) {
                    CatalogFilter.Sort.RATING -> seriesDao.pagedGenreByRating(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    CatalogFilter.Sort.TITLE -> seriesDao.pagedGenreByTitle(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    CatalogFilter.Sort.RECENT -> seriesDao.pagedGenreByRecent(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    CatalogFilter.Sort.PROVIDER -> seriesDao.pagedGenreByProvider(p, genre, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                }
                else if (f.narrows) when {
                    cat != null -> seriesDao.pagedFilteredInCategory(p, cat, lv.useLang, lv.langs, f.sortCode, f.minRating, f.yearFrom, f.yearTo)
                    f.sort == CatalogFilter.Sort.RATING -> seriesDao.pagedFilteredByRating(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    f.sort == CatalogFilter.Sort.RECENT -> seriesDao.pagedFilteredByRecent(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    f.sort == CatalogFilter.Sort.TITLE -> seriesDao.pagedFilteredByTitle(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                    else -> seriesDao.pagedFilteredByProvider(p, lv.useLang, lv.langs, f.minRating, f.yearFrom, f.yearTo)
                }
                else if (cat == null) seriesDao.pagedAll(p, lv.useLang, lv.langs) else seriesDao.pagedForCategory(p, cat, lv.useLang, lv.langs)
            }.flow.map { pd -> pd.map { PosterItem(it.id, it.title, it.poster, it.year, it.rating, it.lang) } }
        }.cachedIn(viewModelScope)
}

/** Nom affiché d'une catégorie : genre automatique localisé, ou nom du fournisseur. */
@Composable
private fun categoryLabel(c: CategoryChip): String = if (c.isGenre) LocalDs.current.genreName(c.remoteId) else c.name

/**
 * Films / Séries : deux présentations au choix (mémorisé).
 *  - Liste + grille (TV) : colonne de catégories avec leur nombre de titres à gauche, grille d'affiches paresseuse à droite.
 *  - Rangées : une rangée par catégorie (affichage d'origine, toujours utilisé en tactile).
 * Les catégories viennent du fournisseur ou des genres automatiques. Paginé : seules les affiches visibles existent en
 * mémoire (180 000 films sur la source réelle).
 */
@Composable
fun CatalogGridScreen(kind: CatalogKind, onOpen: (Long) -> Unit) {
    val vm: CatalogGridViewModel = hiltViewModel(key = kind.name)
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    LaunchedEffect(kind) { vm.bind(kind) }
    LaunchedEffect(touch) { vm.setSplitCapable(!touch) }
    val D = LocalDs.current
    val chipsOrNull by vm.chips.collectAsState()
    val chips = chipsOrNull.orEmpty()
    val selected by vm.effectiveSelected.collectAsState()
    val filter by vm.filter.collectAsState()
    val lp by vm.layoutPrefs.collectAsState()
    val progress by vm.genreProgress.collectAsState()
    val items = vm.items.collectAsLazyPagingItems()
    val langView by vm.langView.collectAsState()
    val langCounts by vm.langCounts.collectAsState()
    var langPanel by remember { mutableStateOf(false) }
    val split = !touch && lp?.layout == CatalogLayout.SPLIT
    val title = if (split) D.catalogCategoriesTitle(kind == CatalogKind.MOVIES) else if (kind == CatalogKind.MOVIES) D.moviesTitle else D.seriesTitle
    val detecting = lp?.source == CategorySource.GENRES && progress?.let { it.done < it.total } == true

    val widthDp = com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current
    val listReq = remember { FocusRequester() }
    // TV, rangées, vue « Tous » sans filtre : onglets + hero + rangées (pleine largeur, voir CatalogV2).
    val heroMode = !touch && !split && chipsOrNull != null && lp != null && chips.isNotEmpty() && selected == null && !filter.narrows
    com.ultratv.tv.nativeapp.ui.mobile.TouchRefresh {
    if (heroMode) CatalogHeroHost(vm, kind, chips, filter, detecting, progress, onOpen) else Column(
        Modifier.fillMaxSize().then(if (touch) Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp) else Modifier.padding(start = 72.design, end = 96.design, top = 54.design)),
        verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else if (split) 20.design else 32.design),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 28.sp else 48.spx, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                if (detecting) progress?.let { GenreProgressBanner(it, touch) }
                com.ultratv.tv.nativeapp.ui.mobile.SearchAction()
            }
        }
        lp?.let { ChoiceBar(vm, it, filter, split, touch, chips.size, if (split) listReq else null) }
        if (detecting && !touch) Text(D.genreHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!split) {
        LazyRow(Modifier.rowBleedStart(RowBleed), horizontalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design), contentPadding = PaddingValues(start = RowBleed)) {
            item(key = "all") { Chip(D.allChip, selected == null) { vm.select(null) } }
            items(chips, key = { it.remoteId }, contentType = { "chip" }) { c -> Chip(categoryLabel(c), selected == c.remoteId) { vm.select(c.remoteId) } }
        }
        // Tri et filtres : Tri · Note minimale · Période. Un filtre actif sur « Tous » affiche la grille filtrée.
        LazyRow(Modifier.rowBleedStart(RowBleed), horizontalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design), contentPadding = PaddingValues(start = RowBleed)) {
            items(CatalogFilter.Sort.entries, key = { "s-${it.name}" }, contentType = { "chip" }) { o -> Chip(D.catalogSortName(o), filter.sort == o) { vm.setFilter(filter.copy(sort = o)) } }
            items(CatalogFilter.RATINGS, key = { "r-$it" }, contentType = { "chip" }) { r -> Chip(if (r == 0) D.anyRating else "★ $r+", filter.minRating == r) { vm.setFilter(filter.copy(minRating = r)) } }
            items(CatalogFilter.Period.entries, key = { "p-${it.name}" }, contentType = { "chip" }) { o -> Chip(D.periodName(o), filter.period == o) { vm.setFilter(filter.copy(period = o)) } }
        }
        }
        // Chargement : squelette de rangées à taille fixe (la mise en page ne saute plus quand les données arrivent).
        if (chipsOrNull == null || lp == null) { RowsSkeleton(touch); return@Column }
        if (split) {
            if (chips.isEmpty()) {
                if (detecting) Text(D.catalogEmptyDetecting, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
                else if (!com.ultratv.tv.nativeapp.ui.common.NoDataStateCard()) Text(if (kind == CatalogKind.MOVIES) D.noMovies else D.noSeries, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
                return@Column
            }
            SplitBody(
                vm = vm, chips = chips, selected = selected, items = items, listReq = listReq,
                columns = CatalogLayoutRules.columns(lp?.columns ?: 0, split = true, touch = false, touchColumns = 0), emptyText = if (kind == CatalogKind.MOVIES) D.noMovies else D.noSeries, onOpen = onOpen,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            return@Column
        }
        // « Tous » : une rangée par catégorie (comme Netflix), « Voir tout » ouvre la grille de la catégorie.
        if (selected == null && chips.isNotEmpty() && !filter.narrows) {
            CategoryRows(vm, chips, touch, onOpen)
            return@Column
        }
        if (items.itemCount == 0) {
            // Grille en cours de chargement : squelette, pas « aucun film ».
            if (items.loadState.refresh is androidx.paging.LoadState.Loading) { RowsSkeleton(touch); return@Column }
            if (chips.isEmpty() && com.ultratv.tv.nativeapp.ui.common.NoDataStateCard()) return@Column
            Text(if (kind == CatalogKind.MOVIES) D.noMovies else D.noSeries, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
            return@Column
        }
        val first = remember { FocusRequester() }
        var firstFocused by remember { mutableStateOf(false) }
        RequestInitialFocus(first, hasFocus = { firstFocused }, key = selected to filter)
        val safeKey0 = com.ultratv.tv.nativeapp.ui.common.rememberSafeItemKey(items) { it.id }
        LazyVerticalGrid(
            columns = GridCells.Fixed(CatalogLayoutRules.columns(lp?.columns ?: 0, split = false, touch = touch, touchColumns = com.ultratv.tv.nativeapp.ui.mobile.gridColumns(widthDp - 40f - (if (widthDp >= 600f) 88f else 0f)))),
            horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 28.design),
            verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 28.design),
            contentPadding = PaddingValues(top = if (touch) 4.dp else 12.design, bottom = if (touch) 16.dp else 54.design),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count = items.itemCount, key = safeKey0, contentType = { "poster" }) { i ->
                val it = items[i]
                if (it != null) PosterCell(it, if (i == 0) Modifier.focusRequester(first).onFocusChanged { f -> firstFocused = f.isFocused } else Modifier) { onOpen(it.id) }
                else androidx.compose.foundation.layout.Spacer(Modifier.height(1.design))
            }
        }
    }
    }
}

/** Vue « Tous » TV : câble le ViewModel (rangées en cache, focus initial, filtres) à la mise en page sans état [CatalogRowsTv]. */
@Composable
private fun CatalogHeroHost(
    vm: CatalogGridViewModel, kind: CatalogKind, chips: List<CategoryChip>, filter: CatalogFilter, detecting: Boolean, progress: GenreDetector.Progress?, onOpen: (Long) -> Unit,
) {
    val D = LocalDs.current
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    RequestInitialFocus(first, hasFocus = { firstFocused }, key = chips.firstOrNull()?.remoteId)
    // Hero : premier titre de la première rangée (déjà en cache pour la rangée elle-même).
    val firstRow by remember(chips.first().remoteId) { vm.rowItems(chips.first().remoteId) }.collectAsState()
    val hero = remember(firstRow) { firstRow?.firstOrNull()?.let(::heroOf) }
    val tabs = buildList {
        add(CatalogTabUi(null, D.allChip))
        chips.forEach { add(CatalogTabUi(it.remoteId, if (it.isGenre) D.genreName(it.remoteId) else it.name)) }
    }
    CatalogRowsTv(
        tabs = tabs, selectedKey = null, onTab = { vm.select(it) },
        filters = {
            // Tri · note minimale · période (mêmes pastilles qu'avant) et, pendant la détection, son avancement.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.design), verticalAlignment = Alignment.CenterVertically) {
                items(CatalogFilter.Sort.entries, key = { "s-${it.name}" }, contentType = { "chip" }) { o -> Chip(D.catalogSortName(o), filter.sort == o) { vm.setFilter(filter.copy(sort = o)) } }
                items(CatalogFilter.RATINGS, key = { "r-$it" }, contentType = { "chip" }) { r -> Chip(if (r == 0) D.anyRating else "★ $r+", filter.minRating == r) { vm.setFilter(filter.copy(minRating = r)) } }
                items(CatalogFilter.Period.entries, key = { "p-${it.name}" }, contentType = { "chip" }) { o -> Chip(D.periodName(o), filter.period == o) { vm.setFilter(filter.copy(period = o)) } }
                if (detecting && progress != null) item(key = "detect") { GenreProgressBanner(progress, false) }
            }
        },
        hero = hero, featuredLabel = D.featured, watchLabel = D.watch, onWatch = onOpen, heroExtra = {},
        chips = chips,
        labelOf = { c -> categoryLabel(c) },
        rowItems = { id -> vm.rowItems(id).collectAsState().value },
        seeAllLabel = D.seeAll, onOpen = onOpen, onSeeAll = { vm.select(it) },
        firstPoster = first, onFirstPosterFocus = { firstFocused = it },
        kind = com.ultratv.tv.nativeapp.ui.design.LocalPosterKind.current,
    )
}

/** Petit bandeau « Détection des genres 10 021 / 17 451 · 57 % ». */
@Composable
private fun GenreProgressBanner(p: GenreDetector.Progress, touch: Boolean) {
    val D = LocalDs.current
    val text = remember(p.done / 50, p.total, D.lang) { D.genreDetecting(p.done, p.total, p.percent) }
    Text(
        text, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = if (touch) 11.sp else 20.spx, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(if (touch) 10.dp else 16.design)).background(Ux.Surface).padding(horizontal = if (touch) 10.dp else 20.design, vertical = if (touch) 6.dp else 10.design),
    )
}

/** Barre du haut : tri, note minimale et période (pastilles d'origine), puis le nombre de catégories. */
@Composable
private fun ChoiceBar(vm: CatalogGridViewModel, lp: CatalogLayoutPrefs, filter: CatalogFilter, split: Boolean, touch: Boolean, categoryCount: Int, down: FocusRequester?) {
    val D = LocalDs.current
    val m = if (down != null) Modifier.focusProperties { this.down = down } else Modifier
    LazyRow(Modifier.rowBleedStart(RowBleed), horizontalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design), contentPadding = PaddingValues(start = RowBleed), verticalAlignment = Alignment.CenterVertically) {
        // Barre d'origine : tri · note minimale · période, chaque option en pastille (sélection en surbrillance).
        // Catégories du fournisseur à gauche ; ni source, ni affichage, ni colonnes à régler.
        items(CatalogFilter.Sort.entries, key = { "s-${it.name}" }, contentType = { "pill" }) { o -> Chip(D.catalogSortName(o), filter.sort == o, m) { vm.setFilter(filter.copy(sort = o)) } }
        items(CatalogFilter.RATINGS, key = { "r-$it" }, contentType = { "pill" }) { r -> Chip(if (r == 0) D.anyRating else "★ $r+", filter.minRating == r, m) { vm.setFilter(filter.copy(minRating = r)) } }
        items(CatalogFilter.Period.entries, key = { "p-${it.name}" }, contentType = { "pill" }) { o -> Chip(D.periodName(o), filter.period == o, m) { vm.setFilter(filter.copy(period = o)) } }
        item(key = "count", contentType = "count") {
            Text(D.categoriesCount(categoryCount), color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 13.sp else 24.spx, maxLines = 1, modifier = Modifier.padding(start = if (touch) 4.dp else 8.design))
        }
    }
}

/**
 * Liste de catégories (gauche) + grille d'affiches (droite). Télécommande : gauche / droite passent de l'une à l'autre (la
 * première colonne de la grille mène à la liste), haut mène à la barre de choix, Retour dans la grille ramène à la liste.
 * Le choix d'une catégorie suit le focus de la liste (avec une courte pause) ; OK valide et passe à la grille.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SplitBody(
    vm: CatalogGridViewModel,
    chips: List<CategoryChip>,
    selected: String?,
    items: androidx.paging.compose.LazyPagingItems<PosterItem>,
    listReq: FocusRequester,
    columns: Int,
    emptyText: String,
    onOpen: (Long) -> Unit,
    modifier: Modifier,
) {
    val D = LocalDs.current
    val gridReq = remember { FocusRequester() }
    val selReq = remember { FocusRequester() }
    var listFocused by remember { mutableStateOf(false) }
    var gridFocused by remember { mutableStateOf(false) }
    // Focus initial sur la catégorie choisie ; au retour d'une fiche, le focusRestorer de l'écran a déjà rendu le focus.
    RequestInitialFocus(selReq, hasFocus = { listFocused || gridFocused }, key = chips.isEmpty())
    androidx.activity.compose.BackHandler(enabled = gridFocused) { runCatching { listReq.requestFocus() } }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = chips.indexOfFirst { it.remoteId == selected }
        if (i > 2) listState.scrollToItem(i - 2)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(36.design)) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.width(400.design).fillMaxHeight()
                .onFocusChanged { listFocused = it.hasFocus }.focusRestorer().focusRequester(listReq).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.design),
            contentPadding = PaddingValues(start = 6.design, end = 6.design, top = 6.design, bottom = 54.design),
        ) {
            items(chips, key = { it.remoteId }, contentType = { "category" }) { c ->
                val isSel = c.remoteId == selected
                CategoryRow(
                    label = categoryLabel(c), count = c.count, selected = isSel,
                    modifier = (if (isSel) Modifier.focusRequester(selReq) else Modifier)
                        .focusProperties { right = gridReq }
                        // Droite : la catégorie focalisée devient tout de suite la catégorie choisie (sans attendre la
                        // pause de l'aperçu), la grille affichée est donc la sienne et Gauche y revient.
                        .onPreviewKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionRight) vm.select(c.remoteId); false }
                        .onFocusChanged { if (it.isFocused) vm.preview(c.remoteId) },
                    onClick = { vm.select(c.remoteId); runCatching { gridReq.requestFocus() } },
                )
            }
        }
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        // Défilement remis en haut quand la catégorie change (pas à la restauration d'une fiche : même catégorie).
        var shown by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(selected) }
        LaunchedEffect(selected) { if (shown != selected) { gridState.scrollToItem(0); shown = selected } }
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.weight(1f).fillMaxHeight().onFocusChanged { gridFocused = it.hasFocus }.focusRestorer().focusRequester(gridReq).focusGroup(),
        ) {
            val gap = 24.design
            val cellW = (maxWidth - gap * (columns - 1)) / columns
            if (items.itemCount == 0) {
                if (items.loadState.refresh is androidx.paging.LoadState.Loading) SkeletonRow(false)
                else Text(emptyText, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
            } else {
                val safeKey = com.ultratv.tv.nativeapp.ui.common.rememberSafeItemKey(items) { it.id }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    contentPadding = PaddingValues(top = 8.design, bottom = 54.design),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(count = items.itemCount, key = safeKey, contentType = { "poster" }) { i ->
                        val it = items[i]
                        if (it != null) PosterCell(it, if (CatalogLayoutRules.isFirstColumn(i, columns)) Modifier.onPreviewKeyEvent { e ->
                            // Gauche depuis la 1re colonne : retour sur la catégorie CHOISIE (et non la ligne voisine à l'écran).
                            if (e.key != androidx.compose.ui.input.key.Key.DirectionLeft) false
                            else { if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && runCatching { selReq.requestFocus() }.isFailure) runCatching { listReq.requestFocus() }; true }
                        } else Modifier, imageWidth = cellW) { onOpen(it.id) }
                        else androidx.compose.foundation.layout.Spacer(Modifier.height(1.design))
                    }
                }
            }
        }
    }
}

/** Ligne de la liste : nom à gauche, nombre de titres à droite ; la catégorie choisie est surlignée (le focus ajoute l'anneau). */
@Composable
private fun CategoryRow(label: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(16.design), bg = if (selected) Ux.Surface2 else androidx.compose.ui.graphics.Color.Transparent,
        focusedScale = 1.02f, ringWidth = 0.dp, focusedBg = Ux.Cta,
        modifier = modifier.fillMaxWidth().height(68.design),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = if (selected || f) FontWeight.Bold else FontWeight.SemiBold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(count.toString(), color = if (f) Ux.TextOnLight else Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
        }
    }
}

@Composable
private fun CategoryRows(vm: CatalogGridViewModel, chips: List<CategoryChip>, touch: Boolean, onOpen: (Long) -> Unit) {
    val D = LocalDs.current
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    RequestInitialFocus(first, hasFocus = { firstFocused }, key = chips.firstOrNull()?.remoteId)
    androidx.compose.foundation.lazy.LazyColumn(
        verticalArrangement = Arrangement.spacedBy(if (touch) 18.dp else 36.design),
        contentPadding = PaddingValues(bottom = if (touch) 16.dp else 54.design),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chips.size, key = { chips[it].remoteId }, contentType = { "row" }) { idx ->
            val c = chips[idx]
            val loaded by remember(c.remoteId) { vm.rowItems(c.remoteId) }.collectAsState()
            val list = loaded.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 16.design)) {
                Text(categoryLabel(c), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 18.sp else 30.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (loaded == null) { SkeletonRow(touch); return@Column }
                LazyRow(Modifier.rowBleedStart(RowBleed), horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 24.design), contentPadding = if (touch) PaddingValues(start = RowBleed, end = 16.dp)
                    // Affiche ciblée agrandie (x1,15 + contour) : ~34 px au-dessus, qui recouvraient le titre de la rangée.
                    else PaddingValues(start = RowBleed, end = 96.design, top = 34.design, bottom = 8.design)) {
                    items(list.size, key = { list[it].id }, contentType = { "poster" }) { i ->
                        val it = list[i]
                        val w = if (touch) 120.dp else 200.design
                        val m = Modifier.width(w)
                        PosterCell(it, if (idx == 0 && i == 0) m.focusRequester(first).onFocusChanged { f -> firstFocused = f.isFocused } else m, imageWidth = w) { onOpen(it.id) }
                    }
                    if (list.size >= ROW_SIZE) item(key = "more-${c.remoteId}") {
                        FocusSurface(onClick = { vm.select(c.remoteId) }, shape = RoundedCornerShape(18.design), bg = Ux.Surface,
                            modifier = Modifier.width(if (touch) 120.dp else 200.design).height(if (touch) 180.dp else 300.design)) { f ->
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(D.seeAll, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 14.sp else 24.spx)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rangée d'affiches vides, à la taille exacte d'une vraie rangée (affiche 2:3 + titre + méta). */
@Composable
internal fun SkeletonRow(touch: Boolean) {
    val w = if (touch) 120.dp else 200.design
    Row(horizontalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 24.design)) {
        repeat(if (touch) 3 else 7) {
            Column(verticalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 10.design)) {
                androidx.compose.foundation.layout.Box(Modifier.width(w).height(w * 1.5f).clip(RoundedCornerShape(if (touch) 12.dp else 18.design)).background(Ux.Surface))
                androidx.compose.foundation.layout.Box(Modifier.width(w * 0.8f).height(if (touch) 12.dp else 22.design).clip(RoundedCornerShape(6.design)).background(Ux.Surface))
                androidx.compose.foundation.layout.Box(Modifier.width(w * 0.5f).height(if (touch) 10.dp else 18.design).clip(RoundedCornerShape(6.design)).background(Ux.Surface))
            }
        }
    }
}

/** Écran Films / Séries en chargement : deux rangées squelettes avec leur titre. */
@Composable
private fun RowsSkeleton(touch: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (touch) 18.dp else 36.design)) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 16.design)) {
                androidx.compose.foundation.layout.Box(Modifier.width(if (touch) 140.dp else 320.design).height(if (touch) 16.dp else 30.design).clip(RoundedCornerShape(8.design)).background(Ux.Surface))
                SkeletonRow(touch)
            }
        }
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    // TV (maquette v2) : onglet 48 px, rond ; sélectionné = couleur de focus de la palette, les autres sur la carte.
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(if (touch) 20.dp else 24.design), bg = if (selected) Ux.Cta else Ux.Surface, focusedScale = if (touch) 1.06f else 1f, ringWidth = if (touch) 6.design else 0.dp, focusedBg = if (touch || !selected) Ux.Cta else Ux.Accent, modifier = modifier.height(if (touch) 40.dp else 48.design)) { f ->
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = if (touch) 16.dp else 22.design).height(if (touch) 40.dp else 48.design), contentAlignment = Alignment.Center) {
            Text(label, color = if (!touch && selected && f) Ux.OnAccent else if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (touch) 13.sp else 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Affiche 2:3 (focus : ×1,06 + anneau), titre 22 gras sur 1 ligne, année · note 18.
 * [kind] : type d'œuvre pour le repli TMDB (par défaut celui de l'écran) — passé directement par les rangées mêlant films
 * et séries, sans CompositionLocalProvider par carte. [imageWidth] : largeur connue de la carte (rangées) → l'affiche
 * est décodée à 2:3 de cette largeur dès la composition ; null (grille à colonnes variables) → taille de mise en page.
 */
@Composable
internal fun PosterCell(
    item: PosterItem,
    modifier: Modifier,
    kind: com.ultratv.tv.nativeapp.data.tmdb.TmdbKind? = com.ultratv.tv.nativeapp.ui.design.LocalPosterKind.current,
    imageWidth: androidx.compose.ui.unit.Dp? = null,
    onClick: () -> Unit,
) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    // TV : affiche v2 (contour + titre au focus) ; tactile : cellule d'origine.
    if (!touch) { PosterCellTv(item, modifier, kind, imageWidth, onClick); return }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 12.design)) {
        FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.Surface, ringWidth = 5.design, focusedBg = Color0xE4E4E7, modifier = Modifier.fillMaxWidth().aspectRatio23()) {
            Box(Modifier.fillMaxSize()) {
                PosterImage(
                    item.poster, item.title, Modifier.fillMaxSize(), radius = 18, kind = kind,
                    decodeSize = imageWidth?.let { androidx.compose.ui.unit.DpSize(it, it * 1.5f) },
                )
                com.ultratv.tv.nativeapp.ui.common.LangBadge(item.lang, modifier = Modifier.align(Alignment.TopStart).padding(10.design))
                if (item.watched) WatchedBadge(Modifier.align(Alignment.BottomEnd).padding(8.design))
            }
        }
        // Deux lignes réservées (cellules alignées dans la rangée / la grille) : un titre long n'est plus coupé à 15 caractères.
        Text(item.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 13.sp else 22.spx,
            lineHeight = if (touch) 16.sp else 27.spx, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // Formatage (String.format) une fois par affiche, pas à chaque recomposition de la cellule.
        val meta = androidx.compose.runtime.remember(item.year, item.rating) { listOfNotNull(item.year?.toString(), item.rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) }).joinToString(" · ") }
        Text(meta, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(if (touch) 16.dp else 30.design))
    }
}

private val Color0xE4E4E7 = androidx.compose.ui.graphics.Color(0xFFE4E4E7)
private fun Modifier.aspectRatio23() = this.aspectRatio(2f / 3f)

/** Pastille « ✓ Vu » (film vu sur Trakt) : discrète, en bas à droite de l'affiche. */
@Composable
internal fun WatchedBadge(modifier: Modifier = Modifier) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    Text(
        "✓ " + com.ultratv.tv.nativeapp.i18n.LocalDs.current.traktWatched, color = Ux.White, fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = if (touch) 10.sp else 20.spx, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.design)).background(androidx.compose.ui.graphics.Color(0xCC0A0A0C)).padding(horizontal = 8.design, vertical = 3.design),
    )
}

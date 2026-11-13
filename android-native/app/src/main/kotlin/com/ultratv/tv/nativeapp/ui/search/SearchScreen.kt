package com.ultratv.tv.nativeapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.FuzzySearch
import com.ultratv.tv.nativeapp.data.repo.ProgramHit
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SearchRank
import com.ultratv.tv.nativeapp.data.repo.SearchResults
import com.ultratv.tv.nativeapp.data.repo.SearchSort
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.locale
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Onglets de la recherche : tout, puis un onglet par type (masqué quand il ne contient rien). */
enum class SearchTab { ALL, MOVIES, SERIES, CHANNELS, PROGRAMS }

/**
 * Résultats prêts à afficher : TOUS les résultats de chaque type (plus de troncature à 12 titres), triés selon le choix
 * de l'utilisateur, avec le meilleur résultat mis en avant (tri « Pertinence », titre identique ou qui commence par la saisie).
 */
data class SearchView(
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val programs: List<ProgramHit> = emptyList(),
    /** [MovieEntity] ou [SeriesEntity]. */
    val best: Any? = null,
    /** Résultats du repli tolérant aux fautes : affichés comme « titres proches » avec une suggestion. */
    val fuzzy: Boolean = false,
    /** Saisie pour laquelle cette vue a été calculée (calcul en différé : elle peut retarder sur la saisie). */
    val query: String = "",
) {
    val total get() = movies.size + series.size + channels.size + programs.size
    fun count(t: SearchTab) = when (t) {
        SearchTab.ALL -> total
        SearchTab.MOVIES -> movies.size
        SearchTab.SERIES -> series.size
        SearchTab.CHANNELS -> channels.size
        SearchTab.PROGRAMS -> programs.size
    }
}

/** Plafond par type : de quoi tout parcourir (affichage paresseux) sans charger un catalogue entier pour « a ». */
private const val SEARCH_LIMIT = 500

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val provider: ProviderRepository,
    private val catalog: CatalogRepository,
    private val history: com.ultratv.tv.nativeapp.data.prefs.SearchHistoryStore,
    private val liveZap: com.ultratv.tv.nativeapp.data.repo.LiveZapContext,
    private val movieDao: com.ultratv.tv.nativeapp.data.db.MovieDao,
    private val seriesDao: com.ultratv.tv.nativeapp.data.db.SeriesDao,
    private val indexHealth: com.ultratv.tv.nativeapp.data.repo.SearchIndexHealth,
) : ViewModel() {
    /** Chaîne trouvée : contexte « LIVE » + file de zapping avant d'ouvrir le lecteur (OSD du direct, OK = liste). */
    fun playChannel(c: ChannelEntity, then: () -> Unit) {
        viewModelScope.launch {
            runCatching { liveZap.prepare(c.providerId, c.remoteId, c.title, c.logo, c.streamUrl) }
            then()
        }
    }

    private val _q = MutableStateFlow("")
    val query: StateFlow<String> = _q.asStateFlow()

    /** Résultats bruts et la saisie qui les a produits (le classement se fait sur CETTE saisie, pas sur la frappe en cours). */
    private data class Hits(val q: String, val r: SearchResults)
    private val _hits = MutableStateFlow(Hits("", SearchResults()))

    private val _tab = MutableStateFlow(SearchTab.ALL)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()
    private val _sort = MutableStateFlow(SearchSort.RELEVANCE)
    val sort: StateFlow<SearchSort> = _sort.asStateFlow()
    fun setTab(t: SearchTab) { _tab.value = t }
    fun setSort(s: SearchSort) { _sort.value = s; viewModelScope.launch { _view.value = withContext(Dispatchers.Default) { build(_hits.value, s) } } }

    // Vue calculée DANS la recherche, avant qu'elle ne soit déclarée finie : calculée à part (flux différé), elle retardait
    // sur la saisie — l'écran montrait encore les résultats précédents, ou « Aucun résultat », puis les bons un moment après.
    private val _view = MutableStateFlow(SearchView())
    val view: StateFlow<SearchView> = _view.asStateFlow()

    private suspend fun publish(h: Hits) {
        _hits.value = h
        _view.value = withContext(Dispatchers.Default) { build(h, _sort.value) }
    }

    val recent: StateFlow<List<String>> = history.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** « Ajoutés récemment » (avant la saisie) : films et séries les plus récents de la source, entrelacés. */
    private val _latest = MutableStateFlow<List<Any>>(emptyList())
    val latest: StateFlow<List<Any>> = _latest.asStateFlow()

    /** Vrai tant que la requête en cours n'a pas rendu ses résultats (évite d'afficher « Aucun résultat » à tort). */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var job: Job? = null

    init {
        // Index de recherche réparé s'il a été laissé incomplet (synchro interrompue) : une fois par lancement.
        // Synchro en cours à l'ouverture : la vérification était abandonnée et jamais retentée — un index cassé ne rendait
        // alors plus aucun résultat. Elle est relancée à chaque fin de synchro (et à chaque recherche, voir setQuery).
        viewModelScope.launch(Dispatchers.IO) { indexHealth.idleTicks().collect { runCatching { indexHealth.ensure() } } }
        viewModelScope.launch {
            val pid = provider.firstActiveId() ?: return@launch
            runCatching {
                val m = movieDao.latest(pid, 12); val s = seriesDao.latest(pid, 8)
                val out = ArrayList<Any>(m.size + s.size)
                for (i in 0 until maxOf(m.size, s.size)) { m.getOrNull(i)?.let(out::add); s.getOrNull(i)?.let(out::add) }
                _latest.value = out
            }
        }
    }

    fun setQuery(s: String) {
        if (s != _q.value) _tab.value = SearchTab.ALL
        _q.value = s
        job?.cancel()
        _searching.value = s.isNotBlank()
        viewModelScope.launch(Dispatchers.IO) { runCatching { indexHealth.ensure() } }   // sans attendre (base peut-être occupée)
        job = viewModelScope.launch {
            delay(220)
            // Aucune source : on ne laisse pas « Recherche… » affiché indéfiniment.
            val pid = provider.firstActiveId() ?: run { publish(Hits(s, SearchResults())); _searching.value = false; return@launch }
            // Chaînes / films / séries (FTS, instantané) affichés d'abord ; les programmes du guide (plus lents) ensuite.
            // La vérification de l'index n'est PLUS attendue ici : elle passe par la connexion d'écriture de la base, prise
            // par une synchro en cours ; la recherche restait alors « Recherche… » plus de 30 s. Elle tourne à part (init).
            val t0 = android.os.SystemClock.elapsedRealtime()
            // « Aucun résultat » seulement quand TOUT est fini : la recherche rapide vide laissait ce message à l'écran
            // pendant que les programmes du guide étaient encore cherchés. Une erreur ne laisse jamais le chargement affiché.
            try {
                val quick = catalog.search(pid, s, limit = SEARCH_LIMIT, includePrograms = false)
                // Mesure (jamais le texte saisi) : durée de la recherche rapide.
                android.util.Log.i("UltraSearch", "quick len=${s.length} ms=${android.os.SystemClock.elapsedRealtime() - t0} hits=${quick.channels.size + quick.movies.size + quick.series.size}")
                publish(Hits(s, quick))
                if (s.trim().length >= 3) publish(Hits(s, quick.copy(programs = catalog.searchPrograms(pid, s))))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                publish(Hits(s, SearchResults()))   // erreur : « Aucun résultat » plutôt qu'un chargement sans fin
            } finally {
                if (_q.value == s) _searching.value = false
            }
            // Récents : seulement la saisie finale (une nouvelle touche annule ce délai).
            if (s.length >= 3) { delay(1_500); history.record(s) }
        }
    }

    fun append(c: Char) { setQuery(query.value + c) }
    fun backspace() { setQuery(query.value.dropLast(1)) }
    fun clear() { setQuery("") }
    fun clearHistory() { viewModelScope.launch { history.clear() } }

    private fun build(h: Hits, sort: SearchSort): SearchView {
        val r = h.r
        if (r.fuzzy) {
            // Repli tolérant aux fautes : l'ordre de ressemblance du dépôt est le bon, on ne le retrie pas.
            return SearchView(movies = r.movies, series = r.series, channels = r.channels, programs = r.programs, fuzzy = true, query = h.q)
        }
        val movies = SearchRank.order(r.movies, h.q, sort, { it.title }, { it.year }, { it.rating })
        val series = SearchRank.order(r.series, h.q, sort, { it.title }, { it.year }, { it.rating })
        val chSort = if (sort == SearchSort.TITLE) SearchSort.TITLE else SearchSort.RELEVANCE
        val channels = SearchRank.order(r.channels, h.q, chSort, { it.title }, { null }, { null })
        val best: Any? = if (sort != SearchSort.RELEVANCE) null else {
            val m = movies.firstOrNull(); val s = series.firstOrNull()
            val rm = m?.let { SearchRank.relevance(h.q, it.title) } ?: 0
            val rs = s?.let { SearchRank.relevance(h.q, it.title) } ?: 0
            when { rm >= 3 && rm >= rs -> m; rs >= 3 -> s; else -> null }
        }
        return SearchView(movies, series, channels, r.programs, best, false, query = h.q)
    }
}

private const val KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val CHEVRON = "M9 6l6 6-6 6"

/** « En cours » ou « Mar. 21:30 » (jour abrégé + heure, langue de l'application). */
private fun programWhen(h: ProgramHit, D: com.ultratv.tv.nativeapp.i18n.DesignStrings): String =
    if (h.live) D.programNow else java.text.SimpleDateFormat("EEE", D.locale).apply { timeZone = com.ultratv.tv.nativeapp.ui.common.EpgClock.timeZone() }.format(java.util.Date(h.startMs)).replaceFirstChar { it.uppercase() } + " " + com.ultratv.tv.nativeapp.ui.common.EpgClock.wall(h.startMs)

private fun tabLabel(t: SearchTab, D: com.ultratv.tv.nativeapp.i18n.DesignStrings) = when (t) {
    SearchTab.ALL -> D.searchTabAll
    SearchTab.MOVIES -> D.searchTabMovies
    SearchTab.SERIES -> D.searchTabSeries
    SearchTab.CHANNELS -> D.searchTabChannels
    SearchTab.PROGRAMS -> D.searchTabPrograms
}

/** Note lisible (« ★ 7,4 ») d'après la note de la source ; null si elle n'est pas exploitable. */
private fun ratingLabel(r: Double?, D: com.ultratv.tv.nativeapp.i18n.DesignStrings): String? =
    SearchRank.validRating(r)?.let { "★ " + String.format(D.locale, "%.1f", it) }

/** Éléments ENTIÈREMENT visibles d'une rangée (bornes d'index), pour « 1–6 sur 18 ». */
private fun visibleBounds(info: LazyListLayoutInfo): Pair<Int, Int>? {
    val vis = info.visibleItemsInfo
    if (vis.isEmpty()) return null
    val full = vis.filter { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
    return (full.firstOrNull() ?: vis.first()).index to (full.lastOrNull() ?: vis.last()).index
}

private fun visibleBounds(info: LazyGridLayoutInfo): Pair<Int, Int>? {
    val vis = info.visibleItemsInfo
    if (vis.isEmpty()) return null
    val full = vis.filter { it.offset.y >= info.viewportStartOffset && it.offset.y + it.size.height <= info.viewportEndOffset }
    return (full.firstOrNull() ?: vis.first()).index to (full.lastOrNull() ?: vis.last()).index
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    // (streamUrl, title) — same play path the Live/Guide screens use.
    onOpenChannel: (String, String) -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val q by vm.query.collectAsState()
    val view by vm.view.collectAsState()
    val recent by vm.recent.collectAsState()
    val latest by vm.latest.collectAsState()
    val searching by vm.searching.collectAsState()
    val tab by vm.tab.collectAsState()
    val sort by vm.sort.collectAsState()
    val S = LocalStrings.current
    val D = LocalDs.current
    val dbgQ by com.ultratv.tv.nativeapp.StartupNav.debugQuery.collectAsState()
    androidx.compose.runtime.LaunchedEffect(dbgQ) { dbgQ?.let { vm.setQuery(it); com.ultratv.tv.nativeapp.StartupNav.debugQuery.value = null } }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val voiceIntent = remember {
        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    // Masquée si aucun service de reconnaissance vocale n'est installé.
    val voiceAvailable = remember { runCatching { voiceIntent.resolveActivity(ctx.packageManager) != null }.getOrDefault(false) }
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { vm.setQuery(it.trim()) }
    }
    // Onglet vidé par une nouvelle recherche : retour à « Tout ».
    val effTab = if (tab != SearchTab.ALL && view.count(tab) == 0) SearchTab.ALL else tab
    val openMovie: (MovieEntity) -> Unit = { onOpenMovie(it.id) }
    val openSeries: (SeriesEntity) -> Unit = { onOpenSeries(it.id) }
    val playChannel: (ChannelEntity) -> Unit = { c -> vm.playChannel(c) { onOpenChannel(c.streamUrl, c.name) } }

    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        SearchTouch(q, view, recent, searching, effTab, sort, voiceAvailable, { runCatching { voiceLauncher.launch(voiceIntent) } }, vm, playChannel, openMovie, openSeries)
        return
    }
    // Clavier physique / télécommande à clavier : les caractères tapés vont dans la recherche, où que soit le focus.
    val typing = Modifier.onPreviewKeyEvent { e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val cp = e.utf16CodePoint
        when {
            e.key == androidx.compose.ui.input.key.Key.Backspace -> { vm.backspace(); true }
            cp > 0 && (Character.isLetterOrDigit(cp) || cp == ' '.code) -> { vm.append(cp.toChar().lowercaseChar()); true }
            else -> false
        }
    }
    // Onglet d'un type : le clavier se replie pour laisser la place à la grille ; Retour le rouvre (onglet « Tout »).
    val keyboardShown = q.isBlank() || effTab == SearchTab.ALL
    androidx.activity.compose.BackHandler(enabled = !keyboardShown) { vm.setTab(SearchTab.ALL) }

    Row(Modifier.fillMaxSize().background(Ux.Bg).then(typing)) {
        if (keyboardShown) {
            KeyboardColumn(q, view, voiceAvailable, { runCatching { voiceLauncher.launch(voiceIntent) } }, vm)
            Box(Modifier.width(1.design).fillMaxHeight().background(Ux.Surface))
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                q.isBlank() -> StartPane(recent, latest, vm, openMovie, openSeries)
                // Recherche en cours : un retour visible plutôt qu'un panneau vide (box lente).
                // « Aucun résultat » seulement quand la vue correspond à la saisie ET que tout est fini : avant, la fin de la
                // recherche arrivait avant le calcul (différé) de la vue, et l'écran disait « Aucun résultat » puis
                // affichait les résultats un moment après.
                view.query != q || (searching && view.total == 0) -> SearchingPane(D.searchingLabel)
                view.total == 0 -> Message(S.searchNoMatches)
                view.fuzzy -> FuzzyPane(q, view, vm, openMovie, openSeries)
                effTab == SearchTab.ALL -> AllPane(view, effTab, sort, vm, playChannel, openMovie, openSeries)
                else -> TypePane(q, view, effTab, sort, vm, playChannel, openMovie, openSeries)
            }
        }
    }
}

/** Recherche en cours : indicateur de chargement et libellé, à la place de « Aucun résultat » tant que rien n'est fini. */
@Composable
private fun SearchingPane(text: String) {
    Box(Modifier.fillMaxSize().padding(56.design), contentAlignment = Alignment.TopStart) {
        Row(Modifier.padding(top = 60.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.design)) {
            androidx.compose.material3.CircularProgressIndicator(color = Ux.Accent, strokeWidth = 4.design, modifier = Modifier.size(36.design))
            Text(text, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(56.design), contentAlignment = Alignment.TopStart) {
        Text(text, color = Ux.Text3, fontFamily = Manrope, fontSize = 26.spx, modifier = Modifier.padding(top = 60.design))
    }
}

/** Colonne de gauche : titre, champ, clavier 6 colonnes, touches utilitaires. */
@Composable
private fun KeyboardColumn(q: String, view: SearchView, voiceAvailable: Boolean, onVoice: () -> Unit, vm: SearchViewModel) {
    val S = LocalStrings.current
    val D = LocalDs.current
    Column(
        Modifier.width(600.design).fillMaxHeight().padding(start = 72.design, end = 48.design, top = 54.design, bottom = 40.design),
        verticalArrangement = Arrangement.spacedBy(24.design),
    ) {
        SectionTitle(S.navSearch, 44)
        Row(
            Modifier.fillMaxWidth().height(80.design).clip(RoundedCornerShape(20.design)).background(Ux.SurfaceDeep).border(3.design, Ux.Accent, RoundedCornerShape(20.design)).padding(horizontal = 24.design),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design),
        ) {
            DIcon(Icons.Search, 30.design, Ux.Text3, strokeWidth = 2.2f)
            Text(
                q.ifEmpty { S.searchPlaceholder }, color = if (q.isEmpty()) Ux.Muted else Ux.Text,
                fontFamily = Manrope, fontWeight = if (q.isEmpty()) FontWeight.SemiBold else FontWeight.Bold, fontSize = if (q.isEmpty()) 26.spx else 32.spx,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (q.isNotBlank() && view.total > 0) Text("${view.total}", color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
            KEYS.toList().chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.design)) {
                    row.forEach { ch -> Key(ch.toString(), Modifier.weight(1f), onClick = { vm.append(ch.lowercaseChar()) }) }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.design)) {
            if (voiceAvailable) Key(D.keyVoice, Modifier.weight(1f), small = true, onClick = onVoice)
            Key(D.keySpace, Modifier.weight(1f), small = true, onClick = { vm.append(' ') })
            Key(D.keyDelete, Modifier.weight(1f), small = true, onClick = { vm.backspace() })
            Key(S.searchClear, Modifier.weight(1f), small = true, onClick = { vm.clear() })
        }
        Text(D.searchInputHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 19.spx, lineHeight = 28.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.design), bg = Ux.Surface, ringWidth = 4.design, focusedScale = 1f, modifier = modifier.height(62.design)) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = if (small) FontWeight.SemiBold else FontWeight.Bold, fontSize = if (small) 20.spx else 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pastille d'onglet (compteur réel) ou de tri : sélectionnée = fond + soulignement accent ; autres = contour. */
@Composable
private fun Chip(label: String, count: Int?, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val h = if (small) 44 else 52
    val shape = RoundedCornerShape(if (small) 12.design else 26.design)
    FocusSurface(
        onClick = onClick, shape = shape, bg = if (selected) Ux.Tone else Color.Transparent, ringWidth = 4.design, focusedScale = 1f,
        modifier = Modifier.height(h.design).then(if (!selected && !small) Modifier.border(2.design, Ux.Tone, shape) else Modifier),
    ) { f ->
        Row(Modifier.height(h.design).padding(horizontal = if (small) 16.design else 22.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
            val ink = if (f) Ux.TextOnLight else if (selected) Ux.Text else if (small) Ux.Text3 else Ux.Text2
            Text(label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = (if (small) 18 else 21).spx, maxLines = 1)
            if (count != null) Text("$count", color = ink.copy(alpha = 0.75f), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1)
        }
        if (selected && !f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.design).background(Ux.Accent))
    }
}

/** Onglets (seulement ceux qui ont des résultats) puis tri ; [compact] = requête repliée en tête (onglet d'un type). */
@Composable
private fun TabsBar(q: String?, view: SearchView, tab: SearchTab, sort: SearchSort, vm: SearchViewModel, requester: androidx.compose.ui.focus.FocusRequester? = null) {
    val D = LocalDs.current
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.design), verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(end = 72.design),
        // Cible de « Haut » depuis les résultats ; le focus revient sur le dernier onglet ou tri utilisé.
        modifier = Modifier.fillMaxWidth().then(if (requester != null) Modifier.focusRequester(requester).focusRestorer() else Modifier),
    ) {
        if (q != null) item(key = "query", contentType = "query") { QueryChip(q) { vm.setTab(SearchTab.ALL) } }
        for (t in SearchTab.entries) if (t == SearchTab.ALL || view.count(t) > 0) item(key = "t-${t.name}", contentType = "tab") {
            Chip(tabLabel(t, D), view.count(t), t == tab) { vm.setTab(t) }
        }
        if (tab != SearchTab.PROGRAMS) {
            item(key = "sortLabel", contentType = "label") {
                Text(D.searchSort, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, modifier = Modifier.padding(start = 24.design, end = 4.design))
            }
            val sorts = if (tab == SearchTab.CHANNELS) listOf(SearchSort.RELEVANCE, SearchSort.TITLE) else SearchSort.entries
            for (s in sorts) item(key = "s-${s.name}", contentType = "sort") { Chip(D.searchSortName(s), null, s == sort, small = true) { vm.setSort(s) } }
        }
    }
}

/** Requête repliée (onglet d'un type) : OK ou Retour rouvre le clavier. */
@Composable
private fun QueryChip(q: String, onClick: () -> Unit) {
    val D = LocalDs.current
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.height(56.design).width(340.design).border(2.design, Ux.Tone, RoundedCornerShape(16.design))) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 20.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
            DIcon(Icons.Search, 24.design, if (f) Ux.TextOnLight else Ux.Text3, strokeWidth = 2.2f)
            Text(q, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("◀ " + D.searchKeyboard, color = if (f) Ux.TextOnLight else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1)
        }
    }
}

/** En-tête de section : « FILMS · 18 », « 1–6 sur 18 », « Tout voir → ». */
@Composable
private fun SectionHeader(title: String, range: Pair<Int, Int>?, total: Int, onSeeAll: (() -> Unit)?) {
    val D = LocalDs.current
    Row(Modifier.fillMaxWidth().padding(end = 72.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
        Text(title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, letterSpacing = 2.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (range != null) Text(D.searchRange(range.first, range.second, total), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 19.spx, maxLines = 1)
        if (onSeeAll != null) FocusSurface(onClick = onSeeAll, shape = RoundedCornerShape(12.design), bg = Color.Transparent, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.height(44.design)) { f ->
            Box(Modifier.height(44.design).padding(horizontal = 14.design), contentAlignment = Alignment.Center) {
                Text(D.searchSeeAll + " →", color = if (f) Ux.TextOnLight else Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 19.spx, maxLines = 1)
            }
        }
    }
}

/** Rangée horizontale de TOUS les éléments (défile jusqu'au dernier), avec son compteur « 1–6 sur 18 ». */
@Composable
private fun <T> ResultRow(title: String, items: List<T>, key: (T) -> Any, onSeeAll: (() -> Unit)?, spacing: Int = 20, card: @Composable (T) -> Unit) {
    val state = rememberLazyListState()
    val bounds by remember(state) { derivedStateOf { visibleBounds(state.layoutInfo) } }
    val range = bounds?.let { SearchRank.visibleRange(it.first, it.second, items.size) }
    Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
        SectionHeader(title, range, items.size, onSeeAll)
        LazyRow(
            state = state, horizontalArrangement = Arrangement.spacedBy(spacing.design),
            contentPadding = PaddingValues(start = 6.design, end = 72.design, top = 8.design, bottom = 8.design),
        ) {
            items(items.size, key = { key(items[it]) }, contentType = { "card" }) { i -> card(items[i]) }
        }
    }
}

@Composable
private fun MoviePoster(m: MovieEntity, width: Int?, onOpen: (MovieEntity) -> Unit) =
    VodCard(m.title, m.poster, if (width != null) Modifier.width(width.design) else Modifier.fillMaxWidth(), TmdbKind.MOVIE, m.year) { onOpen(m) }

@Composable
private fun SeriesPoster(s: SeriesEntity, width: Int?, onOpen: (SeriesEntity) -> Unit) =
    VodCard(s.title, s.poster, if (width != null) Modifier.width(width.design) else Modifier.fillMaxWidth(), TmdbKind.TV, s.year) { onOpen(s) }

/** Onglet « Tout » : meilleur résultat, puis une rangée complète par type. */
@Composable
private fun AllPane(
    view: SearchView, tab: SearchTab, sort: SearchSort, vm: SearchViewModel,
    playChannel: (ChannelEntity) -> Unit, openMovie: (MovieEntity) -> Unit, openSeries: (SeriesEntity) -> Unit,
) {
    val D = LocalDs.current
    Column(Modifier.fillMaxSize().padding(start = 56.design, top = 54.design)) {
        val tabsReq = remember { androidx.compose.ui.focus.FocusRequester() }
        TabsBar(null, view, tab, sort, vm, tabsReq)
        // Haut depuis le haut des résultats : les onglets, juste au-dessus (avant : la touche du clavier la plus proche
        // à l'écran, « F », et les onglets n'étaient plus atteignables à la télécommande).
        LazyColumn(Modifier.fillMaxSize().upExitTo(tabsReq), contentPadding = PaddingValues(top = 28.design, bottom = 54.design), verticalArrangement = Arrangement.spacedBy(34.design)) {
            view.best?.let { b -> item(key = "best", contentType = "best") { BestResult(b, openMovie, openSeries) } }
            if (view.movies.isNotEmpty()) item(key = "movies", contentType = "row") {
                ResultRow(D.searchMovies(view.movies.size), view.movies, { it.id }, { vm.setTab(SearchTab.MOVIES) }) { MoviePoster(it, 180, openMovie) }
            }
            if (view.series.isNotEmpty()) item(key = "series", contentType = "row") {
                ResultRow(D.searchSeries(view.series.size), view.series, { it.id }, { vm.setTab(SearchTab.SERIES) }) { SeriesPoster(it, 180, openSeries) }
            }
            if (view.channels.isNotEmpty()) item(key = "channels", contentType = "row") {
                ResultRow(D.searchChannels(view.channels.size), view.channels, { it.id }, { vm.setTab(SearchTab.CHANNELS) }, spacing = 16) { c ->
                    ChannelCard(c, Modifier.width(400.design)) { playChannel(c) }
                }
            }
            if (view.programs.isNotEmpty()) item(key = "programs", contentType = "row") {
                ResultRow(D.searchPrograms(view.programs.size), view.programs, { "${it.channel.id}-${it.startMs}" }, { vm.setTab(SearchTab.PROGRAMS) }, spacing = 16) { h ->
                    ProgramCard(h, D, Modifier.width(560.design)) { playChannel(h.channel) }
                }
            }
        }
    }
}

/** « MEILLEUR RÉSULTAT » : fond (ou affiche), titre, type · année · durée · note, résumé, bouton Fiche. */
@Composable
private fun BestResult(b: Any, openMovie: (MovieEntity) -> Unit, openSeries: (SeriesEntity) -> Unit) {
    val D = LocalDs.current
    val m = b as? MovieEntity
    val s = b as? SeriesEntity
    val title = m?.title ?: s?.title ?: return
    val backdrop = m?.backdrop ?: s?.backdrop
    val poster = m?.poster ?: s?.poster
    val year = m?.year ?: s?.year
    val kind = if (m != null) TmdbKind.MOVIE else TmdbKind.TV
    val meta = listOfNotNull(
        if (m != null) D.searchTabMovies else D.searchTabSeries,
        year?.toString(),
        m?.duration?.takeIf { it.isNotBlank() },
        ratingLabel(m?.rating ?: s?.rating, D),
    ).joinToString("  ·  ")
    val plot = (m?.plot ?: s?.plot)?.takeIf { it.isNotBlank() }
    Row(Modifier.fillMaxWidth().padding(end = 72.design), horizontalArrangement = Arrangement.spacedBy(32.design), verticalAlignment = Alignment.CenterVertically) {
        if (!backdrop.isNullOrBlank()) BackdropImage(backdrop, Modifier.width(480.design).height(270.design).clip(RoundedCornerShape(20.design)))
        else PosterImage(poster, title, Modifier.width(180.design).height(270.design), radius = 20, kind = kind, year = year)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.design)) {
            Text(D.searchBest, color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx, letterSpacing = 1.8.sp, maxLines = 1)
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (plot != null) Text(plot, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, lineHeight = 29.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            PillButton(D.searchDetails, onClick = { if (m != null) openMovie(m) else if (s != null) openSeries(s) }, heightPx = 56, hPadPx = 28, fontPx = 21, weight = FontWeight.Bold, ringWidth = 4.design, modifier = Modifier.padding(top = 6.design))
        }
    }
}

/** Onglet d'un type : grille complète qui défile vers le bas (films, séries, chaînes) ou liste (programmes). */
@Composable
private fun TypePane(
    q: String, view: SearchView, tab: SearchTab, sort: SearchSort, vm: SearchViewModel,
    playChannel: (ChannelEntity) -> Unit, openMovie: (MovieEntity) -> Unit, openSeries: (SeriesEntity) -> Unit,
) {
    val D = LocalDs.current
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val total = view.count(tab)
    val bounds by remember(grid) { derivedStateOf { visibleBounds(grid.layoutInfo) } }
    val title = when (tab) {
        SearchTab.MOVIES -> D.searchMovies(total)
        SearchTab.SERIES -> D.searchSeries(total)
        SearchTab.CHANNELS -> D.searchChannels(total)
        else -> D.searchPrograms(total)
    }
    val titles: List<String> = when (tab) {
        SearchTab.MOVIES -> view.movies.map { it.title }
        SearchTab.SERIES -> view.series.map { it.title }
        else -> emptyList()
    }
    // Rail de lettres : seulement en tri A–Z (la liste est alors dans l'ordre des initiales).
    val letters = remember(titles, sort) { if (sort == SearchSort.TITLE && titles.isNotEmpty()) SearchRank.letterIndex(titles) { it } else emptyMap() }
    val current = bounds?.first?.let { titles.getOrNull(it) }?.let { SearchRank.initial(it) }
    Column(Modifier.fillMaxSize().padding(start = 72.design, top = 54.design)) {
        val tabsReq = remember { androidx.compose.ui.focus.FocusRequester() }
        TabsBar(q, view, tab, sort, vm, tabsReq)
        Spacer(Modifier.height(24.design))
        SectionHeader(title, bounds?.let { SearchRank.visibleRange(it.first, it.second, total) }, total, null)
        Row(Modifier.fillMaxSize().padding(top = 20.design).upExitTo(tabsReq)) {
            val cols = if (tab == SearchTab.CHANNELS) 3 else if (tab == SearchTab.PROGRAMS) 2 else 8
            LazyVerticalGrid(
                GridCells.Fixed(cols), state = grid, modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 6.design, top = 8.design, bottom = 54.design, end = if (letters.isEmpty()) 72.design else 24.design),
                horizontalArrangement = Arrangement.spacedBy(if (cols == 8) 28.design else 16.design),
                verticalArrangement = Arrangement.spacedBy(if (cols == 8) 28.design else 16.design),
            ) {
                when (tab) {
                    SearchTab.MOVIES -> items(view.movies.size, key = { view.movies[it].id }, contentType = { "poster" }) { MoviePoster(view.movies[it], null, openMovie) }
                    SearchTab.SERIES -> items(view.series.size, key = { view.series[it].id }, contentType = { "poster" }) { SeriesPoster(view.series[it], null, openSeries) }
                    SearchTab.CHANNELS -> items(view.channels.size, key = { view.channels[it].id }, contentType = { "channel" }) { i ->
                        val c = view.channels[i]; ChannelCard(c, Modifier.fillMaxWidth()) { playChannel(c) }
                    }
                    else -> items(view.programs.size, key = { "${view.programs[it].channel.id}-${view.programs[it].startMs}" }, contentType = { "program" }) { i ->
                        val h = view.programs[i]; ProgramCard(h, D, Modifier.fillMaxWidth()) { playChannel(h.channel) }
                    }
                }
            }
            if (letters.isNotEmpty()) LetterRail(letters, current) { idx -> scope.launch { grid.scrollToItem(idx) } }
        }
    }
}

/** Rail A–Z : lettres présentes focalisables (OK = saut à la première œuvre), absentes grisées. */
@Composable
private fun LetterRail(letters: Map<Char, Int>, current: Char?, onJump: (Int) -> Unit) {
    val all = listOf('#') + ('A'..'Z').toList()
    Column(Modifier.width(64.design).fillMaxHeight().padding(end = 12.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.design)) {
        for (c in all) {
            val idx = letters[c]
            if (idx == null) {
                if (c != '#') Box(Modifier.width(44.design).height(28.design), contentAlignment = Alignment.Center) {
                    Text(c.toString(), color = Ux.Muted2, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx)
                }
            } else {
                val on = c == current
                FocusSurface(onClick = { onJump(idx) }, shape = RoundedCornerShape(8.design), bg = if (on) Ux.Accent else Color.Transparent, ringWidth = 3.design, focusedScale = 1f, modifier = Modifier.width(44.design).height(28.design)) { f ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(c.toString(), color = if (f) Ux.TextOnLight else if (on) Ux.OnAccent else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Avant la saisie : recherches récentes (+ effacer l'historique), puis ajouts récents de la source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPane(recent: List<String>, latest: List<Any>, vm: SearchViewModel, openMovie: (MovieEntity) -> Unit, openSeries: (SeriesEntity) -> Unit) {
    val S = LocalStrings.current
    val D = LocalDs.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 56.design, top = 64.design, bottom = 54.design), verticalArrangement = Arrangement.spacedBy(44.design)) {
        if (recent.isNotEmpty()) item(key = "recent", contentType = "recent") {
            Column(Modifier.padding(end = 72.design), verticalArrangement = Arrangement.spacedBy(16.design)) {
                Text(S.searchRecent.trimEnd(':', ' ').uppercase(D.locale), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, letterSpacing = 2.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.design), verticalArrangement = Arrangement.spacedBy(12.design), maxLines = 3) {
                    recent.take(10).forEach { rec ->
                        FocusSurface(onClick = { vm.setQuery(rec) }, shape = RoundedCornerShape(26.design), bg = Ux.Surface, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.height(52.design)) { f ->
                            Row(Modifier.height(52.design).padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                                DIcon("M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16M12 8v4l3 2", 20.design, if (f) Ux.TextOnLight else Ux.Text3)
                                Text(rec, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 21.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Chip(D.searchClearHistory, null, selected = false) { vm.clearHistory() }
                }
            }
        }
        if (latest.isNotEmpty()) item(key = "latest", contentType = "row") {
            Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                Text(D.searchRecentlyAdded, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, letterSpacing = 2.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.design), contentPadding = PaddingValues(start = 6.design, end = 72.design, top = 8.design, bottom = 8.design)) {
                    items(latest.size, key = { i -> latest[i].let { if (it is MovieEntity) "m${it.id}" else "s${(it as SeriesEntity).id}" } }, contentType = { "poster" }) { i ->
                        when (val v = latest[i]) {
                            is MovieEntity -> MoviePoster(v, 180, openMovie)
                            is SeriesEntity -> SeriesPoster(v, 180, openSeries)
                        }
                    }
                }
            }
        }
        if (recent.isEmpty() && latest.isEmpty()) item(key = "start") {
            Text(D.searchStart, color = Ux.Text3, fontFamily = Manrope, fontSize = 26.spx, modifier = Modifier.padding(end = 72.design))
        }
    }
}

/** Titre le plus proche de la saisie parmi les premiers films et séries du repli tolérant (« Vouliez-vous dire … ? »). */
private fun suggestion(q: String, view: SearchView): String? =
    listOfNotNull(view.movies.firstOrNull()?.title, view.series.firstOrNull()?.title).maxByOrNull { FuzzySearch.score(q, it) }

/** Aucun titre ne contient la saisie : suggestion, titres proches, puis le reste (chaînes, programmes). */
@Composable
private fun FuzzyPane(q: String, view: SearchView, vm: SearchViewModel, openMovie: (MovieEntity) -> Unit, openSeries: (SeriesEntity) -> Unit) {
    val D = LocalDs.current
    val close: List<Any> = remember(view) { view.movies + view.series }
    val sugg = remember(q, view) { suggestion(q, view) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 56.design, top = 96.design, bottom = 54.design), verticalArrangement = Arrangement.spacedBy(36.design)) {
        item(key = "head") {
            Column(Modifier.padding(end = 72.design), verticalArrangement = Arrangement.spacedBy(18.design)) {
                Text(D.searchNoExact(q), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sugg != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                    Text(D.searchDidYouMean, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1)
                    PillButton(sugg, onClick = { vm.setQuery(sugg.lowercase(D.locale)) }, heightPx = 64, hPadPx = 28, fontPx = 30, weight = FontWeight.Bold, ringWidth = 4.design)
                    Text("?", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx)
                }
            }
        }
        item(key = "close", contentType = "row") {
            ResultRow(D.searchCloseTitles, close, { if (it is MovieEntity) "m${it.id}" else "s${(it as SeriesEntity).id}" }, null) { v ->
                when (v) {
                    is MovieEntity -> MoviePoster(v, 180, openMovie)
                    is SeriesEntity -> SeriesPoster(v, 180, openSeries)
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(c: ChannelEntity, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f, modifier = modifier.height(96.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            LogoBox(c.logo, c.title, Modifier.width(64.design).height(44.design), radius = 10, pad = 4, bg = if (f) Ux.Surface else Ux.Surface2)
            Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, lineHeight = 28.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProgramCard(h: ProgramHit, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f, modifier = modifier.height(96.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            LogoBox(h.channel.logo, h.channel.title, Modifier.width(64.design).height(44.design), radius = 10, pad = 4, bg = if (f) Ux.Surface else Ux.Surface2)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(h.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(h.channel.title + " · " + programWhen(h, D), color = if (f) Ux.TextOnLight else Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (h.live) LiveBadge(D.live)
        }
    }
}

// ───────────────────────────── Tactile (téléphone, tablette) ─────────────────────────────

/** Nombre d'éléments par type dans l'onglet « Tout » sur téléphone ; « Tout voir » ouvre l'onglet complet. */
private const val TOUCH_PREVIEW = 5

/**
 * Recherche tactile : champ du SYSTÈME (clavier Android), onglets en pastilles, tri, listes complètes par type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchTouch(
    q: String, view: SearchView, recent: List<String>, searching: Boolean, tab: SearchTab, sort: SearchSort,
    voiceAvailable: Boolean, onVoice: () -> Unit, vm: SearchViewModel,
    playChannel: (ChannelEntity) -> Unit, openMovie: (MovieEntity) -> Unit, openSeries: (SeriesEntity) -> Unit,
) {
    val S = LocalStrings.current
    val D = LocalDs.current
    val M = com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(Unit) { if (q.isEmpty()) runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler(enabled = q.isNotBlank() && tab != SearchTab.ALL) { vm.setTab(SearchTab.ALL) }
    Column(Modifier.fillMaxSize().background(Ux.Bg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            com.ultratv.tv.nativeapp.ui.mobile.IconCircle(com.ultratv.tv.nativeapp.ui.mobile.MobileIcons.Back, M.a11yBack, Ux.Surface, Ux.Text, com.ultratv.tv.nativeapp.ui.mobile.LocalNavBack.current)
            Text(S.navSearch, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Ux.SurfaceDeep).border(2.dp, Ux.Accent, RoundedCornerShape(14.dp)).padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DIcon(Icons.Search, 20.dp, Ux.Text3)
            androidx.compose.foundation.text.BasicTextField(
                value = q, onValueChange = { vm.setQuery(it) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ux.Accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = M.a11ySearch },
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (q.isEmpty()) Text(M.searchHint, color = Ux.Muted, fontFamily = Manrope, fontSize = 16.sp, maxLines = 1); inner() } },
            )
            if (q.isNotEmpty()) com.ultratv.tv.nativeapp.ui.mobile.IconCircle(com.ultratv.tv.nativeapp.ui.mobile.MobileIcons.Close, M.clearSearch, Color.Transparent, Ux.Text2, { vm.clear() })
            else if (voiceAvailable) com.ultratv.tv.nativeapp.ui.mobile.IconCircle("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3", D.keyVoice, Color.Transparent, Ux.Text2, onVoice)
        }
        when {
            q.isBlank() -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recent.isNotEmpty()) {
                    Text(S.searchRecent.trimEnd(':', ' ').uppercase(D.locale), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.take(8).forEach { rec -> TouchChip(rec, false) { vm.setQuery(rec) } }
                        TouchChip(D.searchClearHistory, false) { vm.clearHistory() }
                    }
                } else Text(D.searchStart, color = Ux.Text3, fontFamily = Manrope, fontSize = 15.sp)
            }
            view.query != q || (searching && view.total == 0) -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                androidx.compose.material3.CircularProgressIndicator(color = Ux.Accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp).semantics { contentDescription = D.searchingLabel })
            }
            view.total == 0 -> Text(S.searchNoMatches, color = Ux.Text3, fontFamily = Manrope, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    for (t in SearchTab.entries) if (t == SearchTab.ALL || view.count(t) > 0) item(key = "t-${t.name}") {
                        TouchChip("${tabLabel(t, D)}  ${view.count(t)}", t == tab) { vm.setTab(t) }
                    }
                }
                if (!view.fuzzy && tab != SearchTab.PROGRAMS) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    item(key = "label") { Text(D.searchSort, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    val sorts = if (tab == SearchTab.CHANNELS) listOf(SearchSort.RELEVANCE, SearchSort.TITLE) else SearchSort.entries
                    for (s in sorts) item(key = "s-${s.name}") { TouchChip(D.searchSortName(s), s == sort, small = true) { vm.setSort(s) } }
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    if (view.fuzzy) item(key = "noexact") { Text(D.searchNoExact(q), color = Ux.Text2, fontFamily = Manrope, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp)) }
                    fun section(t: SearchTab, header: String, n: Int, body: androidx.compose.foundation.lazy.LazyListScope.(Int) -> Unit) {
                        if (n == 0 || (tab != SearchTab.ALL && tab != t)) return
                        item(key = "h-${t.name}") { TouchHeader(header) }
                        val shown = if (tab == SearchTab.ALL) minOf(n, TOUCH_PREVIEW) else n
                        body(shown)
                        if (tab == SearchTab.ALL && n > shown) item(key = "more-${t.name}") { TouchChip(D.searchSeeAll + " (" + n + ")", false) { vm.setTab(t) } }
                    }
                    section(SearchTab.MOVIES, D.searchMovies(view.movies.size), view.movies.size) { shown ->
                        items(shown, key = { "m${view.movies[it].id}" }, contentType = { "vod" }) { i ->
                            val m = view.movies[i]; TouchVodRow(m.title, m.poster, TmdbKind.MOVIE, m.year, listOfNotNull(m.year?.toString(), ratingLabel(m.rating, D)).joinToString(" · ")) { openMovie(m) }
                        }
                    }
                    section(SearchTab.SERIES, D.searchSeries(view.series.size), view.series.size) { shown ->
                        items(shown, key = { "s${view.series[it].id}" }, contentType = { "vod" }) { i ->
                            val s = view.series[i]; TouchVodRow(s.title, s.poster, TmdbKind.TV, s.year, listOfNotNull(s.year?.toString(), ratingLabel(s.rating, D)).joinToString(" · ")) { openSeries(s) }
                        }
                    }
                    section(SearchTab.CHANNELS, D.searchChannels(view.channels.size), view.channels.size) { shown ->
                        items(shown, key = { "c${view.channels[it].id}" }, contentType = { "channel" }) { i -> val c = view.channels[i]; TouchChannelHit(c) { playChannel(c) } }
                    }
                    section(SearchTab.PROGRAMS, D.searchPrograms(view.programs.size), view.programs.size) { shown ->
                        items(shown, key = { "p${view.programs[it].channel.id}-${view.programs[it].startMs}" }, contentType = { "program" }) { i -> val h = view.programs[i]; TouchProgramHit(h, D) { playChannel(h.channel) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchHeader(text: String) =
    Text(text, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))

@Composable
private fun TouchChip(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.dp), bg = if (selected) Ux.Cta else Ux.Surface, modifier = Modifier.height(if (small) 32.dp else 36.dp)) { _ ->
        Box(Modifier.height(if (small) 32.dp else 36.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (small) 13.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TouchVodRow(title: String, poster: String?, kind: TmdbKind, year: Int?, meta: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(12.dp), bg = Color.Transparent, modifier = Modifier.fillMaxWidth().height(90.dp)) { _ ->
        Row(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterImage(poster, title, Modifier.width(52.dp).height(78.dp), radius = 16, kind = kind, year = year)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) Text(meta, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            }
            DIcon(CHEVRON, 18.dp, Ux.Muted, strokeWidth = 2.4f)
        }
    }
}

@Composable
private fun TouchChannelHit(c: ChannelEntity, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.dp), bg = Ux.SurfaceDeep, modifier = Modifier.fillMaxWidth().height(60.dp)) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoBox(c.logo, c.title, Modifier.width(48.dp).height(32.dp), radius = 12, pad = 4)
            Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TouchProgramHit(h: ProgramHit, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.dp), bg = Ux.SurfaceDeep, modifier = Modifier.fillMaxWidth().height(64.dp)) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoBox(h.channel.logo, h.channel.title, Modifier.width(48.dp).height(32.dp), radius = 12, pad = 4)
            Column(Modifier.weight(1f)) {
                Text(h.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(h.channel.title + " · " + programWhen(h, D), color = Ux.Text3, fontFamily = Manrope, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (h.live) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Ux.Accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(D.live, color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
            }
        }
    }
}

/** Sortie par le HAUT de ce groupe : vers [target] (les onglets), les autres directions inchangées. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.upExitTo(target: androidx.compose.ui.focus.FocusRequester): Modifier =
    focusProperties {
        exit = { dir -> if (dir == androidx.compose.ui.focus.FocusDirection.Up) target else androidx.compose.ui.focus.FocusRequester.Default }
    }.focusGroup()

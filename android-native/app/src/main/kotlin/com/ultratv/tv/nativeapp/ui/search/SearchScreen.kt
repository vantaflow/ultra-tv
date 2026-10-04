package com.ultratv.tv.nativeapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.ProgramHit
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SearchResults
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.locale
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val provider: ProviderRepository,
    private val catalog: CatalogRepository,
    private val history: com.ultratv.tv.nativeapp.data.prefs.SearchHistoryStore,
) : ViewModel() {
    private val _q = MutableStateFlow("")
    val query: StateFlow<String> = _q.asStateFlow()
    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    val recent: StateFlow<List<String>> = history.recent
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Vrai tant que la requête en cours n'a pas rendu ses résultats (évite d'afficher « Aucun résultat » à tort). */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var job: Job? = null

    fun setQuery(s: String) {
        _q.value = s
        job?.cancel()
        _searching.value = s.isNotBlank()
        job = viewModelScope.launch {
            delay(220)
            val pid = provider.firstActive()?.id ?: return@launch
            _results.value = catalog.search(pid, s)
            _searching.value = false
            if (s.length >= 3) history.record(s)
        }
    }

    fun append(c: Char) { setQuery(query.value + c) }
    fun backspace() { setQuery(query.value.dropLast(1)) }
    fun clear() { setQuery("") }
    fun clearHistory() { viewModelScope.launch { history.clear() } }
}

private const val KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

/** Une ligne de la colonne de résultats : titre de section, rangée de chaînes (3) ou rangée d'affiches (6). */
private sealed interface Row_ {
    data class Header(val text: String) : Row_
    data class Channels(val items: List<ChannelEntity>) : Row_
    data class Vod(val items: List<Any>) : Row_
    data class Program(val hit: ProgramHit) : Row_
}

/** « En cours » ou « Mar. 21:30 » (jour abrégé + heure, langue de l'application). */
private fun programWhen(h: ProgramHit, D: com.ultratv.tv.nativeapp.i18n.DesignStrings): String =
    if (h.live) D.programNow else java.text.SimpleDateFormat("EEE HH:mm", D.locale).format(java.util.Date(h.startMs)).replaceFirstChar { it.uppercase() }

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
    val r by vm.results.collectAsState()
    val recent by vm.recent.collectAsState()
    val searching by vm.searching.collectAsState()
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

    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        SearchTouch(q, r, recent, searching, voiceAvailable, { runCatching { voiceLauncher.launch(voiceIntent) } }, vm, onOpenChannel, onOpenMovie, onOpenSeries)
        return
    }
    Row(Modifier.fillMaxSize().background(Ux.Bg)) {
        // ===== Gauche : saisie + clavier (620 px) =====
        Column(
            Modifier.width(620.design).fillMaxHeight().padding(start = 72.design, end = 48.design, top = 54.design, bottom = 40.design),
            verticalArrangement = Arrangement.spacedBy(24.design),
        ) {
            SectionTitle(S.navSearch, 48)
            Box(
                Modifier.fillMaxWidth().height(80.design).clip(RoundedCornerShape(20.design)).background(Ux.SurfaceDeep).border(3.design, Ux.Accent, RoundedCornerShape(20.design)).padding(horizontal = 28.design),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    q.ifEmpty { S.searchPlaceholder }, color = if (q.isEmpty()) Ux.Muted else Ux.Text,
                    fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 30.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
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
                if (voiceAvailable) Key(D.keyVoice, Modifier.weight(1f), small = true, onClick = { runCatching { voiceLauncher.launch(voiceIntent) } })
                Key(D.keySpace, Modifier.weight(1f), small = true, onClick = { vm.append(' ') })
                Key(D.keyDelete, Modifier.weight(1f), small = true, onClick = { vm.backspace() })
                Key(S.searchClear, Modifier.weight(1f), small = true, onClick = { vm.clear() })
            }
            if (recent.isNotEmpty()) {
                Text(S.searchRecent.trimEnd(':', ' ').uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.design), verticalArrangement = Arrangement.spacedBy(10.design), maxLines = 2) {
                    recent.take(8).forEach { rec ->
                        FocusSurface(onClick = { vm.setQuery(rec) }, shape = RoundedCornerShape(22.design), bg = Ux.Surface, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.height(44.design)) { f ->
                            Box(Modifier.height(44.design).padding(horizontal = 20.design), contentAlignment = Alignment.Center) {
                                Text(rec, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.width(1.design).fillMaxHeight().background(Ux.Surface))

        // ===== Droite : résultats =====
        val rows = remember(r, D) { buildRows(r, D) }
        val total = r.channels.size + r.movies.size + r.series.size + r.programs.size
        when {
            q.isBlank() -> Box(Modifier.fillMaxSize().padding(56.design), contentAlignment = Alignment.TopStart) {
                Text(D.searchStart, color = Ux.Text3, fontFamily = Manrope, fontSize = 26.spx, modifier = Modifier.padding(top = 60.design))
            }
            searching && total == 0 -> Box(Modifier.fillMaxSize())
            total == 0 -> Box(Modifier.fillMaxSize().padding(56.design), contentAlignment = Alignment.TopStart) {
                Text(S.searchNoMatches, color = Ux.Text3, fontFamily = Manrope, fontSize = 26.spx, modifier = Modifier.padding(top = 60.design))
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.design),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 56.design, end = 96.design, top = 54.design, bottom = 54.design),
            ) {
                items(rows.size) { i ->
                    when (val row = rows[i]) {
                        is Row_.Header -> Text(row.text, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(2.2f, androidx.compose.ui.unit.TextUnitType.Sp), modifier = Modifier.padding(top = if (i == 0) 0.design else 20.design))
                        is Row_.Channels -> Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                            row.items.forEach { c -> ChannelCard(c, Modifier.weight(1f)) { onOpenChannel(c.streamUrl, c.name) } }
                            repeat(3 - row.items.size) { Spacer(Modifier.weight(1f)) }
                        }
                        is Row_.Program -> ProgramCard(row.hit, D) { onOpenChannel(row.hit.channel.streamUrl, row.hit.channel.name) }
                        is Row_.Vod -> Row(horizontalArrangement = Arrangement.spacedBy(20.design)) {
                            row.items.forEach { v ->
                                when (v) {
                                    is MovieEntity -> VodCard(v.title, v.poster, Modifier.weight(1f), TmdbKind.MOVIE, v.year) { onOpenMovie(v.id) }
                                    is SeriesEntity -> VodCard(v.title, v.poster, Modifier.weight(1f), TmdbKind.TV, v.year) { onOpenSeries(v.id) }
                                }
                            }
                            repeat(6 - row.items.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private fun buildRows(r: SearchResults, D: com.ultratv.tv.nativeapp.i18n.DesignStrings): List<Row_> {
    val out = mutableListOf<Row_>()
    if (r.channels.isNotEmpty()) {
        out += Row_.Header(D.searchChannels(r.channels.size))
        r.channels.take(6).chunked(3).forEach { out += Row_.Channels(it) }
    }
    if (r.programs.isNotEmpty()) {
        out += Row_.Header(D.searchPrograms(r.programs.size))
        r.programs.forEach { out += Row_.Program(it) }
    }
    val vod: List<Any> = r.movies + r.series
    if (vod.isNotEmpty()) {
        out += Row_.Header(D.searchVod(vod.size))
        vod.take(12).chunked(6).forEach { out += Row_.Vod(it) }
    }
    return out
}

@Composable
private fun Key(label: String, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.design), bg = Ux.Surface, ringWidth = 4.design, focusedScale = 1f, modifier = modifier.height(64.design)) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = if (small) FontWeight.SemiBold else FontWeight.Bold, fontSize = if (small) 22.spx else 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
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



/**
 * Recherche tactile : champ de saisie du SYSTÈME (clavier Android, pas de clavier à l'écran), résultats par sections.
 * Chaînes en lignes pleines ; films et séries en grille d'affiches à colonnes adaptatives.
 */
@Composable
private fun SearchTouch(
    q: String, r: SearchResults, recent: List<String>, searching: Boolean, voiceAvailable: Boolean, onVoice: () -> Unit, vm: SearchViewModel,
    onOpenChannel: (String, String) -> Unit, onOpenMovie: (Long) -> Unit, onOpenSeries: (Long) -> Unit,
) {
    val S = LocalStrings.current
    val D = LocalDs.current
    val M = com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current
    val widthDp = com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current
    val cols = com.ultratv.tv.nativeapp.ui.mobile.gridColumns(widthDp - 40f - (if (widthDp >= 600f) 88f else 0f), minCellDp = 110f)
    val focus = androidx.compose.ui.focus.FocusRequester()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(Unit) { if (q.isEmpty()) runCatching { focus.requestFocus() } }
    val total = r.channels.size + r.movies.size + r.series.size + r.programs.size
    Column(Modifier.fillMaxSize().background(Ux.Bg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            com.ultratv.tv.nativeapp.ui.mobile.IconCircle(com.ultratv.tv.nativeapp.ui.mobile.MobileIcons.Back, M.a11yBack, Ux.Surface, Ux.Text, com.ultratv.tv.nativeapp.ui.mobile.LocalNavBack.current)
            Text(S.navSearch, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Ux.SurfaceDeep).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DIcon(Icons.Search, 20.dp, Ux.Text3)
            androidx.compose.foundation.text.BasicTextField(
                value = q, onValueChange = { vm.setQuery(it) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ux.Accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = M.a11ySearch },
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (q.isEmpty()) Text(M.searchHint, color = Ux.Muted, fontFamily = Manrope, fontSize = 16.sp, maxLines = 1); inner() } },
            )
            if (q.isNotEmpty()) com.ultratv.tv.nativeapp.ui.mobile.IconCircle(com.ultratv.tv.nativeapp.ui.mobile.MobileIcons.Close, M.clearSearch, Color.Transparent, Ux.Text2, { vm.clear() })
            else if (voiceAvailable) com.ultratv.tv.nativeapp.ui.mobile.IconCircle("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3", D.keyVoice, Color.Transparent, Ux.Text2, onVoice)
        }
        val rows = remember(r, D, cols) { buildTouchRows(r, D, cols) }
        when {
            q.isBlank() -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recent.isNotEmpty()) {
                    Text(S.searchRecent.trimEnd(':', ' ').uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.take(8).forEach { rec ->
                            FocusSurface(onClick = { vm.setQuery(rec) }, shape = RoundedCornerShape(20.dp), bg = Ux.Surface, modifier = Modifier.height(40.dp)) { _ ->
                                Box(Modifier.height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(rec, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                } else Text(D.searchStart, color = Ux.Text3, fontFamily = Manrope, fontSize = 15.sp)
            }
            searching && total == 0 -> Box(Modifier.fillMaxSize())
            total == 0 -> Text(S.searchNoMatches, color = Ux.Text3, fontFamily = Manrope, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp))
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                items(rows.size) { i ->
                    when (val row = rows[i]) {
                        is Row_.Header -> Text(row.text, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = if (i == 0) 0.dp else 10.dp))
                        is Row_.Channels -> row.items.forEach { c -> TouchChannelHit(c) { onOpenChannel(c.streamUrl, c.name) } }
                        is Row_.Program -> TouchProgramHit(row.hit, D) { onOpenChannel(row.hit.channel.streamUrl, row.hit.channel.name) }
                        is Row_.Vod -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.items.forEach { v ->
                                when (v) {
                                    is MovieEntity -> VodCard(v.title, v.poster, Modifier.weight(1f), TmdbKind.MOVIE, v.year) { onOpenMovie(v.id) }
                                    is SeriesEntity -> VodCard(v.title, v.poster, Modifier.weight(1f), TmdbKind.TV, v.year) { onOpenSeries(v.id) }
                                }
                            }
                            repeat(cols - row.items.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private fun buildTouchRows(r: SearchResults, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, cols: Int): List<Row_> {
    val out = mutableListOf<Row_>()
    if (r.channels.isNotEmpty()) { out += Row_.Header(D.searchChannels(r.channels.size)); out += Row_.Channels(r.channels.take(8)) }
    if (r.programs.isNotEmpty()) { out += Row_.Header(D.searchPrograms(r.programs.size)); r.programs.forEach { out += Row_.Program(it) } }
    val vod: List<Any> = r.movies + r.series
    if (vod.isNotEmpty()) { out += Row_.Header(D.searchVod(vod.size)); vod.take(cols * 4).chunked(cols).forEach { out += Row_.Vod(it) } }
    return out
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
private fun ProgramCard(h: ProgramHit, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(96.design)) { f ->
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
                Text(D.live, color = Ux.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
            }
        }
    }
}

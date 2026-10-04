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
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SearchResults
import com.ultratv.tv.nativeapp.i18n.LocalDs
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
    val r by vm.results.collectAsState()
    val recent by vm.recent.collectAsState()
    val searching by vm.searching.collectAsState()
    val S = LocalStrings.current
    val D = LocalDs.current
    val dbgQ by com.ultratv.tv.nativeapp.StartupNav.debugQuery.collectAsState()
    androidx.compose.runtime.LaunchedEffect(dbgQ) { dbgQ?.let { vm.setQuery(it); com.ultratv.tv.nativeapp.StartupNav.debugQuery.value = null } }

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
                Key(D.keySpace, Modifier.weight(1f), small = true, onClick = { vm.append(' ') })
                Key(D.keyDelete, Modifier.weight(1f), small = true, onClick = { vm.backspace() })
                Key(S.searchClear, Modifier.weight(1f), small = true, onClick = { vm.clear() })
            }
            if (recent.isNotEmpty()) {
                Text(S.searchRecent.trimEnd(':', ' ').uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.design), verticalArrangement = Arrangement.spacedBy(10.design), maxLines = 2) {
                    recent.take(8).forEach { rec ->
                        FocusSurface(onClick = { vm.setQuery(rec) }, shape = RoundedCornerShape(22.design), bg = Ux.Surface, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.height(44.design)) { f ->
                            Box(Modifier.height(44.design).padding(horizontal = 20.design), contentAlignment = Alignment.Center) {
                                Text(rec, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.width(1.design).fillMaxHeight().background(Ux.Surface))

        // ===== Droite : résultats =====
        val rows = remember(r, D) { buildRows(r, D) }
        val total = r.channels.size + r.movies.size + r.series.size
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
                        is Row_.Vod -> Row(horizontalArrangement = Arrangement.spacedBy(20.design)) {
                            row.items.forEach { v ->
                                when (v) {
                                    is MovieEntity -> VodCard(v.title, v.poster, Modifier.weight(1f)) { onOpenMovie(v.id) }
                                    is SeriesEntity -> VodCard(v.title, v.poster, Modifier.weight(1f)) { onOpenSeries(v.id) }
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


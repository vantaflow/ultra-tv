package com.ultratv.tv.nativeapp.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.tmdb.PersonRepository
import com.ultratv.tv.nativeapp.data.tmdb.PersonWorks
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.catalog.PosterCell
import com.ultratv.tv.nativeapp.ui.catalog.PosterItem
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val repo: PersonRepository,
) : ViewModel() {
    /** null = en cours de chargement. */
    private val _works = MutableStateFlow<PersonWorks?>(null)
    val works: StateFlow<PersonWorks?> = _works

    fun load(name: String) {
        if (_works.value != null) return
        viewModelScope.launch {
            val ps = providers.observeProviders().first()
            val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id
            _works.value = if (pid == null) PersonWorks(emptyList(), emptyList()) else runCatching { repo.works(pid, name) }.getOrDefault(PersonWorks(emptyList(), emptyList()))
        }
    }
}

/** Page d'un acteur ou d'un réalisateur : ses films et séries présents dans la playlist. */
@Composable
fun PersonScreen(name: String, onOpenMovie: (Long) -> Unit, onOpenSeries: (Long) -> Unit, vm: PersonViewModel = hiltViewModel()) {
    LaunchedEffect(name) { vm.load(name) }
    val D = LocalDs.current
    val works by vm.works.collectAsState()
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val widthDp = com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current
    val cols = if (touch) com.ultratv.tv.nativeapp.ui.mobile.gridColumns(widthDp - 40f) else 7
    Column(
        Modifier.fillMaxSize().then(if (touch) Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp) else Modifier.padding(start = 72.design, end = 96.design, top = 54.design)),
        verticalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 28.design),
    ) {
        Text(name, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 28.sp else 48.spx, maxLines = 1)
        val w = works
        when {
            w == null -> Text(D.personLoading, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 14.sp else 24.spx)
            w.movies.isEmpty() && w.series.isEmpty() -> Text(D.personEmpty, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 14.sp else 24.spx)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 28.design),
                verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 28.design),
                contentPadding = PaddingValues(bottom = if (touch) 16.dp else 54.design),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (w.movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-m") { Header(D.moviesTitle, touch) }
                    items(w.movies, key = { "m-${it.id}" }) { m ->
                        PosterCell(PosterItem(m.id, m.title, m.poster, m.year, m.rating), Modifier, kind = TmdbKind.MOVIE) { onOpenMovie(m.id) }
                    }
                }
                if (w.series.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-s") { Header(D.seriesTitle, touch) }
                    items(w.series, key = { "s-${it.id}" }) { s ->
                        PosterCell(PosterItem(s.id, s.title, s.poster, s.year, s.rating), Modifier, kind = TmdbKind.TV) { onOpenSeries(s.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String, touch: Boolean) =
    Text(text, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 18.sp else 30.spx, modifier = Modifier.padding(top = if (touch) 4.dp else 8.design))

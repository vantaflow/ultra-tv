package com.ultratv.tv.nativeapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val provider: ProviderRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _q = MutableStateFlow("")
    val query: StateFlow<String> = _q.asStateFlow()
    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    private var job: Job? = null

    fun setQuery(s: String) {
        _q.value = s
        job?.cancel()
        job = viewModelScope.launch {
            delay(220)  // debounce
            val pid = provider.firstActive()?.id ?: return@launch
            _results.value = catalog.search(pid, s)
        }
    }
}

@Composable
fun SearchScreen(
    onOpenChannel: (Long) -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val q by vm.query.collectAsState()
    val r by vm.results.collectAsState()

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Search", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
        ) {
            BasicTextField(
                value = q,
                onValueChange = vm::setQuery,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (q.isEmpty()) Text("Type to search channels, movies, series…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                    inner()
                },
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (r.channels.isNotEmpty()) {
                item { SectionTitle("Live channels — ${r.channels.size}") }
                items(r.channels, key = { "c-${it.id}" }) { ChannelRow(it) { onOpenChannel(it.id) } }
            }
            if (r.movies.isNotEmpty()) {
                item { SectionTitle("Movies — ${r.movies.size}") }
                items(r.movies, key = { "m-${it.id}" }) { MovieRow(it) { onOpenMovie(it.id) } }
            }
            if (r.series.isNotEmpty()) {
                item { SectionTitle("Series — ${r.series.size}") }
                items(r.series, key = { "s-${it.id}" }) { SeriesRow(it) { onOpenSeries(it.id) } }
            }
            if (q.isNotBlank() && r.channels.isEmpty() && r.movies.isEmpty() && r.series.isEmpty()) {
                item { Text("No matches.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) =
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable private fun ChannelRow(c: ChannelEntity, onClick: () -> Unit) =
    androidx.tv.material3.Card(onClick = onClick) {
        Text(c.name, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    }

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable private fun MovieRow(m: MovieEntity, onClick: () -> Unit) =
    androidx.tv.material3.Card(onClick = onClick) {
        Text("${m.name}  ${m.year?.let { "· $it" } ?: ""}", modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    }

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable private fun SeriesRow(s: SeriesEntity, onClick: () -> Unit) =
    androidx.tv.material3.Card(onClick = onClick) {
        Text("${s.name}  ${s.year?.let { "· $it" } ?: ""}", modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    }

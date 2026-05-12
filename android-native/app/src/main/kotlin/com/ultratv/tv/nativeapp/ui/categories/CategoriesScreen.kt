package com.ultratv.tv.nativeapp.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
    private val hidden: HiddenCategoriesStore,
) : ViewModel() {

    private val _kind = MutableStateFlow("LIVE")
    val kind: StateFlow<String> = _kind.asStateFlow()

    val hiddenSet: StateFlow<Set<String>> = hidden.hidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val categories: StateFlow<List<CategoryEntity>> =
        combine(providerRepo.observeProviders(), _kind) { ps, k -> ps to k }
            .flatMapLatest { (ps, k) ->
                val pid = ps.firstOrNull()?.id ?: return@flatMapLatest flowOf(emptyList())
                catalog.categories(pid, k)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setKind(k: String) { _kind.value = k }

    fun toggle(cat: CategoryEntity, hide: Boolean) {
        viewModelScope.launch {
            hidden.set(hidden.keyFor(cat.kind, cat.providerId, cat.remoteId), hide)
        }
    }

    fun showAll() = viewModelScope.launch { hidden.clearAll() }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: CategoriesViewModel = hiltViewModel()) {
    val kind by vm.kind.collectAsState()
    val cats by vm.categories.collectAsState()
    val hidden by vm.hiddenSet.collectAsState()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Manage categories", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Hide categories you don't want to see. Hidden categories disappear from the chip filters and from VOD rails.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindButton("LIVE", "Live TV", kind, vm::setKind)
            KindButton("MOVIE", "Movies", kind, vm::setKind)
            KindButton("SERIES", "Series", kind, vm::setKind)
            Button(onClick = { vm.showAll() }, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("Show all again")
            }
        }

        if (cats.isEmpty()) {
            Text("No categories yet — add a provider and re-sync.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(cats, key = { it.id }) { cat ->
                    val key = "${cat.kind}:${cat.providerId}:${cat.remoteId}"
                    val isHidden = key in hidden
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            cat.name + if (cat.locked) " 🔒" else "",
                            fontSize = 16.sp,
                            color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { vm.toggle(cat, !isHidden) },
                            colors = if (isHidden)
                                ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                            else
                                ButtonDefaults.colors(),
                        ) {
                            Text(if (isHidden) "Show" else "Hide")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun KindButton(value: String, label: String, current: String, onSelect: (String) -> Unit) {
    val active = value == current
    Button(
        onClick = { onSelect(value) },
        shape = ButtonDefaults.shape(RoundedCornerShape(18.dp)),
        colors = if (active) ButtonDefaults.colors() else ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Text(label) }
}

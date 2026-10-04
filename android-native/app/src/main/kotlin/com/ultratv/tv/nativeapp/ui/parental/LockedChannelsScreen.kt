package com.ultratv.tv.nativeapp.ui.parental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.categories.FilterField
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class LockedChannelsViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
    private val store: LockedChannelsStore,
) : ViewModel() {
    val query = MutableStateFlow("")

    private val pid = providerRepo.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    val locked: StateFlow<Set<String>> = store.locked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Sans filtre : uniquement les chaînes déjà verrouillées (on n'énumère jamais tout le catalogue).
     * Avec un filtre : recherche plein texte (FTS), limitée à 60 résultats.
     */
    val channels: StateFlow<List<ChannelEntity>> = combine(pid, query.debounce(200), store.locked.map { it.size }.distinctUntilChanged()) { p, q, _ -> p to q }
        .flatMapLatest { (p, q) ->
            flow {
                if (p == null) { emit(emptyList()); return@flow }
                if (q.isBlank()) {
                    val ids = store.locked.first().mapNotNull { it.substringAfter(':', "").takeIf { s -> s.isNotEmpty() && it.substringBefore(':') == p.toString() } }
                    emit(catalog.channelsByRemoteIds(p, ids))
                } else emit(catalog.searchChannels(p, q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lockedCount: StateFlow<Int> = locked.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun toggle(channel: ChannelEntity, on: Boolean) {
        viewModelScope.launch { store.set(channel.providerId, channel.remoteId, on) }
    }
}

/** Chaînes verrouillées (patron Catégories) : filtre, puis une ligne par chaîne avec l'interrupteur « Verrouillée ». */
@Composable
fun LockedChannelsScreen(onBack: () -> Unit = {}, vm: LockedChannelsViewModel = hiltViewModel()) {
    val chans by vm.channels.collectAsState()
    val locked by vm.locked.collectAsState()
    val q by vm.query.collectAsState()
    val count by vm.lockedCount.collectAsState()
    val D = LocalDs.current
    val S = LocalStrings.current

    Column(Modifier.fillMaxSize().background(Ux.Bg).padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design), verticalArrangement = Arrangement.spacedBy(24.design)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
            com.ultratv.tv.nativeapp.ui.series.BackLink(D.settingsTitle, onBack)
            Text(S.lockChannelsTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(D.lockedCountLine(count), color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
        }
        FilterField(q, { vm.query.value = it }, S.lockChannelsFilterHint, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.design)) {
            Text(if (q.isBlank()) D.lockedOnly else D.searchResultsCount(chans.size), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1, modifier = Modifier.weight(1f))
        }
        if (chans.isEmpty()) {
            Text(if (q.isBlank()) D.lockedNone else S.searchNoMatches, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(vertical = 6.design)) {
            items(chans, key = { it.id }) { c ->
                val isOn = "${c.providerId}:${c.remoteId}" in locked
                FocusSurface(onClick = { vm.toggle(c, !isOn) }, shape = RoundedCornerShape(18.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(76.design)) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.design)) {
                        LogoBox(c.logo, c.title, Modifier.width(72.design).height(48.design), radius = 10, pad = 5, bg = if (f) Ux.Surface else Ux.Surface2)
                        Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(D.lockedSwitch, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                        Switch(isOn, inverted = f)
                    }
                }
            }
        }
    }
}

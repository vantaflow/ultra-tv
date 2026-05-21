package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    providerRepo: ProviderRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    val channels: StateFlow<List<ChannelEntity>> = providerRepo.observeProviders()
        .flatMapLatest { ps ->
            val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@flatMapLatest flowOf(emptyList())
            catalog.channels(pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _epgByChannel = MutableStateFlow<Map<Long, List<EpgEntity>>>(emptyMap())
    val epgByChannel: StateFlow<Map<Long, List<EpgEntity>>> = _epgByChannel.asStateFlow()

    fun loadEpgFor(channelId: Long) {
        viewModelScope.launch {
            catalog.refreshShortEpg(channelId)
            catalog.upcomingEpg(channelId).collect { rows ->
                _epgByChannel.value = _epgByChannel.value + (channelId to rows)
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun GuideScreen(vm: GuideViewModel = hiltViewModel()) {
    val channels by vm.channels.collectAsState()
    val epgMap by vm.epgByChannel.collectAsState()
    var expanded by remember { mutableStateOf<Long?>(null) }
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Column(Modifier.padding(start = UltraTokens.EdgeGutter, end = UltraTokens.EdgeGutter, bottom = 16.dp)) {
            Text(
                "GUIDE TÉLÉ",
                color = UltraTokens.Fg3,
                fontSize = 11.sp,
                letterSpacing = 2.3.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                S.tvGuide,
                fontFamily = UltraFonts.Serif,
                fontSize = 56.sp,
                lineHeight = 56.sp,
                letterSpacing = (-1.5).sp,
                color = UltraTokens.Fg,
            )
            Spacer(Modifier.height(10.dp))
            Text(S.guideClickHint, color = UltraTokens.Fg3, fontSize = 13.sp)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = UltraTokens.EdgeGutter),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(channels, key = { it.id }) { c ->
                val rows = epgMap[c.id].orEmpty()
                val isOpen = expanded == c.id
                Card(
                    onClick = {
                        if (isOpen) expanded = null
                        else {
                            expanded = c.id
                            if (rows.isEmpty()) vm.loadEpgFor(c.id)
                        }
                    },
                    shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = CardDefaults.colors(
                        containerColor = if (isOpen) UltraTokens.AccentSoft else UltraTokens.Surface1,
                    ),
                    modifier = Modifier.then(
                        if (isOpen) Modifier.border(1.dp, UltraTokens.Accent, RoundedCornerShape(14.dp))
                        else Modifier.border(1.dp, UltraTokens.Line, RoundedCornerShape(14.dp))
                    ),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            c.name,
                            color = UltraTokens.Fg,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (isOpen) {
                            if (rows.isEmpty()) {
                                Text(S.guideLoadingEpg, color = UltraTokens.Fg3, fontSize = 13.sp)
                            } else rows.take(6).forEachIndexed { idx, e ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Text(
                                        formatTime(e.startMs),
                                        color = if (idx == 0) UltraTokens.Accent else UltraTokens.Fg4,
                                        fontFamily = UltraFonts.Mono,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(56.dp),
                                    )
                                    Text(
                                        e.title,
                                        color = if (idx == 0) UltraTokens.Fg else UltraTokens.Fg2,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatTime(ms: Long): String = fmt.format(java.util.Date(ms))

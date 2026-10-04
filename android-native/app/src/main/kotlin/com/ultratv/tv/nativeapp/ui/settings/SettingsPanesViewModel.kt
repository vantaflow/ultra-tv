package com.ultratv.tv.nativeapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.SyncPart
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Counts(val live: Int = 0, val movies: Int = 0, val series: Int = 0)
data class CategoryTotals(val enabled: Int = 0, val disabled: Int = 0)

/** État des panneaux de Réglages (synchro, affichage, lecture, langues, à propos). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsPanesViewModel @Inject constructor(
    val prefs: UserPreferencesStore,
    providers: ProviderRepository,
    private val providerDao: ProviderDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val sync: SyncCoordinator,
    val adaptive: AdaptiveProfile,
) : ViewModel() {
    val state: StateFlow<UserPrefs> = prefs.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPrefs())
    val provider: StateFlow<ProviderEntity?> = providers.observeProviders().map { ps -> ps.firstOrNull { it.active } ?: ps.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val pid = provider.map { it?.id }.distinctUntilChanged()

    val counts: StateFlow<Counts> = pid.flatMapLatest { id ->
        if (id == null) flowOf(Counts()) else combine(channelDao.observeCount(id), movieDao.observeCount(id), seriesDao.observeCount(id)) { a, b, c -> Counts(a, b, c) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Counts())

    val categoryTotals: StateFlow<CategoryTotals> = pid.flatMapLatest { id ->
        if (id == null) flowOf(CategoryTotals()) else categoryDao.observeEnabledCounts(id).map { rows -> CategoryTotals(rows.filter { it.enabled }.sumOf { it.n }, rows.filter { !it.enabled }.sumOf { it.n }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryTotals())

    val langCounts: StateFlow<List<com.ultratv.tv.nativeapp.data.db.LangCount>> = pid.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else categoryDao.observeLangCounts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun syncNow() { provider.value?.let { sync.request(it.id, force = true) } }

    fun setMode(m: String) = viewModelScope.launch { prefs.setSyncMode(m) }
    fun setHour(h: Int) = viewModelScope.launch { prefs.setSyncHour(h) }
    fun setUnmetered(v: Boolean) = viewModelScope.launch { prefs.setSyncUnmeteredOnly(v) }

    /** Active / désactive un type de contenu. Désactivé + [purge] : les données déjà téléchargées sont supprimées. */
    fun setPart(part: String, on: Boolean, purge: Boolean) {
        viewModelScope.launch {
            prefs.setSyncPart(part, on)
            val id = provider.value?.id ?: return@launch
            if (!on && purge) when (part) {
                "live" -> { channelDao.deleteForProvider(id); providerDao.markSynced(id, SyncPart.LIVE, 0) }
                "vod" -> { movieDao.deleteForProvider(id); providerDao.markSynced(id, SyncPart.VOD, 0) }
                "series" -> { seriesDao.deleteForProvider(id); providerDao.markSynced(id, SyncPart.SERIES, 0) }
            }
            if (on) sync.request(id, force = false)
        }
    }

    fun setPlayerEngine(v: String) = viewModelScope.launch { prefs.setPlayerEngine(v) }
    fun setDecoder(v: String) = viewModelScope.launch { prefs.setDecoderMode(v) }
    fun setBufferPreset(v: String) = viewModelScope.launch { prefs.setBufferPreset(v) }
    fun backToAuto() = viewModelScope.launch { prefs.resetPlaybackToAuto() }
    fun setLanguages(csv: String) = viewModelScope.launch { prefs.setLanguages(csv) }
    fun setPreferredQuality(v: String) = viewModelScope.launch { prefs.setPreferredQuality(v) }
    fun setIncludeMulti(v: Boolean) = viewModelScope.launch { prefs.setIncludeMulti(v) }
    fun setIncludeUnknown(v: Boolean) = viewModelScope.launch { prefs.setIncludeUnknownLang(v) }
}

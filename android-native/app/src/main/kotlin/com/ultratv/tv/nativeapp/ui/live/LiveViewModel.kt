package com.ultratv.tv.nativeapp.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sentinel category remoteId meaning "show every channel". The Tivimate-style
 * UI renders this as a pinned "All channels" entry at the top of the category
 * list.
 */
const val CATEGORY_ALL = "__all__"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val provider: ProviderRepository,
    private val catalog: CatalogRepository,
    private val hiddenStore: HiddenCategoriesStore,
    private val lockedStore: LockedChannelsStore,
    private val playback: PlaybackContext,
    private val epgDaoArg: com.ultratv.tv.nativeapp.data.db.EpgDao,
    private val zapQueue: com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue,
) : ViewModel() {

    val lockedChannels: StateFlow<Set<String>> = lockedStore.locked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // EPG now/next per channel for the current visible list. We re-query every
    // 60s as well as whenever the channel list changes; rangeForChannels with
    // an IN(...) on a few hundred ids is fast (indices on channelId).
    private val _nowNext = MutableStateFlow<Map<Long, Pair<com.ultratv.tv.nativeapp.data.db.EpgEntity?, com.ultratv.tv.nativeapp.data.db.EpgEntity?>>>(emptyMap())
    val nowNext: StateFlow<Map<Long, Pair<com.ultratv.tv.nativeapp.data.db.EpgEntity?, com.ultratv.tv.nativeapp.data.db.EpgEntity?>>> = _nowNext.asStateFlow()

    private val epgDao = epgDaoArg

    // NOTE: the init {} block lives at the *bottom* of the class so the
    // properties it touches (channels, providers) are already constructed
    // by the time it runs. viewModelScope uses Dispatchers.Main.immediate,
    // which dispatches synchronously when the VM is created on the main
    // thread — so anything referencing a not-yet-initialised property in
    // an init block reads `null` and crashes (#LiveViewModel NPE).

    private suspend fun refreshNowNext(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = epgDao.rangeForChannels(ids, now - 30 * 60_000, now + 6 * 60 * 60_000)
        val byCh = rows.groupBy { it.channelId }
        _nowNext.value = ids.associateWith { id ->
            val list = byCh[id].orEmpty()
            val nowProg = list.firstOrNull { it.startMs <= now && it.endMs > now }
            val nextProg = list.firstOrNull { it.startMs > now }
            nowProg to nextProg
        }
    }

    fun toggleLock(channel: ChannelEntity) {
        viewModelScope.launch {
            val key = lockedStore.keyFor(channel.providerId, channel.remoteId)
            val on = key in lockedChannels.value
            lockedStore.set(channel.providerId, channel.remoteId, !on)
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String>(CATEGORY_ALL)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val pid = providers.value.firstOrNull { it.active }?.id
                ?: providers.value.firstOrNull()?.id
                ?: return@launch
            _refreshing.value = true
            try { provider.syncAll(pid) } finally { _refreshing.value = false }
        }
    }

    private val providers = provider.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        combine(providers, hiddenStore.hidden) { ps, hidden -> ps to hidden }
            .flatMapLatest { (ps, hidden) ->
                val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@flatMapLatest flowOf(emptyList())
                catalog.categories(pid, "LIVE").map { list ->
                    list.filter { hiddenStore.keyFor("LIVE", pid, it.remoteId) !in hidden }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Channels for the currently selected category — much smaller per-render
     * set than "all channels", and computed via the SQL `categoryId =`
     * filter so we don't materialise the full list. With CATEGORY_ALL we
     * still apply hidden-category filtering in-flight.
     */
    val channels: StateFlow<List<ChannelEntity>> =
        combine(providers, _selectedCategory, hiddenStore.hidden) { ps, cat, hidden ->
            Triple(ps, cat, hidden)
        }.flatMapLatest { (ps, cat, hidden) ->
            val pid = ps.firstOrNull { it.active }?.id ?: ps.firstOrNull()?.id
            if (pid == null) flowOf(emptyList())
            else {
                val base = if (cat == CATEGORY_ALL) {
                    catalog.channels(pid).map { list ->
                        list.filter { ch ->
                            val cid = ch.categoryId ?: return@filter true
                            hiddenStore.keyFor("LIVE", pid, cid) !in hidden
                        }
                    }
                } else {
                    catalog.channelsForCategory(pid, cat)
                }
                // Reorder so favorited live channels float to the top of every
                // view. We re-use the existing FavoriteEntity table (kind="LIVE").
                combine(base, catalog.favoritesByKind(pid, "LIVE")) { all, favs ->
                    val favIds = favs.map { it.remoteId }.toSet()
                    val (pinned, rest) = all.partition { it.remoteId in favIds }
                    pinned + rest
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun selectCategory(remoteId: String) { _selectedCategory.value = remoteId }

    fun resolveAndPlay(channel: ChannelEntity, onReady: (url: String, title: String) -> Unit) {
        // Seed the zap queue with the list the user was browsing so the
        // player can D-pad UP/DOWN through it without going back.
        zapQueue.set(channels.value, channel)
        fun register(url: String) {
            playback.set(PlaybackContext.Item(
                providerId = channel.providerId,
                kind = "LIVE",
                remoteId = channel.remoteId,
                title = channel.name,
                poster = channel.logo,
                streamUrl = url,
            ))
        }
        if (!channel.streamUrl.startsWith("stalker://")) {
            register(channel.streamUrl)
            onReady(channel.streamUrl, channel.name)
            return
        }
        viewModelScope.launch {
            _resolving.value = true
            try {
                val resolved = provider.resolvePlayUrl(channel.id, channel.streamUrl)
                register(resolved)
                onReady(resolved, channel.name)
            } finally {
                _resolving.value = false
            }
        }
    }

    init {
        // Run AFTER `channels` is initialised. viewModelScope is Main.immediate,
        // so referencing channels in an init block at the top of the class read
        // a null backing field and crashed.
        viewModelScope.launch {
            channels.collect { list ->
                if (list.isEmpty()) { _nowNext.value = emptyMap(); return@collect }
                refreshNowNext(list.map { it.id })
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                refreshNowNext(channels.value.map { it.id })
            }
        }
    }
}

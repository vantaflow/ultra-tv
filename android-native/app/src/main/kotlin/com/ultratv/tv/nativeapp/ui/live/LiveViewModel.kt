package com.ultratv.tv.nativeapp.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.FavoriteDao
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.reminders.RemindersScheduler
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Catégorie « Tout » (toutes les chaînes) et « Favoris » (colonne de gauche du Direct). */
const val CATEGORY_ALL = "__all__"
const val CATEGORY_FAVORITES = "__fav__"

/** Entrée de la colonne de catégories : nom, compteur réel, verrouillage parental. */
data class DirectCategory(val id: String, val name: String?, val count: Int, val locked: Boolean = false, val sections: Int = 0)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val provider: ProviderRepository,
    private val catalog: CatalogRepository,
    private val hiddenStore: HiddenCategoriesStore,
    lockedStore: LockedChannelsStore,
    private val playback: PlaybackContext,
    private val epgDao: EpgDao,
    private val zapQueue: LivePlaybackQueue,
    private val reminders: RemindersScheduler,
    private val channelDao: ChannelDao,
    private val prefs: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
    private val favoriteDao: FavoriteDao,
    private val syncCoordinator: SyncCoordinator,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appCtx: android.content.Context,
) : ViewModel() {

    private val _locked = lockedStore
    val lockedChannels: StateFlow<Set<String>> = lockedStore.locked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val providers = provider.observeProviders().distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val pid: Flow<Long?> = providers.map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    private val landing = appCtx.getSharedPreferences("direct_state", android.content.Context.MODE_PRIVATE)
    /** null = pas encore choisi : on ouvre sur la dernière catégorie utilisée, sinon Favoris, sinon la première non vide. */
    private val _selected = MutableStateFlow<String?>(null)
    fun selectCategory(id: String) { _selected.value = id; if (id != CATEGORY_ALL) landing.edit().putString("last", id).apply() }

    /** Favoris, Tout, puis les catégories NON vides avec leur compteur réel (SQL, servi par l'index). */
    val categories: StateFlow<List<DirectCategory>> = combine(pid, hiddenStore.hidden) { id, hidden -> id to hidden }
        .flatMapLatest { (id, hidden) ->
            if (id == null) flowOf(emptyList())
            else combine(
                catalog.categories(id, "LIVE"),
                channelDao.observeCategoryCounts(id),
                favoriteDao.observeCount(id, "LIVE"),
            ) { cats: List<CategoryEntity>, counts, favCount ->
                val byId = counts.associate { it.categoryId to it.n }
                val secById = counts.associate { it.categoryId to it.sections }
                val visible = cats.filter { hiddenStore.keyFor("LIVE", id, it.remoteId) !in hidden }
                    .mapNotNull { c -> byId[c.remoteId]?.takeIf { it > 0 }?.let { DirectCategory(c.remoteId, c.name, it, c.locked, secById[c.remoteId] ?: 0) } }
                val total = visible.sumOf { it.count }
                listOf(DirectCategory(CATEGORY_FAVORITES, null, favCount), DirectCategory(CATEGORY_ALL, null, total, sections = visible.sumOf { it.sections })) + visible
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedCategory: StateFlow<String> = combine(_selected, categories) { sel, cats -> sel ?: pickLanding(cats, landing.getString("last", null)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CATEGORY_ALL)

    /** Filtre de langue TEMPORAIRE de la vue (oublié à la fermeture de l'écran) + langues présentes avec leurs effectifs. */
    private val _langView = MutableStateFlow(com.ultratv.tv.nativeapp.data.repo.LangView.ALL)
    val langView: StateFlow<com.ultratv.tv.nativeapp.data.repo.LangView> = _langView
    fun toggleLang(code: String) { _langView.value = _langView.value.toggle(code) }
    fun clearLangView() { _langView.value = com.ultratv.tv.nativeapp.data.repo.LangView.ALL }
    val langCounts: StateFlow<List<com.ultratv.tv.nativeapp.data.db.LangCount>> = pid.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else channelDao.observeLangCounts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Chaînes de la catégorie choisie, paginées (Paging 3 sur Room : seules les lignes visibles sont chargées). */
    val channels: Flow<PagingData<ChannelEntity>> = combine(pid, selectedCategory.debounce(120), hiddenStore.hidden, _langView) { id, cat, hidden, lv -> arrayOf(id, cat, hidden, lv) }
        .distinctUntilChanged()
        .flatMapLatest { arr ->
            @Suppress("UNCHECKED_CAST") val id = arr[0] as Long?; val cat = arr[1] as String; val hidden = arr[2] as Set<String>; val lv = arr[3] as com.ultratv.tv.nativeapp.data.repo.LangView
            if (id == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 40, prefetchDistance = 24, initialLoadSize = 60, enablePlaceholders = false)) {
                when {
                    cat == CATEGORY_FAVORITES -> channelDao.pagedFavorites(id, lv.useLang, lv.langs)
                    cat == CATEGORY_ALL -> {
                        val hiddenIds = hidden.filter { it.startsWith("LIVE:$id:") }.map { it.substringAfterLast(':') }
                        if (hiddenIds.isEmpty()) channelDao.pagedAll(id, lv.useLang, lv.langs) else channelDao.pagedAllExcluding(id, hiddenIds, lv.useLang, lv.langs)
                    }
                    else -> channelDao.pagedForCategory(id, cat, lv.useLang, lv.langs)
                }
            }.flow
        }
        .cachedIn(viewModelScope)

    // ── Programme en cours / suivant, chargé pour les lignes VISIBLES seulement ──
    private val _nowNext = MutableStateFlow<Map<Long, Pair<EpgEntity?, EpgEntity?>>>(emptyMap())
    val nowNext: StateFlow<Map<Long, Pair<EpgEntity?, EpgEntity?>>> = _nowNext.asStateFlow()
    private val visibleIds = MutableStateFlow<List<Long>>(emptyList())
    fun setVisible(ids: List<Long>) { visibleIds.value = ids }

    private suspend fun loadNowNext(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = ids.chunked(500).flatMap { epgDao.rangeForChannels(it, now - 30 * 60_000, now + 6 * 60 * 60_000) }.groupBy { it.channelId }
        val add = ids.associateWith { id ->
            val l = rows[id].orEmpty()
            l.firstOrNull { it.startMs <= now && it.endMs > now } to l.firstOrNull { it.startMs > now }
        }
        _nowNext.value = (_nowNext.value + add).let { m -> if (m.size > 600) add else m }
    }

    init {
        viewModelScope.launch { visibleIds.debounce(200).collect { loadNowNext(it) } }
        viewModelScope.launch { while (true) { delay(60_000); loadNowNext(visibleIds.value) } }
    }

    fun toggleLock(channel: ChannelEntity) {
        viewModelScope.launch {
            val on = _locked.keyFor(channel.providerId, channel.remoteId) in lockedChannels.value
            _locked.set(channel.providerId, channel.remoteId, !on)
        }
    }

    val favoriteIds: StateFlow<Set<String>> = pid.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.favoritesByKind(id, "LIVE") }
        .map { l -> l.map { it.remoteId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch { catalog.setFavorite(channel.providerId, "LIVE", channel.remoteId, channel.remoteId !in favoriteIds.value) }
    }

    fun addReminder(channel: ChannelEntity, prog: EpgEntity) {
        viewModelScope.launch {
            reminders.add(
                com.ultratv.tv.nativeapp.data.reminders.ReminderEntity(
                    providerId = channel.providerId, channelRemoteId = channel.remoteId, channelName = channel.name,
                    programmeTitle = prog.title, startMs = prog.startMs, endMs = prog.endMs,
                ),
            )
        }
    }

    /** Guide complet d'une chaîne (aujourd'hui + demain), pour le rattrapage et les rappels. */
    suspend fun loadDaySchedule(channelId: Long): List<EpgEntity> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return epgDao.forChannelInRange(channelId, cal.timeInMillis, cal.timeInMillis + 48 * 3_600_000L)
    }

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    /**
     * Ouvre le lecteur plein écran. File de zapping = fenêtre de ±200 chaînes autour de la chaîne
     * choisie dans la catégorie courante (jamais les 55 000 en mémoire).
     */
    /** Autres qualités de la même chaîne (même nom et même langue) ; vide s'il n'y en a pas. */
    suspend fun variantsOf(c: ChannelEntity): List<ChannelEntity> =
        channelDao.variantsOf(c.providerId, c.title, c.lang).takeIf { v -> v.size > 1 && v.any { it.id == c.id } }.orEmpty()

    /** Variante à lancer selon la qualité préférée (réglage) ; la chaîne choisie telle quelle si rien ne correspond. */
    private suspend fun preferredVariant(c: ChannelEntity): ChannelEntity {
        val pref = prefs.flow.first().preferredQuality
        if (pref == "auto") return c
        return pickVariant(c, variantsOf(c), pref)
    }

    fun resolveAndPlay(channel0: ChannelEntity, exact: Boolean = false, onReady: (url: String, title: String) -> Unit) {
        viewModelScope.launch {
            _resolving.value = true
            try {
                val channel = if (exact) channel0 else preferredVariant(channel0)
                val cat = selectedCategory.value
                val window = when (cat) {
                    CATEGORY_FAVORITES -> channelDao.favoritesList(channel.providerId)
                    CATEGORY_ALL -> {
                        val rank = channelDao.rankAll(channel.providerId, channel.num, channel.sortKey)
                        channelDao.windowAll(channel.providerId, 401, (rank - 200).coerceAtLeast(0))
                    }
                    else -> {
                        val rank = channelDao.rankCategory(channel.providerId, cat, channel.num, channel.sortKey)
                        channelDao.windowCategory(channel.providerId, cat, 401, (rank - 200).coerceAtLeast(0))
                    }
                }
                zapQueue.set(_langView.value.filter(window) { it.lang }.map { if (it.id == channel0.id) channel else it }.ifEmpty { listOf(channel) }, channel)
                val url = if (channel.streamUrl.startsWith("stalker://")) provider.resolvePlayUrl(channel.id, channel.streamUrl) else channel.streamUrl
                playback.set(PlaybackContext.Item(channel.providerId, "LIVE", channel.remoteId, channel.title, channel.logo, url))
                onReady(url, channel.title)
            } finally { _resolving.value = false }
        }
    }

    fun refresh() {
        val id = providers.value.firstOrNull { it.active }?.id ?: providers.value.firstOrNull()?.id ?: return
        syncCoordinator.request(id, force = true)
    }
}

/** Catégorie d'ouverture du Direct : dernière utilisée (si elle existe encore), sinon Favoris (s'il y en a), sinon la première non vide. */
fun pickLanding(cats: List<DirectCategory>, last: String?): String {
    if (cats.isEmpty()) return CATEGORY_ALL
    last?.let { l -> if (cats.any { it.id == l && it.count > 0 }) return l }
    cats.firstOrNull { it.id == CATEGORY_FAVORITES && it.count > 0 }?.let { return it.id }
    return cats.firstOrNull { it.id != CATEGORY_FAVORITES && it.id != CATEGORY_ALL && it.count > 0 }?.id ?: CATEGORY_ALL
}

/** Qualité demandée → variante : exacte, sinon la meilleure en dessous, sinon la plus basse au-dessus ; qualité inconnue en dernier recours. */
fun pickVariant(current: ChannelEntity, variants: List<ChannelEntity>, pref: String): ChannelEntity {
    val want = when (pref) { "4k" -> 4; "fhd" -> 3; "hd" -> 2; "sd" -> 1; else -> return current }
    val known = variants.filter { it.quality > 0 }
    if (known.isEmpty()) return current
    return known.firstOrNull { it.quality == want }
        ?: known.filter { it.quality < want }.maxByOrNull { it.quality }
        ?: known.filter { it.quality > want }.minByOrNull { it.quality }
        ?: current
}

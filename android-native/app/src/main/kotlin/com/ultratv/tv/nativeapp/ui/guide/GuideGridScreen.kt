package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.ui.design.designT
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ultratv.tv.nativeapp.data.replay.ReplayAvailability
import com.ultratv.tv.nativeapp.data.replay.ReplayUrls
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.collectLatest
import com.ultratv.tv.nativeapp.i18n.nowButton
import com.ultratv.tv.nativeapp.i18n.nowBadge
import com.ultratv.tv.nativeapp.i18n.minutesRemaining
import com.ultratv.tv.nativeapp.i18n.helpPrograms
import com.ultratv.tv.nativeapp.i18n.helpChannels
import com.ultratv.tv.nativeapp.i18n.helpWatch
import com.ultratv.tv.nativeapp.i18n.helpOneDay
import com.ultratv.tv.nativeapp.i18n.helpBackKey
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.reminders.RemindersScheduler
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.recording.ConnectionPolicy
import com.ultratv.tv.nativeapp.data.recording.ConnectionWarning
import com.ultratv.tv.nativeapp.data.recording.ScheduleResult
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.recConnectionBusy
import com.ultratv.tv.nativeapp.i18n.categoryLabel
import com.ultratv.tv.nativeapp.i18n.sportRemind
import com.ultratv.tv.nativeapp.i18n.guideGroupEmpty
import com.ultratv.tv.nativeapp.i18n.guideGroupPill
import com.ultratv.tv.nativeapp.i18n.guideGroupTitle
import com.ultratv.tv.nativeapp.i18n.recNoSpace
import com.ultratv.tv.nativeapp.i18n.recNothing
import com.ultratv.tv.nativeapp.i18n.recScheduled
import com.ultratv.tv.nativeapp.i18n.remindSet
import com.ultratv.tv.nativeapp.i18n.replayTag
import com.ultratv.tv.nativeapp.i18n.yesterday
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.common.ChannelBigMark
import com.ultratv.tv.nativeapp.ui.common.LocalChannelDisplay
import com.ultratv.tv.nativeapp.ui.common.channelNumber
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.ultratv.tv.nativeapp.data.repo.atMostEvery
import com.ultratv.tv.nativeapp.data.repo.snapshotWhile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ultratv.tv.nativeapp.i18n.locale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Fenêtre de la grille : 3 h en 6 créneaux de 30 min (maquette Guide.dc.html). */
const val GUIDE_WINDOW_MS = 3 * 3_600_000L
private const val SLOT_MS = 30 * 60_000L

/** Début de fenêtre pour le jour [dayOffset] : aujourd'hui = demi-heure courante, ensuite 20:00. */
fun guideWindowStart(nowMs: Long, dayOffset: Int): Long {
    if (dayOffset == 0) return nowMs / SLOT_MS * SLOT_MS
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMs; add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/**
 * Certains guides contiennent des programmes qui se chevauchent ou se répètent : on garde l'ordre
 * chronologique, on rogne le début des suivants et on écarte ceux entièrement recouverts.
 */
fun normalizeRow(items: List<EpgEntity>): List<EpgEntity> {
    val out = ArrayList<EpgEntity>(items.size)
    var cursor = Long.MIN_VALUE
    for (p in items.sortedWith(compareBy({ it.startMs }, { it.endMs }))) {
        if (p.endMs <= cursor || p.endMs <= p.startMs) continue
        out += if (p.startMs < cursor) p.copy(startMs = cursor) else p
        cursor = p.endMs
    }
    return out
}

/** Placement d'un programme dans la grille : fractions [0..1] de la fenêtre, tronquées aux bords. */
data class Slot(val startFrac: Float, val widthFrac: Float)

fun slotFor(startMs: Long, endMs: Long, windowStart: Long, windowMs: Long = GUIDE_WINDOW_MS): Slot? {
    val s = maxOf(startMs, windowStart)
    val e = minOf(endMs, windowStart + windowMs)
    if (e <= s) return null
    return Slot((s - windowStart).toFloat() / windowMs, (e - s).toFloat() / windowMs)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class GuideGridViewModel @Inject constructor(
    private val providerRepo: ProviderRepository,
    syncBus: com.ultratv.tv.nativeapp.data.repo.SyncStatusBus,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val reminders: RemindersScheduler,
    private val recScheduler: com.ultratv.tv.nativeapp.data.recording.RecordingScheduler,
    recDao: com.ultratv.tv.nativeapp.data.db.RecordingDao,
    private val replay: com.ultratv.tv.nativeapp.data.replay.ReplayService,
    private val limits: com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore,
    lockedStore: com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore,
    private val catalog: com.ultratv.tv.nativeapp.data.repo.CatalogRepository,
    private val hiddenStore: com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appCtx: android.content.Context,
    private val zapQueue: com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue,
    private val playback: com.ultratv.tv.nativeapp.data.repo.PlaybackContext,
) : ViewModel() {
    /**
     * Prépare la lecture en DIRECT comme l'écran Direct : contexte LIVE (fiche Info, bandeau, pause en différé) et file de
     * zapping du groupe affiché dans le guide (Favoris, catégorie, ou catégorie de la chaîne pour « Tout »).
     */
    fun playLive(ch: ChannelEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val g = selectedGroup.value
                val cat = if (g == com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL) ch.categoryId else g
                val window = when {
                    g == com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES -> channelDao.favoritesList(ch.providerId, profiles.currentIdNow)
                    cat.isNullOrEmpty() -> listOf(ch)
                    else -> {
                        val rank = channelDao.rankCategory(ch.providerId, cat, ch.num, ch.id)
                        channelDao.windowCategory(ch.providerId, cat, 401, (rank - 200).coerceAtLeast(0))
                    }
                }
                zapQueue.set(window.ifEmpty { listOf(ch) }, ch)
                playback.set(com.ultratv.tv.nativeapp.data.repo.PlaybackContext.Item(ch.providerId, "LIVE", ch.remoteId, ch.title, ch.logo, ch.streamUrl))
            }
            onReady()
        }
    }

    /** Chaînes verrouillées (code parental) : jamais d'aperçu vidéo pour elles. */
    val lockedChannels: StateFlow<Set<String>> = lockedStore.locked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Adresse lisible du flux pour l'aperçu (portail Stalker : lien demandé au portail). */
    suspend fun previewUrl(c: ChannelEntity): String = c.streamUrl   // stalker:// : résolu par la session à chaque ouverture

    fun maxConnections(providerId: Long) = limits.maxConnections(providerId)
    fun replayUrl(channel: ChannelEntity, prog: EpgEntity): String? = replay.urlFor(channel, prog)
    fun armReplay(channel: ChannelEntity, prog: EpgEntity) = replay.armFromGuide(prog, channel.providerId)

    /** Un enregistrement en cours occupe la (seule) connexion : le dialogue et « Regarder » le signalent. */
    val recordingRunning: StateFlow<Boolean> = recDao.observeRunningCount().map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun record(channel: ChannelEntity, prog: EpgEntity, wholeSeries: Boolean, onResult: (com.ultratv.tv.nativeapp.data.recording.ScheduleResult) -> Unit) {
        viewModelScope.launch { onResult(recScheduler.schedule(channel, prog, wholeSeries)) }
    }

    /** Chargement / « Guide indisponible » : jamais un chargement sans fin (l'échec arrive avec sa raison). */
    val guideLoad: StateFlow<GuideLoad> = combine(syncBus.epg, syncBus.status) { e, st -> guideLoadState(e, st != null) }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideLoad.Idle)

    /** « Réessayer » : relance SEULEMENT le guide de la source active. */
    fun retryGuide() { viewModelScope.launch { pid.first()?.let { providerRepo.retryEpg(it) } } }

    private val pid = providerRepo.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    private val _windowStart = MutableStateFlow(guideWindowStart(System.currentTimeMillis(), 0))
    val windowStart: StateFlow<Long> = _windowStart.asStateFlow()
    fun setWindowStart(ms: Long) { _windowStart.value = ms }

    // ── Groupe de chaînes (mêmes groupes que la colonne de gauche du Direct) ──
    private val syncing = syncBus.status.map { it != null }.distinctUntilChanged()

    /** Favoris, Tout, catégories non vides non masquées : même liste, mêmes noms, même ordre que le Direct. Compteurs relus au plus 1 fois par seconde, figés pendant une synchro. */
    val groups: StateFlow<List<com.ultratv.tv.nativeapp.ui.live.DirectCategory>> =
        combine(pid, hiddenStore.hidden) { id, hidden -> id to hidden }
            .flatMapLatest { (id, hidden) ->
                if (id == null) flowOf(emptyList())
                else combine(
                    catalog.categories(id, "LIVE"),
                    channelDao.observeCategoryCounts(id).atMostEvery(1_000).snapshotWhile(syncing),
                    catalog.favoriteCount(id, "LIVE"),
                ) { cats, counts, fav -> com.ultratv.tv.nativeapp.ui.live.buildDirectCategories(cats, counts, fav, id, hidden) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Dernier groupe mémorisé pour le profil courant (null = jamais choisi). */
    private val savedGroup: Flow<String?> = profiles.currentPrefs.map { it.guideGroup }.distinctUntilChanged()
    /** Groupe ouvert dans le Direct (même mémoire que son écran) : défaut tant que rien n'est choisi dans le Guide. */
    private val directLast: String? get() = appCtx.getSharedPreferences("direct_state", android.content.Context.MODE_PRIVATE).getString("last", null)

    /** Groupe affiché ; Tout tant que les groupes ne sont pas chargés. */
    val selectedGroup: StateFlow<String> = combine(savedGroup, groups) { saved, g ->
        if (g.isEmpty()) saved ?: directLast ?: com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
        else resolveGuideGroup(saved, directLast, g)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL)

    /** Mémorise le choix pour le profil courant (rechargement de la grille via [selectedGroup]). */
    fun selectGroup(id: String) {
        viewModelScope.launch { profiles.putPref(com.ultratv.tv.nativeapp.data.profile.ProfilePrefs.GUIDE_GROUP, id) }
    }

    /**
     * Chaînes du groupe ayant un guide dans la fenêtre (pas 55 000 lignes vides) : le groupe est filtré par Room
     * (requêtes paginées), jamais en mémoire.
     */
    val channels: kotlinx.coroutines.flow.Flow<PagingData<ChannelEntity>> =
        combine(pid, _windowStart, selectedGroup, hiddenStore.hidden, profiles.currentId) { id, w, g, hidden, prof -> arrayOf(id, w, g, hidden, prof) }
            .distinctUntilChanged { a, b -> a.contentEquals(b) }
            .flatMapLatest { a ->
                @Suppress("UNCHECKED_CAST") val id = a[0] as Long?; val w = a[1] as Long; val g = a[2] as String; val hidden = a[3] as Set<String>; val prof = a[4] as Long
                if (id == null) flowOf(PagingData.empty())
                else Pager(PagingConfig(pageSize = 20, prefetchDistance = 10, initialLoadSize = 30, enablePlaceholders = false)) {
                    when (g) {
                        com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES -> channelDao.pagedWithEpgFavorites(id, w, w + GUIDE_WINDOW_MS, prof)
                        com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL -> {
                            val hiddenIds = hidden.filter { it.startsWith("LIVE:$id:") }.map { it.substringAfterLast(':') }
                            channelDao.pagedWithEpgAll(id, w, w + GUIDE_WINDOW_MS, if (hiddenIds.isEmpty()) 0 else 1, hiddenIds)
                        }
                        else -> channelDao.pagedWithEpgForCategory(id, g, w, w + GUIDE_WINDOW_MS)
                    }
                }.flow
            }.cachedIn(viewModelScope)

    private val _programmes = MutableStateFlow<Map<Long, List<EpgEntity>>>(emptyMap())
    val programmes: StateFlow<Map<Long, List<EpgEntity>>> = _programmes.asStateFlow()
    private val visible = MutableStateFlow<List<Long>>(emptyList())
    fun setVisible(ids: List<Long>) { visible.value = ids }

    init {
        viewModelScope.launch {
            combine(visible.debounce(150), _windowStart) { v, w -> v to w }.collect { (ids, w) ->
                if (ids.isEmpty()) return@collect
                val rows = ids.chunked(500).flatMap { epgDao.rangeForChannels(it, w, w + GUIDE_WINDOW_MS) }
                _programmes.value = _programmes.value + rows.groupBy { it.channelId }.let { m -> ids.associateWith { normalizeRow(m[it].orEmpty()) } }
            }
        }
        // Nouvelle fenêtre : on repart d'un cache vide (les lignes visibles sont rechargées).
        viewModelScope.launch { _windowStart.collect { _programmes.value = emptyMap() } }
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
}

/** Guide TV (maquette Guide.dc.html) : colonne chaînes de 260, 6 créneaux de 30 min, ligne accent = maintenant. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun GuideGridScreen(onPlayChannel: (ChannelEntity) -> Unit, onPlayUrl: (url: String, title: String) -> Unit = { _, _ -> }, vm: GuideGridViewModel = hiltViewModel()) {
    // Android 13+ : la permission de notification est demandée au PREMIER rappel (pas au lancement de l'app).
    val notifCtx = androidx.compose.ui.platform.LocalContext.current
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val askNotifications = {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(notifCtx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) runCatching { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    val D = LocalDs.current
    val channels = vm.channels.collectAsLazyPagingItems()
    val programmes by vm.programmes.collectAsState()
    val windowStart by vm.windowStart.collectAsState()
    var day by remember { mutableStateOf(0) }
    // Horloge UNIQUE (30 s) : lue seulement au dessin (barres, ligne de l'heure) ou dans de petits textes dédiés.
    val clock = com.ultratv.tv.nativeapp.ui.common.rememberEpgClock()
    val shared = com.ultratv.tv.nativeapp.ui.player.engine.LocalSharedLive.current
    var actionTarget by remember { mutableStateOf<Pair<ChannelEntity, EpgEntity>?>(null) }
    val recordingRunning by vm.recordingRunning.collectAsState()
    val guideLoad by vm.guideLoad.collectAsState()
    val groups by vm.groups.collectAsState()
    val group by vm.selectedGroup.collectAsState()
    var groupOpen by remember { mutableStateOf(false) }
    val groupName = { g: com.ultratv.tv.nativeapp.ui.live.DirectCategory ->
        when (g.id) { com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES -> D.catFavorites; com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL -> D.catAll; else -> com.ultratv.tv.nativeapp.ui.common.prettyCategoryName(g.name.orEmpty()) }
    }
    val groupLabel = guideGroupName(groups, group, groupName)
    val nameColPx = LocalChannelDisplay.current.guideNameWidth.tvPx

    // Lecture et enregistrement hissés : partagés par le dialogue du programme et par les boutons du panneau du haut.
    val watchLive: (ChannelEntity) -> Unit = { ch ->
        shared?.stopPreview()   // connexion unique : l'aperçu se ferme avant que le lecteur n'ouvre le flux
        // Lecture et enregistrement se partagent la connexion : on prévient avant de la prendre.
        if (ConnectionPolicy.onPlayRequested(recordingRunning, vm.maxConnections(ch.providerId)) != ConnectionWarning.NONE) Toaster.show(D.recConnectionBusy)
        vm.playLive(ch) { onPlayChannel(ch) }
    }
    val recordProgramme: (ChannelEntity, EpgEntity, Boolean) -> Unit = { ch, pr, series ->
        vm.record(ch, pr, series) { r ->
            when (r) {
                is ScheduleResult.Scheduled -> Toaster.ok(D.recScheduled(r.count))
                ScheduleResult.NoSpace -> Toaster.err(D.recNoSpace)
                ScheduleResult.NothingToSchedule -> Toaster.show(D.recNothing)
            }
        }
    }

    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) GuideTouch(
        channels = channels, programmes = programmes, windowStart = windowStart, day = day, D = D, vm = vm, guideLoad = guideLoad,
        onDay = { d -> day = d; vm.setWindowStart(guideWindowStart(System.currentTimeMillis(), d)) },
        onShift = { dir -> vm.setWindowStart(windowStart + dir * 3 * SLOT_MS) },
        onSelect = { c, p -> actionTarget = c to p },
        onRemind = { c, p -> vm.addReminder(c, p); askNotifications(); Toaster.ok(D.remindSet) },
        groupLabel = groupLabel, onGroup = { groupOpen = true },
    ) else GuideTvContent(
        channelCount = channels.itemCount, channelAt = { channels[it] }, peekAt = { channels.peek(it) },
        itemKey = com.ultratv.tv.nativeapp.ui.common.rememberSafeItemKey(channels) { it.id },
        programmes = programmes, windowStart = windowStart, day = day, D = D, clock = clock,
        groups = groups, group = group, guideLoad = guideLoad, nameColPx = nameColPx, groupName = groupName,
        onDay = { d -> day = d; vm.setWindowStart(guideWindowStart(System.currentTimeMillis(), d)) },
        onWindowStart = { ms -> vm.setWindowStart(ms) },
        onGroup = { id -> vm.selectGroup(id) },
        onVisible = { ids -> vm.setVisible(ids) },
        onRetry = { vm.retryGuide() },
        onSelect = { c, p -> actionTarget = c to p },
        onWatch = { ch -> watchLive(ch) },
        onRemind = { c, p -> vm.addReminder(c, p); askNotifications(); Toaster.ok(D.remindSet) },
        onRecord = { c, p -> recordProgramme(c, p, false) },
        preview = { sel, m -> GuidePreviewPane(sel, vm, m) },
    )
    if (groupOpen) com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(
        D.guideGroupTitle, guideGroupOptions(groups, groupName), group,
        onPick = { id -> groupOpen = false; if (id != group) vm.selectGroup(id) }, onDismiss = { groupOpen = false },
    )
    actionTarget?.let { (ch, pr) ->
        val nowMs = System.currentTimeMillis()
        val replayUrl = remember(ch.id, pr.id) { vm.replayUrl(ch, pr) }
        ProgramActionsDialog(
            channel = ch, prog = pr, state = programActionState(pr, replayUrl, nowMs), D = D, recordingRunning = recordingRunning,
            onReplay = { actionTarget = null; shared?.stopPreview(); replayUrl?.let { vm.armReplay(ch, pr); onPlayUrl(it, pr.title) } },
            onWatch = { actionTarget = null; watchLive(ch) },
            onRemind = { actionTarget = null; vm.addReminder(ch, pr); askNotifications(); Toaster.ok(D.remindSet) },
            onRecord = { series -> actionTarget = null; recordProgramme(ch, pr, series) },
            onDismiss = { actionTarget = null },
        )
    }
}

/**
 * Panneau du programme sélectionné (maquette) : intitulé « MAINTENANT · CHAÎNE · horaire », titre, progression, description,
 * puis « Regarder » / « Me rappeler » / « Enregistrer » (les deux derniers seulement quand le programme le permet,
 * comme dans le dialogue d'actions).
 */
@Composable
private fun ProgramInfo(
    sel: androidx.compose.runtime.State<Pair<ChannelEntity, EpgEntity>?>, clock: androidx.compose.runtime.State<Long>,
    D: com.ultratv.tv.nativeapp.i18n.DesignStrings, progressLine: String?, modifier: Modifier,
    onWatch: (ChannelEntity) -> Unit, onRemind: (ChannelEntity, EpgEntity) -> Unit, onRecord: (ChannelEntity, EpgEntity) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.design, Alignment.CenterVertically)) {
        val s = sel.value
        if (s == null) { Text(D.noProgramInfo, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2); return@Column }
        val (ch, p) = s
        val nowMs = clock.value
        val isNow = p.startMs <= nowMs && p.endMs > nowMs
        val head = (if (isNow) "${D.nowBadge} · " else "") + "${ch.title} · ${EpgClock.range(p.startMs, p.endMs)}"
        Text(
            head.uppercase(), color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.spx, letterSpacing = 0.75.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
        )
        Text(p.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, lineHeight = 58.spx, letterSpacing = (-1).spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isNow) Row(Modifier.widthIn(max = 900.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
            Box(Modifier.weight(1f)) { com.ultratv.tv.nativeapp.ui.common.EpgProgressBar(p.startMs, p.endMs, clock, heightDp = 4f) }
            Text(D.minutesRemaining(com.ultratv.tv.nativeapp.ui.common.EpgProgress.remainingMinutes(p.endMs, nowMs)), color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, softWrap = false)
        }
        p.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 32.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 900.design)) }
        val acts = programActionState(p, null, nowMs)
        Row(Modifier.padding(top = 6.design), horizontalArrangement = Arrangement.spacedBy(12.design)) {
            com.ultratv.tv.nativeapp.ui.design.PillButton(D.watch, onClick = { onWatch(ch) }, heightPx = 56, hPadPx = 26, fontPx = 19, weight = FontWeight.Bold, bg = Ux.Accent)
            if (acts.remindEnabled) com.ultratv.tv.nativeapp.ui.design.PillButton(D.sportRemind, onClick = { onRemind(ch, p) }, heightPx = 56, hPadPx = 26, fontPx = 19)
            if (acts.recordEnabled) com.ultratv.tv.nativeapp.ui.design.PillButton(D.pRecord, onClick = { onRecord(ch, p) }, heightPx = 56, hPadPx = 26, fontPx = 19)
        }
        // Des programmes sont déjà là mais le guide continue d'arriver : progression visible, jamais muette.
        if (progressLine != null) Text(progressLine, color = Ux.AccentInk, fontFamily = Manrope, fontSize = 16.spx, maxLines = 1)
    }
}

/** Pastille « maintenant » au-dessus de la ligne d'accent : l'heure courante, seulement si elle est dans la fenêtre. */
@Composable
private fun NowPill(clock: androidx.compose.runtime.State<Long>, windowStart: Long, nameCol: Dp, gridW: Dp) {
    val frac = com.ultratv.tv.nativeapp.ui.common.EpgProgress.nowLineFraction(clock.value, windowStart, GUIDE_WINDOW_MS) ?: return
    Text(
        EpgClock.wall(clock.value), color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.spx, maxLines = 1, softWrap = false,
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset((nameCol + gridW * frac - 30.design).roundToPx(), -(66.design).roundToPx()) }
            .clip(RoundedCornerShape(50)).background(Ux.Accent).padding(horizontal = 10.design, vertical = 4.design),
    )
}

/**
 * Aperçu de la chaîne du programme sélectionné : le moteur d'aperçu du Direct (session partagée, connexion unique,
 * même réglage « Aperçu vidéo dans le Direct » et même délai). Sans aperçu actif : logo de la chaîne.
 */
@OptIn(FlowPreview::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun GuidePreviewPane(sel: androidx.compose.runtime.State<Pair<ChannelEntity, EpgEntity>?>, vm: GuideGridViewModel, modifier: Modifier) {
    val shared = com.ultratv.tv.nativeapp.ui.player.engine.LocalSharedLive.current
    val pvm: com.ultratv.tv.nativeapp.ui.live.LivePreviewViewModel = hiltViewModel()
    val pv by pvm.previewPrefs.state.collectAsState()
    val userPrefs by pvm.userPrefs.collectAsState()
    val locked by vm.lockedChannels.collectAsState()
    val touchUi = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val videoPreview = shared != null && com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.enabled(pv.mode, pvm.lowRam, touchUi)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val textureView = com.ultratv.tv.nativeapp.ui.player.engine.useTextureView(userPrefs?.videoSurface ?: "auto", touchUi)
    val lockedNow by androidx.compose.runtime.rememberUpdatedState(locked)
    val mutedNow by androidx.compose.runtime.rememberUpdatedState(!pv.sound)
    val channel = sel.value?.first
    if (videoPreview && shared != null) {
        LaunchedEffect(shared) { shared.env = pvm.env() }
        LaunchedEffect(pv.sound) { shared.applyPreviewMuted(!pv.sound) }
        LaunchedEffect(shared, pv.delayMs) {
            // Chaîne du programme focalisé, lue après le délai du réglage ; collectLatest : jamais deux flux.
            snapshotFlow { sel.value?.first }.distinctUntilChanged().debounce(com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.guideDelayMs(pv.delayMs)).collectLatest { c ->
                if (c == null) return@collectLatest
                val row = com.ultratv.tv.nativeapp.ui.live.channelKey(c)
                when (com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.decide(shared.owner, shared.rowKey, row, row in lockedNow, false)) {
                    com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.Action.KEEP -> Unit
                    com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.Action.STOP -> shared.stopPreview()
                    com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.Action.START -> {
                        val url = vm.previewUrl(c)
                        if (sel.value?.first?.id != c.id) return@collectLatest
                        shared.preview(row, row, url, mutedNow) { pvm.createSession(ctx, shared.scope, shared, textureView, userPrefs?.videoSurface ?: "auto") }
                    }
                }
            }
        }
    }
    // Écran quitté ou appli en arrière-plan : l'aperçu ferme son flux.
    DisposableEffect(shared) { onDispose { shared?.stopPreview() } }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, shared) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) shared?.stopPreview() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val playingKey by (shared?.channelKey ?: MutableStateFlow<String?>(null)).collectAsState()
    // Propriétaire observé : au retour du plein écran, [SharedLiveSession.demote] arrive APRÈS la recomposition du guide ;
    // sans cette lecture, l'aperçu restait sur le logo alors que le son de la chaîne continuait.
    val ownerNow by (shared?.ownerState ?: MutableStateFlow(com.ultratv.tv.nativeapp.ui.player.engine.LiveOwner.NONE)).collectAsState()
    val session = shared?.session?.takeIf {
        videoPreview && playingKey != null && ownerNow == com.ultratv.tv.nativeapp.ui.player.engine.LiveOwner.PREVIEW &&
            channel != null && shared.rowKey == com.ultratv.tv.nativeapp.ui.live.channelKey(channel)
    }
    Box(modifier.clip(RoundedCornerShape(24.design)).background(Ux.Tone), contentAlignment = Alignment.Center) {
        if (channel != null) {
            val logo: @Composable () -> Unit = { ChannelBigMark(channel, channelNumber(channel.seq, channel.num), Modifier.fillMaxSize(), radius = 24, pad = 80, bg = Ux.Tone) }
            if (session != null && shared != null) com.ultratv.tv.nativeapp.ui.live.SharedVideo(session, shared, logo) else logo()
        }
    }
}

private fun Int.dp1() = androidx.compose.ui.unit.Dp(0.5f)

private fun dayName(D: com.ultratv.tv.nativeapp.i18n.DesignStrings, offset: Int): String = when (offset) {
    -1 -> D.yesterday
    0 -> D.today
    1 -> D.tomorrow
    else -> SimpleDateFormat("EEEE", D.locale).format(Date(System.currentTimeMillis() + offset * 86_400_000L)).replaceFirstChar { it.uppercase() }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(50), bg = if (selected) Ux.Cta else Ux.Card, focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.height(48.designT)) { f ->
        Box(Modifier.padding(horizontal = 22.design).height(48.designT), contentAlignment = Alignment.Center) {
            Text(label, color = if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1)
        }
    }
}

/** Catégorie de chaînes du guide : la choisie est pleine d'accent ; OK la sélectionne (filtre la grille). */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(50), bg = if (selected) Ux.Accent else Ux.Card, focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.height(48.designT)) { f ->
        Box(Modifier.padding(horizontal = 20.design).height(48.designT), contentAlignment = Alignment.Center) {
            Text(label, color = if (f) Ux.TextOnLight else if (selected) Ux.OnAccent else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1)
        }
    }
}

/** Ligne de 84 px : colonne chaîne (logo fixe + nom) puis programmes positionnés par l'heure, 8 px d'écart. */
@Composable
private fun GuideRow(
    c: ChannelEntity, progs: List<EpgEntity>, windowStart: Long, gridW: Dp, nameColPx: Int, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, clock: androidx.compose.runtime.State<Long>,
    firstModifier: Modifier, onSelect: (EpgEntity) -> Unit, onRemind: (EpgEntity) -> Unit, onFocusProg: (EpgEntity) -> Unit,
    nowMs: Long = System.currentTimeMillis(),
) {
    Row(Modifier.fillMaxWidth().height(84.designT)) {
        // Cellule de la chaîne (maquette) : numéro, logo 48 et nom dans une carte arrondie, 8 px avant les programmes.
        Row(Modifier.width(nameColPx.design).fillMaxHeight().padding(end = 8.design).clip(RoundedCornerShape(18.design)).background(Ux.Card2).padding(horizontal = 14.design), verticalAlignment = Alignment.CenterVertically) {
            channelNumber(c.seq, c.num)?.let { n ->
                Text(n.toString(), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(40.design))
                Spacer(Modifier.width(14.design))
            }
            if (LocalChannelDisplay.current.showLogos) {
                LogoBox(c.logo, c.title, Modifier.width(48.design).height(48.design), radius = 12, pad = 4, bg = Ux.Tile)
                Spacer(Modifier.width(14.design))
            }
            // Deux lignes au lieu d'un nom coupé.
            Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, lineHeight = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            // Variantes de qualité (« 13EME RUE » HD / SD) : un badge les distingue.
            val q = remember(c.id) { com.ultratv.tv.nativeapp.data.repo.TitleCleaner.clean(c.name, live = true).quality }
            if (q != null) { Spacer(Modifier.width(8.design)); Text(q, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.design)).background(Ux.Surface2).padding(horizontal = 7.design, vertical = 2.design)) }
        }
        // Cases calculées une fois par (programmes, fenêtre), pas à chaque recomposition de la ligne.
        val slots = remember(progs, windowStart) { progs.map { slotFor(it.startMs, it.endMs, windowStart) } }
        Box(Modifier.width(gridW).fillMaxHeight()) {
            progs.forEachIndexed { idx, p ->
                val slot = slots[idx] ?: return@forEachIndexed
                val isNow = p.startMs <= nowMs && p.endMs > nowMs
                val w = (gridW * slot.widthFrac - 8.design).coerceAtLeast(24.design)
                FocusSurface(
                    onClick = { onSelect(p) },
                    onLongClick = { if (p.startMs > nowMs) onRemind(p) },
                    shape = RoundedCornerShape(14.design),
                    bg = if (isNow) Ux.NowTint else Ux.Card,
                    ringWidth = 0.dp, focusedScale = 1f,
                    modifier = Modifier.offset(x = gridW * slot.startFrac).width(w).fillMaxHeight()
                        .then(if (idx == 0 || isNow) firstModifier.takeIf { idx == 0 } ?: Modifier else Modifier),
                ) { f ->
                    val wPx = (w.value * 2).toInt()      // largeur en px de maquette
                    if (f) LaunchedEffect(p.id) { onFocusProg(p) }
                    // Sous ~90 px : fond seul (le titre apparaît dans le panneau du bas). Entre 90 et 200 px : titre seul.
                    if (wPx >= 90) Column(Modifier.fillMaxSize().clip(RoundedCornerShape(14.design)).padding(horizontal = if (wPx < 200) 10.design else 18.design, vertical = 12.design), verticalArrangement = Arrangement.spacedBy(4.design, Alignment.CenterVertically)) {
                        // softWrap = false : un titre trop long sur une ligne coupée, sans quoi la cellule repoussait l'horaire hors du cadre.
                        Text(p.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                        // Programme passé encore rejouable : badge « Replay » (la lecture passe par « Revoir »).
                        if (wPx >= 200 && p.endMs <= nowMs && ReplayUrls.availability(c, p, nowMs) == ReplayAvailability.AVAILABLE)
                            Text(D.replayTag, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.design)).background(if (f) Ux.OnFocus2.copy(alpha = 0.25f) else Ux.Surface2).padding(horizontal = 8.design, vertical = 2.design))
                        if (wPx >= 200) Text("${EpgClock.range(p.startMs, p.endMs)}", color = if (f) Ux.OnFocus2 else if (isNow) Ux.AccentInk else Ux.Text3, fontFamily = Manrope, fontSize = 15.spx, maxLines = 1, overflow = TextOverflow.Clip)
                    }
                    // Progression dans le programme en cours : fine barre en pied de case, dessinée depuis l'horloge partagée.
                    if (isNow) com.ultratv.tv.nativeapp.ui.common.EpgProgressBar(p.startMs, p.endMs, clock, Modifier.align(Alignment.BottomStart).padding(horizontal = 12.design, vertical = 8.design), heightDp = 2.5f, track = if (f) Color(0x33000000) else Ux.Surface)
                }
            }
        }
    }
}


/**
 * Guide TV (maquette Guide.dc.html / GuideClair.dc.html), sans ViewModel : aperçu + programme + actions, jours et catégories
 * (filtrent les chaînes de la grille), en-tête des créneaux avec pastille « maintenant », colonne des chaînes et grille.
 * [preview] dessine l'aperçu de la chaîne du programme sélectionné (le moteur vidéo reste du ressort de l'écran).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun GuideTvContent(
    channelCount: Int, channelAt: (Int) -> ChannelEntity?, peekAt: (Int) -> ChannelEntity?, itemKey: (Int) -> Any,
    programmes: Map<Long, List<EpgEntity>>, windowStart: Long, day: Int,
    D: com.ultratv.tv.nativeapp.i18n.DesignStrings, clock: androidx.compose.runtime.State<Long>,
    groups: List<com.ultratv.tv.nativeapp.ui.live.DirectCategory>, group: String, guideLoad: GuideLoad, nameColPx: Int,
    groupName: (com.ultratv.tv.nativeapp.ui.live.DirectCategory) -> String,
    onDay: (Int) -> Unit, onWindowStart: (Long) -> Unit, onGroup: (String) -> Unit, onVisible: (List<Long>) -> Unit, onRetry: () -> Unit,
    onSelect: (ChannelEntity, EpgEntity) -> Unit, onWatch: (ChannelEntity) -> Unit,
    onRemind: (ChannelEntity, EpgEntity) -> Unit, onRecord: (ChannelEntity, EpgEntity) -> Unit,
    preview: @Composable (androidx.compose.runtime.State<Pair<ChannelEntity, EpgEntity>?>, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    /** Instant « maintenant » des cases de la grille (injectable pour un rendu de test figé). */
    nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    val focus = LocalFocusManager.current
    var inGrid by remember { mutableStateOf(false) }
    // Programme focalisé : lu SEULEMENT par le panneau du haut et l'aperçu — lu ici, chaque déplacement recomposait
    // tout l'écran et ses lignes.
    val focusedProg = remember { mutableStateOf<Pair<ChannelEntity, EpgEntity>?>(null) }
    Column(
        modifier.fillMaxSize().padding(start = 48.design, end = 56.design, top = 40.design, bottom = 12.design)
            // Au bord de la fenêtre, DROITE / GAUCHE font défiler le temps de 90 min au lieu de buter.
            .onPreviewKeyEvent { e ->
                // ⏪ ⏩ : un jour en arrière / en avant (Hier … après-demain), sans quitter la grille.
                if (e.type == KeyEventType.KeyDown && (e.key == Key.MediaRewind || e.key == Key.MediaFastForward)) {
                    val d = (day + if (e.key == Key.MediaFastForward) 1 else -1).coerceIn(-1, 2)
                    if (d != day) onDay(d)
                    return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown || (e.key != Key.DirectionRight && e.key != Key.DirectionLeft)) return@onPreviewKeyEvent false
                val dir = if (e.key == Key.DirectionRight) FocusDirection.Right else FocusDirection.Left
                // Hors de la grille (jours, catégories) : navigation normale — avant, la touche était
                // avalée et GAUCHE ne menait plus au menu depuis « Hier ».
                if (!inGrid) return@onPreviewKeyEvent false
                if (!focus.moveFocus(dir)) onWindowStart(windowStart + if (dir == FocusDirection.Right) 3 * SLOT_MS else -3 * SLOT_MS)
                true
            },
        verticalArrangement = Arrangement.spacedBy(28.design),
    ) {
        // Aperçu vidéo de la chaîne (à gauche) + programme sélectionné et ses actions (à droite). Chacun lit l'état focalisé
        // dans SA portée : un déplacement ne recompose que ces deux blocs, jamais la grille.
        Row(Modifier.fillMaxWidth().height(300.design), horizontalArrangement = Arrangement.spacedBy(28.design)) {
            preview(focusedProg, Modifier.width(520.design).fillMaxHeight())
            ProgramInfo(
                focusedProg, clock, D, if (channelCount > 0) guideProgressLine(D, guideLoad) else null, Modifier.weight(1f).fillMaxHeight(),
                onWatch, onRemind, onRecord,
            )
        }

        // Jours, puis catégories de chaînes (Favoris, Toutes, catégories réelles de la source avec leur nombre de chaînes).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
            for (d in -1..2) DayChip(dayName(D, d), selected = d == day) { onDay(d) }
            // « Maintenant » : retour à aujourd'hui, fenêtre calée sur la demi-heure courante.
            DayChip(D.nowButton, selected = false) { onDay(0) }
            Box(Modifier.padding(horizontal = 8.design).width(2.design).height(32.design).background(Ux.Line))
            Text(D.categoryLabel.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, letterSpacing = 0.75.sp, maxLines = 1, softWrap = false)
            val chipsState = rememberLazyListState()
            LaunchedEffect(groups.size) {
                val i = groups.indexOfFirst { it.id == group }
                if (i > 0) chipsState.scrollToItem(i)
            }
            androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f), state = chipsState, horizontalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(end = 24.design)) {
                items(groups, key = { it.id }, contentType = { "category" }) { g ->
                    val name = groupName(g)
                    val label = if (g.id == com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES) name else "$name · ${com.ultratv.tv.nativeapp.ui.home.formatCount(g.count, D.locale)}"
                    CategoryChip(label, selected = g.id == group) { if (g.id != group) onGroup(g.id) }
                }
            }
        }

        // En-tête des créneaux, puis la grille (colonne des chaînes + programmes) et la ligne « maintenant ».
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.design)) {
            Row(Modifier.fillMaxWidth().height(32.design), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(nameColPx.design), contentAlignment = Alignment.CenterStart) {
                    Text(dayName(D, day), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.spx, maxLines = 1)
                }
                Row(Modifier.weight(1f)) {
                    for (i in 0 until 6) Text(EpgClock.hm(windowStart + i * SLOT_MS), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.spx, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }

            if (channelCount == 0) {
                // Jour à venir sans aucun programme : ce n'est pas un chargement en cours (le guide n'est publié que sur
                // quelques dizaines d'heures) ; « se charge encore » faisait attendre pour rien.
                if (group != com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL && guideLoad is GuideLoad.Idle) Text(if (day > 0) D.guideNoDataYet else D.guideGroupEmpty, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 33.spx, maxLines = 3)
                else if (day > 0 && guideLoad is GuideLoad.Idle) Text(D.guideNoDataYet, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 33.spx, maxLines = 3)
                else GuideEmptyState(guideLoad, D, onRetry = onRetry, touch = false)
                return@Column
            }

            val state = rememberLazyListState()
            LaunchedEffect(state, channelCount) {
                // Identifiants visibles (peek en O(1), pas de copie de toute la liste ; suit aussi l'arrivée des données).
                snapshotFlow { state.layoutInfo.visibleItemsInfo.mapNotNull { if (it.index < channelCount) peekAt(it.index)?.id else null } }
                    .distinctUntilChanged().collect { onVisible(it) }
            }
            val first = remember { FocusRequester() }
            var firstFocused by remember { mutableStateOf(false) }
            RequestInitialFocus(first, hasFocus = { firstFocused }, key = programmes.isNotEmpty())
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).onFocusChanged { inGrid = it.hasFocus }
                    // Entrée (↓ depuis les jours) : sur la première ligne, au programme en cours, et non sur la case qui se trouve
                    // sous l'onglet (TimeShift 01, à droite). Sortie par → interdite : au bord droit, la fenêtre défile de
                    // 90 min (onPreviewKeyEvent) au lieu de sauter sur l'onglet « Samedi ».
                    .focusProperties {
                        enter = { first }
                        exit = { dir -> if (dir == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
                    }
                    .focusGroup(),
            ) {
                val gridW = maxWidth - nameColPx.design
                // Ligne « maintenant » (accent, 3 px), dessinée SOUS les cellules (visible entre elles) : seulement si l'instant courant est dans la fenêtre.
                // Position lue DANS le calque (mise en page + alpha) : le tic de l'horloge ne recompose rien.
                val accent = Ux.Accent
                Box(
                    Modifier.offset { androidx.compose.ui.unit.IntOffset((nameColPx.design + gridW * (com.ultratv.tv.nativeapp.ui.common.EpgProgress.nowLineFraction(clock.value, windowStart, GUIDE_WINDOW_MS) ?: 0f)).roundToPx(), 0) }
                        .width(3.design).fillMaxHeight()
                        .graphicsLayer { alpha = if (com.ultratv.tv.nativeapp.ui.common.EpgProgress.nowLineFraction(clock.value, windowStart, GUIDE_WINDOW_MS) != null) 1f else 0f }
                        .background(accent),
                )
                LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(8.design), contentPadding = PaddingValues(top = 8.design, bottom = 24.design)) {
                    items(count = channelCount, key = itemKey, contentType = { "row" }) { i ->
                        val c = channelAt(i)
                        if (c != null) {
                            GuideRow(
                                c, programmes[c.id].orEmpty(), windowStart, gridW, nameColPx, D, clock,
                                firstModifier = if (i == 0) Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused } else Modifier,
                                onSelect = { p -> onSelect(c, p) }, onRemind = { p -> onRemind(c, p) }, onFocusProg = { p -> focusedProg.value = c to p },
                                nowMs = nowProvider(),
                            )
                        } else Spacer(Modifier.height(84.designT))
                    }
                }
                NowPill(clock, windowStart, nameColPx.design, gridW)
            }
        }
    }
}

// ───────────────────────── Tactile ─────────────────────────

private val TOUCH_SLOT_W = 128.dp
private val TOUCH_ROW_H = 64.dp

/**
 * Guide tactile : un seul plan défilant dans les deux sens au doigt (horizontal : le temps ; vertical : les chaînes).
 * La colonne des chaînes et la ligne des heures restent épinglées ; un appui sur un programme ouvre ses actions
 * (GuideActions), un appui long sur un programme à venir pose un rappel.
 */
@Composable
private fun GuideTouch(
    channels: androidx.paging.compose.LazyPagingItems<ChannelEntity>, programmes: Map<Long, List<EpgEntity>>, windowStart: Long, day: Int,
    D: com.ultratv.tv.nativeapp.i18n.DesignStrings, vm: GuideGridViewModel, guideLoad: GuideLoad, onDay: (Int) -> Unit, onShift: (Int) -> Unit,
    onSelect: (ChannelEntity, EpgEntity) -> Unit, onRemind: (ChannelEntity, EpgEntity) -> Unit,
    groupLabel: String, onGroup: () -> Unit,
) {
    val M = com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current
    val hScroll = androidx.compose.foundation.rememberScrollState()
    val gridW = TOUCH_SLOT_W * 6
    val chanW = LocalChannelDisplay.current.guideNameWidth.touchDp.dp
    val now = remember(windowStart) { System.currentTimeMillis() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(D.tvGuide, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 1)
                Text("${dayName(D, day)} · ${EpgClock.hm(windowStart)} – ${EpgClock.hm(windowStart + GUIDE_WINDOW_MS)}", color = Ux.Text3, fontFamily = Manrope, fontSize = 12.sp, maxLines = 1)
                if (channels.itemCount > 0) guideProgressLine(D, guideLoad)?.let { Text(it, color = Ux.Accent, fontFamily = Manrope, fontSize = 11.sp, maxLines = 1) }
            }
            com.ultratv.tv.nativeapp.ui.mobile.SearchAction()
            com.ultratv.tv.nativeapp.ui.mobile.IconCircle(com.ultratv.tv.nativeapp.ui.design.Icons.Chevron, D.today.let { "−3 h" }, Ux.Surface, Ux.Text, { onShift(-1) })
            com.ultratv.tv.nativeapp.ui.mobile.IconCircle("M9 6l6 6-6 6", "+3 h", Ux.Surface, Ux.Text, { onShift(1) })
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            item {
                FocusSurface(onClick = onGroup, shape = RoundedCornerShape(20.dp), bg = Ux.Surface2, modifier = Modifier.height(40.dp).widthIn(max = 280.dp)) { _ ->
                    Box(Modifier.height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(D.guideGroupPill(groupLabel), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            items((-1..2).toList()) { d ->
                val sel = d == day
                FocusSurface(onClick = { onDay(d) }, shape = RoundedCornerShape(20.dp), bg = if (sel) Ux.Cta else Ux.Surface, modifier = Modifier.height(40.dp)) { _ ->
                    Box(Modifier.height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(dayName(D, d), color = if (sel) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
        if (channels.itemCount == 0) {
            GuideEmptyState(guideLoad, D, onRetry = { vm.retryGuide() }, touch = true, modifier = Modifier.padding(horizontal = 20.dp))
            return@Column
        }
        val state = rememberLazyListState()
        LaunchedEffect(state, channels) {
            // Identifiants visibles (peek en O(1), pas de copie de toute la liste ; suit aussi l'arrivée des données).
            snapshotFlow { state.layoutInfo.visibleItemsInfo.mapNotNull { if (it.index < channels.itemCount) channels.peek(it.index)?.id else null } }
                .distinctUntilChanged().collect { vm.setVisible(it) }
        }
        com.ultratv.tv.nativeapp.ui.mobile.TouchRefresh(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                Column(Modifier.width(chanW + gridW)) {
                    Row(Modifier.height(28.dp)) {
                        Spacer(Modifier.width(chanW))
                        for (i in 0 until 6) Text(EpgClock.hm(windowStart + i * SLOT_MS), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(TOUCH_SLOT_W), maxLines = 1)
                    }
                    val safeKey1 = com.ultratv.tv.nativeapp.ui.common.rememberSafeItemKey(channels) { it.id }
                    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.weight(1f)) {
                        items(count = channels.itemCount, key = safeKey1, contentType = { "row" }) { i ->
                            val c = channels[i]
                            if (c != null) TouchGuideRow(c, programmes[c.id].orEmpty(), windowStart, hScroll.value, chanW, onSelect = { p -> onSelect(c, p) }, onRemind = { p -> onRemind(c, p) })
                            else Spacer(Modifier.height(TOUCH_ROW_H))
                        }
                    }
                }
                val nowFrac = (now - windowStart).toFloat() / GUIDE_WINDOW_MS
                if (nowFrac in 0f..1f) Box(Modifier.offset(x = chanW + gridW * nowFrac).width(2.dp).fillMaxHeight().background(Ux.Accent))
            }
        }
    }
}

@Composable
private fun TouchGuideRow(c: ChannelEntity, progs: List<EpgEntity>, windowStart: Long, scrollPx: Int, chanW: androidx.compose.ui.unit.Dp, onSelect: (EpgEntity) -> Unit, onRemind: (EpgEntity) -> Unit) {
    val nowMs = System.currentTimeMillis()
    val gridW = TOUCH_SLOT_W * 6
    Box(Modifier.width(chanW + gridW).height(TOUCH_ROW_H)) {
        Box(Modifier.offset(x = chanW).width(gridW).fillMaxHeight()) {
            progs.forEach { p ->
                val slot = slotFor(p.startMs, p.endMs, windowStart) ?: return@forEach
                val isNow = p.startMs <= nowMs && p.endMs > nowMs
                val w = (gridW * slot.widthFrac - 6.dp).coerceAtLeast(20.dp)
                FocusSurface(
                    onClick = { onSelect(p) }, onLongClick = { if (p.startMs > nowMs) onRemind(p) }, shape = RoundedCornerShape(12.dp),
                    bg = if (isNow) Ux.Surface2 else Ux.SurfaceDeep,
                    modifier = Modifier.offset(x = gridW * slot.startFrac).width(w).fillMaxHeight().semantics { contentDescription = "${p.title}, ${EpgClock.hm(p.startMs)} – ${EpgClock.hm(p.endMs)}" },
                ) { _ ->
                    if (w >= 56.dp) Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                        Text(p.title, color = if (isNow) Ux.Text else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // En 12 h les horaires sont plus longs (« 4:30 PM – 5:00 PM ») : seuil relevé pour ne jamais déborder de la case.
                        if (w >= (if (EpgClock.use24()) 110.dp else 142.dp)) Text("${EpgClock.hm(p.startMs)} – ${EpgClock.hm(p.endMs)}", color = Ux.Text3, fontFamily = Manrope, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
        // Colonne chaîne épinglée à gauche (suit le défilement horizontal).
        Row(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(scrollPx, 0) }.width(chanW).fillMaxHeight().background(Ux.Bg).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (LocalChannelDisplay.current.showLogos) LogoBox(c.logo, c.title, Modifier.width(40.dp).height(28.dp), radius = 12, pad = 3)
            else channelNumber(c.seq, c.num)?.let { n -> Text(n.toString(), color = Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false) }
            Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

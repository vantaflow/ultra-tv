package com.ultratv.tv.nativeapp.ui.guide

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ultratv.tv.nativeapp.data.replay.ReplayAvailability
import com.ultratv.tv.nativeapp.data.replay.ReplayUrls
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusDirection
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
import androidx.tv.material3.Text
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
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    providerRepo: ProviderRepository,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val reminders: RemindersScheduler,
    private val recScheduler: com.ultratv.tv.nativeapp.data.recording.RecordingScheduler,
    recDao: com.ultratv.tv.nativeapp.data.db.RecordingDao,
    private val replay: com.ultratv.tv.nativeapp.data.replay.ReplayService,
    private val limits: com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore,
) : ViewModel() {
    fun maxConnections(providerId: Long) = limits.maxConnections(providerId)
    fun replayUrl(channel: ChannelEntity, prog: EpgEntity): String? = replay.urlFor(channel, prog)
    fun armReplay(channel: ChannelEntity, prog: EpgEntity) = replay.armFromGuide(prog, channel.providerId)

    /** Un enregistrement en cours occupe la (seule) connexion : le dialogue et « Regarder » le signalent. */
    val recordingRunning: StateFlow<Boolean> = recDao.observeRunningCount().map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun record(channel: ChannelEntity, prog: EpgEntity, wholeSeries: Boolean, onResult: (com.ultratv.tv.nativeapp.data.recording.ScheduleResult) -> Unit) {
        viewModelScope.launch { onResult(recScheduler.schedule(channel, prog, wholeSeries)) }
    }

    private val pid = providerRepo.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    private val _windowStart = MutableStateFlow(guideWindowStart(System.currentTimeMillis(), 0))
    val windowStart: StateFlow<Long> = _windowStart.asStateFlow()
    fun setWindowStart(ms: Long) { _windowStart.value = ms }

    /** Seules les chaînes ayant un guide dans la fenêtre sont des lignes (pas 55 000 lignes vides). */
    val channels: kotlinx.coroutines.flow.Flow<PagingData<ChannelEntity>> = combine(pid, _windowStart) { id, w -> id to w }
        .flatMapLatest { (id, w) ->
            if (id == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 20, prefetchDistance = 10, initialLoadSize = 30, enablePlaceholders = false)) {
                channelDao.pagedWithEpg(id, w, w + GUIDE_WINDOW_MS)
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
    val now = remember(windowStart) { System.currentTimeMillis() }
    val focus = LocalFocusManager.current
    var inGrid by remember { mutableStateOf(false) }
    var focusedProg by remember { mutableStateOf<Pair<ChannelEntity, EpgEntity>?>(null) }
    var actionTarget by remember { mutableStateOf<Pair<ChannelEntity, EpgEntity>?>(null) }
    val recordingRunning by vm.recordingRunning.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(start = 72.design, end = 96.design, top = 54.design)
            // Au bord de la fenêtre, DROITE / GAUCHE font défiler le temps de 90 min au lieu de buter.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || (e.key != Key.DirectionRight && e.key != Key.DirectionLeft)) return@onPreviewKeyEvent false
                val dir = if (e.key == Key.DirectionRight) FocusDirection.Right else FocusDirection.Left
                if (inGrid && !focus.moveFocus(dir)) {
                    vm.setWindowStart(windowStart + if (dir == FocusDirection.Right) 3 * SLOT_MS else -3 * SLOT_MS)
                    true
                } else true
            },
        verticalArrangement = Arrangement.spacedBy(28.design),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
                Text(D.tvGuide, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 1)
                val dayLabel = dayName(D, day)
                Text("$dayLabel · ${EpgClock.range(windowStart, windowStart + GUIDE_WINDOW_MS)}", color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                for (d in -1..2) DayChip(dayName(D, d), selected = d == day) { day = d; vm.setWindowStart(guideWindowStart(System.currentTimeMillis(), d)) }
            }
        }

        // En-tête des créneaux
        Row(Modifier.fillMaxWidth().padding(bottom = 8.design)) {
            Spacer(Modifier.width(260.design))
            Row(Modifier.weight(1f)) {
                for (i in 0 until 6) Text(EpgClock.hm(windowStart + i * SLOT_MS), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp1()).background(Ux.Surface2))

        if (channels.itemCount == 0) {
            Text(D.guideNoData, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 33.spx, maxLines = 3)
            return@Column
        }

        val state = rememberLazyListState()
        LaunchedEffect(state, channels) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }.collect { idx ->
                vm.setVisible(idx.mapNotNull { channels.itemSnapshotList.getOrNull(it)?.id })
            }
        }
        val first = remember { FocusRequester() }
        var firstFocused by remember { mutableStateOf(false) }
        RequestInitialFocus(first, hasFocus = { firstFocused }, key = programmes.isNotEmpty())
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).onFocusChanged { inGrid = it.hasFocus }) {
            val gridW = maxWidth - 260.design
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(12.design), contentPadding = PaddingValues(top = 12.design, bottom = 54.design)) {
                items(count = channels.itemCount, key = channels.itemKey { it.id }, contentType = { "row" }) { i ->
                    val c = channels[i]
                    if (c != null) {
                        GuideRow(
                            c, programmes[c.id].orEmpty(), windowStart, gridW, D,
                            firstModifier = if (i == 0) Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused } else Modifier,
                            onSelect = { p -> actionTarget = c to p }, onRemind = { vm.addReminder(c, it); askNotifications(); Toaster.ok(D.remindSet) }, onFocusProg = { p -> focusedProg = c to p },
                        )
                    } else Spacer(Modifier.height(84.design))
                }
            }
            // Ligne « maintenant » (accent, 3 px) : seulement si l'instant courant est dans la fenêtre.
            val nowFrac = (now - windowStart).toFloat() / GUIDE_WINDOW_MS
            if (nowFrac in 0f..1f) Box(Modifier.offset(x = 260.design + gridW * nowFrac).width(3.design).fillMaxHeight().background(Ux.Accent))
        }
        focusedProg?.let { (ch, pr) -> InfoPanel(ch, pr) }
    }
    actionTarget?.let { (ch, pr) ->
        val nowMs = System.currentTimeMillis()
        val replayUrl = remember(ch.id, pr.id) { vm.replayUrl(ch, pr) }
        ProgramActionsDialog(
            channel = ch, prog = pr, state = programActionState(pr, replayUrl, nowMs), D = D, recordingRunning = recordingRunning,
            onReplay = { actionTarget = null; replayUrl?.let { vm.armReplay(ch, pr); onPlayUrl(it, pr.title) } },
            onWatch = {
                actionTarget = null
                // Lecture et enregistrement se partagent la connexion : on prévient avant de la prendre.
                if (ConnectionPolicy.onPlayRequested(recordingRunning, vm.maxConnections(ch.providerId)) != ConnectionWarning.NONE) Toaster.show(D.recConnectionBusy)
                onPlayChannel(ch)
            },
            onRemind = { actionTarget = null; vm.addReminder(ch, pr); askNotifications(); Toaster.ok(D.remindSet) },
            onRecord = { series ->
                actionTarget = null
                vm.record(ch, pr, series) { r ->
                    when (r) {
                        is ScheduleResult.Scheduled -> Toaster.ok(D.recScheduled(r.count))
                        ScheduleResult.NoSpace -> Toaster.err(D.recNoSpace)
                        ScheduleResult.NothingToSchedule -> Toaster.show(D.recNothing)
                    }
                }
            },
            onDismiss = { actionTarget = null },
        )
    }
}

/** Panneau d'information du programme focalisé (les cellules courtes n'affichent pas de texte). */
@Composable
private fun InfoPanel(ch: ChannelEntity, p: EpgEntity) {
    Row(Modifier.fillMaxWidth().height(120.design).clip(RoundedCornerShape(20.design)).background(Ux.SurfaceDeep).padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
        LogoBox(ch.logo, ch.title, Modifier.width(84.design).height(56.design), radius = 10, pad = 6)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
            Text(p.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ch.title} · ${EpgClock.range(p.startMs, p.endMs)}", color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            p.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(28.design), bg = if (selected) Ux.Cta else Ux.Surface, modifier = Modifier.height(56.design)) { f ->
        Box(Modifier.padding(horizontal = 28.design).height(56.design), contentAlignment = Alignment.Center) {
            Text(label, color = if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
        }
    }
}

/** Ligne de 84 px : colonne chaîne (logo fixe + nom) puis programmes positionnés par l'heure, 8 px d'écart. */
@Composable
private fun GuideRow(
    c: ChannelEntity, progs: List<EpgEntity>, windowStart: Long, gridW: Dp, D: com.ultratv.tv.nativeapp.i18n.DesignStrings,
    firstModifier: Modifier, onSelect: (EpgEntity) -> Unit, onRemind: (EpgEntity) -> Unit, onFocusProg: (EpgEntity) -> Unit,
) {
    val nowMs = System.currentTimeMillis()
    Row(Modifier.fillMaxWidth().height(84.design)) {
        Row(Modifier.width(260.design).fillMaxHeight().padding(end = 16.design), verticalAlignment = Alignment.CenterVertically) {
            LogoBox(c.logo, c.title, Modifier.width(64.design).height(40.design), radius = 8, pad = 4)
            Spacer(Modifier.width(14.design))
            Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            // Variantes de qualité (« 13EME RUE » HD / SD) : un badge les distingue.
            val q = remember(c.id) { com.ultratv.tv.nativeapp.data.repo.TitleCleaner.clean(c.name, live = true).quality }
            if (q != null) { Spacer(Modifier.width(8.design)); Text(q, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.design)).background(Ux.Surface2).padding(horizontal = 7.design, vertical = 2.design)) }
        }
        Box(Modifier.width(gridW).fillMaxHeight()) {
            progs.forEachIndexed { idx, p ->
                val slot = slotFor(p.startMs, p.endMs, windowStart) ?: return@forEachIndexed
                val isNow = p.startMs <= nowMs && p.endMs > nowMs
                val w = (gridW * slot.widthFrac - 8.design).coerceAtLeast(24.design)
                FocusSurface(
                    onClick = { onSelect(p) },
                    onLongClick = { if (p.startMs > nowMs) onRemind(p) },
                    shape = RoundedCornerShape(14.design),
                    bg = if (isNow) Ux.Surface2 else Ux.SurfaceDeep,
                    ringWidth = 5.design, focusedScale = 1.03f,
                    modifier = Modifier.offset(x = gridW * slot.startFrac).width(w).fillMaxHeight()
                        .then(if (idx == 0 || isNow) firstModifier.takeIf { idx == 0 } ?: Modifier else Modifier),
                ) { f ->
                    val wPx = (w.value * 2).toInt()      // largeur en px de maquette
                    if (f) LaunchedEffect(p.id) { onFocusProg(p) }
                    // Sous ~90 px : fond seul (le titre apparaît dans le panneau du bas). Entre 90 et 200 px : titre seul.
                    if (wPx >= 90) Column(Modifier.fillMaxSize().clip(RoundedCornerShape(14.design)).padding(horizontal = if (wPx < 200) 10.design else 18.design, vertical = 12.design), verticalArrangement = Arrangement.spacedBy(4.design, Alignment.CenterVertically)) {
                        Text(p.title, color = if (f) Ux.TextOnLight else if (isNow) Ux.Text else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Clip)
                        // Programme passé encore rejouable : badge « Replay » (la lecture passe par « Revoir »).
                        if (wPx >= 200 && p.endMs <= nowMs && ReplayUrls.availability(c, p, nowMs) == ReplayAvailability.AVAILABLE)
                            Text(D.replayTag, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.design)).background(if (f) Ux.OnFocus2.copy(alpha = 0.25f) else Ux.Surface2).padding(horizontal = 8.design, vertical = 2.design))
                        if (wPx >= 200) Text("${EpgClock.range(p.startMs, p.endMs)}", color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
    }
}

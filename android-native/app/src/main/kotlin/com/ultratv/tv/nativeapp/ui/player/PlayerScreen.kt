package com.ultratv.tv.nativeapp.ui.player

import android.content.Intent
import android.net.Uri
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.PlaybackResolver
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.recording.RecordingRepository
import com.ultratv.tv.nativeapp.data.repo.HistoryRepository
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.LiveBadge
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode
import com.ultratv.tv.nativeapp.ui.player.engine.BufferPreset
import com.ultratv.tv.nativeapp.ui.player.engine.ChannelPlaybackMemory
import com.ultratv.tv.nativeapp.ui.player.engine.Combo
import com.ultratv.tv.nativeapp.ui.player.engine.DecoderMode
import com.ultratv.tv.nativeapp.ui.player.engine.EngineKind
import com.ultratv.tv.nativeapp.ui.player.engine.Notice
import com.ultratv.tv.nativeapp.ui.player.engine.Phase
import com.ultratv.tv.nativeapp.ui.player.engine.PlayErrorKind
import com.ultratv.tv.nativeapp.ui.player.engine.PlaybackSession
import com.ultratv.tv.nativeapp.ui.player.engine.PrefsChannelPlaybackMemory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackContext,
    private val history: HistoryRepository,
    private val zapQueue: LivePlaybackQueue,
    private val provider: ProviderRepository,
    private val epgDao: EpgDao,
    private val recordings: RecordingRepository,
    private val prefs: UserPreferencesStore,
    private val channelDao: ChannelDao,
    private val adaptive: AdaptiveProfile,
    private val categoryManager: com.ultratv.tv.nativeapp.data.repo.CategoryManager,
    val memory: PrefsChannelPlaybackMemory,
    val network: NetworkMonitor,
) : ViewModel() {

    val prefsFlow: Flow<UserPrefs> = prefs.flow
    suspend fun playbackPrefs(): UserPrefs = prefs.flow.first()

    /** Réglages EFFECTIFS : les choix manuels de l'utilisateur l'emportent sur l'automatique. */
    fun resolved(p: UserPrefs): ResolvedPlayback = PlaybackResolver.resolve(adaptive.state.value.auto, p, adaptive.heapClassMb)
    val adaptiveState get() = adaptive.state

    val current: StateFlow<PlaybackContext.Item?> = playback.current

    /** Programme en cours de la chaîne regardée (guide), rafraîchi toutes les 20 s. */
    val nowProgramme: StateFlow<EpgEntity?> = playback.current.flatMapLatest { item ->
        if (item == null || item.kind != "LIVE") flowOf(null)
        else flow<EpgEntity?> {
            while (true) {
                val ch = channelDao.byRemoteId(item.providerId, item.remoteId)
                val now = System.currentTimeMillis()
                emit(ch?.let { epgDao.rangeForChannels(listOf(it.id), now, now + 1).firstOrNull { p -> p.startMs <= now && p.endMs > now } })
                delay(20_000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun recordLive(maxMinutes: Int = 120, toastTemplate: String = "Recording queued (max %1\$d min)") {
        val c = playback.current.value ?: return
        if (c.kind != "LIVE") return
        viewModelScope.launch {
            recordings.enqueue(c.providerId, "LIVE", c.remoteId, c.title, c.streamUrl, maxMinutes)
            Toaster.ok(toastTemplate.format(maxMinutes))
        }
    }

    private fun setLive(target: com.ultratv.tv.nativeapp.data.db.ChannelEntity, url: String) {
        playback.set(PlaybackContext.Item(
            providerId = target.providerId, kind = "LIVE", remoteId = target.remoteId, title = target.title, poster = target.logo, streamUrl = url,
            badge = TitleCleaner.prefixBadge(target.name, TitleCleaner.clean(target.name, live = true).title),
        ))
    }

    /** Chaîne suivante / précédente de la file de zapping (live) ; renvoie la nouvelle URL ou null. */
    suspend fun zap(forward: Boolean): String? {
        val target = (if (forward) zapQueue.next() else zapQueue.previous()) ?: return null
        val resolved = provider.resolvePlayUrl(target.id, target.streamUrl)
        setLive(target, resolved)
        return resolved
    }

    data class DrawerEntry(
        val channel: com.ultratv.tv.nativeapp.data.db.ChannelEntity,
        val now: EpgEntity?, val next: EpgEntity?, val isCurrent: Boolean,
    )

    private suspend fun entriesFor(channels: List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>, currentId: Long?): List<DrawerEntry> {
        val now = System.currentTimeMillis()
        val rows = channels.map { it.id }.chunked(500).flatMap { epgDao.rangeForChannels(it, now - 30 * 60_000, now + 4 * 60 * 60_000) }.groupBy { it.channelId }
        return channels.map { c ->
            val list = rows[c.id].orEmpty()
            DrawerEntry(c, list.firstOrNull { it.startMs <= now && it.endMs > now }, list.firstOrNull { it.startMs > now }, c.id == currentId)
        }
    }

    /** File de zapping courante (la liste que l'utilisateur parcourait avant d'ouvrir le lecteur). */
    private val queueEntries: StateFlow<List<DrawerEntry>> = zapQueue.state.map { s ->
        if (s == null) emptyList() else entriesFor(s.channels, s.channels.getOrNull(s.index)?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Catégorie parcourue dans le tiroir (null = la file de zapping courante). */
    private val browsedCategory = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val browsedEntries = kotlinx.coroutines.flow.MutableStateFlow<List<DrawerEntry>?>(null)

    val queue: StateFlow<List<DrawerEntry>> = kotlinx.coroutines.flow.combine(queueEntries, browsedEntries, playback.current) { q, b, cur ->
        if (b == null) q else b.map { it.copy(isCurrent = it.channel.remoteId == cur?.remoteId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Catégories LIVE actives (colonne de gauche du tiroir). */
    val categories: StateFlow<List<com.ultratv.tv.nativeapp.data.repo.CategoryRow>> = playback.current.flatMapLatest { item ->
        if (item == null) flowOf(emptyList()) else categoryManager.observe(item.providerId, "LIVE", "")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCategory: StateFlow<String?> = browsedCategory

    /** Charge les chaînes d'une catégorie dans le tiroir (sans zapper). */
    fun browse(categoryId: String) {
        val pid = playback.current.value?.providerId ?: return
        browsedCategory.value = categoryId
        viewModelScope.launch { browsedEntries.value = entriesFor(channelDao.windowCategory(pid, categoryId, 300, 0).filter { !it.junk && !it.isSeparator }, null) }
    }

    fun resetBrowse() { browsedCategory.value = null; browsedEntries.value = null }

    suspend fun zapTo(channel: com.ultratv.tv.nativeapp.data.db.ChannelEntity): String? {
        val browsed = browsedEntries.value
        if (browsed != null) {
            // Chaîne choisie dans une autre catégorie : la file de zapping devient cette catégorie.
            zapQueue.set(browsed.map { it.channel }, channel)
        } else {
            val s = zapQueue.state.value ?: return null
            if (s.channels.none { it.id == channel.id }) return null
            zapQueue.set(s.channels, channel)
        }
        val resolved = provider.resolvePlayUrl(channel.id, channel.streamUrl)
        setLive(channel, resolved)
        return resolved
    }

    suspend fun prepareResume(): Long {
        val c = playback.current.value ?: return 0L
        if (c.kind == "LIVE") return 0L
        return history.resumePositionMs(c.providerId, c.kind, c.remoteId)
    }

    fun recordProgress(positionMs: Long, durationMs: Long) {
        val c = playback.current.value ?: return
        if (positionMs < 5_000 && c.kind != "LIVE") return
        viewModelScope.launch {
            history.record(
                providerId = c.providerId, kind = c.kind, remoteId = c.remoteId, title = c.title, poster = c.poster, streamUrl = c.streamUrl,
                positionMs = if (c.kind == "LIVE") 0 else positionMs, durationMs = if (c.kind == "LIVE") 0 else durationMs, parentRemoteId = c.parentRemoteId,
            )
        }
    }

    fun setEngine(v: String) { viewModelScope.launch { prefs.setPlayerEngine(v) } }
    fun setDecoder(v: String) { viewModelScope.launch { prefs.setDecoderMode(v) } }
}

private enum class Panel { None, Options, Tracks }

/** Onglets du panneau de réglages (maquette LecteurReglages). */
private enum class SideTab { TRACKS, DISPLAY, PLAYER, STATS }

/**
 * Lecteur (maquette Lecteur.dc.html). La surcouche est la même quel que soit le moteur (Media3 / LibVLC) :
 * en-tête (badge EN DIRECT, chaîne, programme, heure), pied (progression, pause 96 px focalisée, pilules).
 * Elle disparaît après 5 s d'inactivité. Aucune URL n'est jamais affichée, ni dans l'interface ni dans les messages d'erreur.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(url: String, title: String, onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    // Le lecteur reste sombre quel que soit le thème de l'application.
    remember { Ux.playerActive = true }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Ux.playerActive = false } }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val D = LocalDs.current
    val S = LocalStrings.current
    val item by vm.current.collectAsState()
    val isLive = item?.kind == "LIVE"
    val prefs by vm.prefsFlow.collectAsState(initial = null)
    val p = prefs ?: run { Box(Modifier.fillMaxSize().background(Color.Black)); return }

    var currentUrl by remember { mutableStateOf(url) }
    var panel by remember { mutableStateOf(Panel.None) }
    var drawerOpen by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var aspect by remember { mutableStateOf(AspectMode.FIT) }
    var speed by remember { mutableStateOf(1f) }
    var statsOpen by remember { mutableStateOf(false) }
    var sleepDeadline by remember { mutableLongStateOf(0L) }
    val latestPrefs by androidx.compose.runtime.rememberUpdatedState(p)

    val container = remember { FrameLayout(context).apply { setBackgroundColor(android.graphics.Color.BLACK) } }
    val session = remember {
        PlaybackSession(
            ctx = context, scope = scope, container = container,
            settings = { vm.resolved(latestPrefs) }, memory = vm.memory, network = vm.network,
            isLive = isLive, autoFrameRate = p.autoFrameRate, userAgent = "UltraTV/1.0 (Android TV)",
        )
    }
    val state by session.state.collectAsState()
    DisposableEffect(Unit) {
        onDispose { session.engine?.let { vm.recordProgress(it.positionMs, it.durationMs.coerceAtLeast(0)) }; session.release() }
    }
    LaunchedEffect(Unit) {
        session.notices.collect { n ->
            Toaster.show(when (n) { Notice.USING_VLC -> D.noticeVlc; Notice.USING_EXO -> D.noticeExo; Notice.USING_SOFTWARE -> D.noticeSoftware; Notice.RETRYING -> D.noticeRetry })
        }
    }
    LaunchedEffect(currentUrl) {
        val it = vm.current.value
        val resume = vm.prepareResume()
        session.start(currentUrl, it?.let { x -> "${x.providerId}:${x.remoteId}" }, resume)
    }
    // Progression enregistrée toutes les 10 s (« Reprendre la lecture »).
    LaunchedEffect(Unit) { while (true) { delay(10_000); session.engine?.let { e -> if (e.durationMs > 0) vm.recordProgress(e.positionMs, e.durationMs) } } }
    // Minuterie de sommeil.
    LaunchedEffect(sleepDeadline) {
        if (sleepDeadline <= 0L) return@LaunchedEffect
        while (System.currentTimeMillis() < sleepDeadline) delay(5_000)
        session.engine?.pause(); onBack()
    }
    // Position / durée / horloge (500 ms) ; masquage de la surcouche après 5 s sans action.
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(-1L) }
    var playing by remember { mutableStateOf(true) }
    var clock by remember { mutableStateOf(EpgClock.hm(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            session.engine?.let { pos = it.positionMs; dur = it.durationMs; playing = it.isPlaying }
            clock = EpgClock.hm(System.currentTimeMillis())
            if (overlayVisible && panel == Panel.None && !drawerOpen && System.currentTimeMillis() - lastInteraction > 5_000) overlayVisible = false
            delay(500)
        }
    }
    LaunchedEffect(aspect) { session.engine?.setAspect(aspect) }
    LaunchedEffect(speed) { session.engine?.setSpeed(speed) }
    LaunchedEffect(state.combo, state.phase) { session.engine?.setAspect(aspect) }

    fun touch() { lastInteraction = System.currentTimeMillis(); overlayVisible = true }
    BackHandler {
        when {
            panel != Panel.None -> panel = Panel.None
            drawerOpen -> drawerOpen = false
            overlayVisible && state.phase == Phase.PLAYING -> overlayVisible = false
            else -> onBack()
        }
    }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    val pauseFocus = remember { FocusRequester() }
    LaunchedEffect(overlayVisible) { if (overlayVisible && panel == Panel.None) runCatching { pauseFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus).androidx_focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val hidden = !overlayVisible && panel == Panel.None && !drawerOpen
                touch()
                if (!hidden) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> if (isLive) { scope.launch { vm.zap(false)?.let { currentUrl = it } }; true } else false
                    Key.DirectionDown -> if (isLive) { scope.launch { vm.zap(true)?.let { currentUrl = it } }; true } else false
                    Key.DirectionLeft -> if (!isLive) { session.engine?.let { it.seekTo((it.positionMs - 10_000).coerceAtLeast(0)) }; true } else false
                    Key.DirectionRight -> if (!isLive) { session.engine?.let { it.seekTo(it.positionMs + 10_000) }; true } else false
                    else -> true      // OK / autres : on affiche seulement la surcouche
                }
            },
    ) {
        AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())

        // Chargement : visuel de la chaîne avec anneau de progression.
        if (state.phase == Phase.LOADING) LoadingVisual(item?.poster, item?.title ?: title)
        if (state.phase == Phase.ERROR) ErrorPanel(
            kind = state.error ?: PlayErrorKind.UNKNOWN, canNext = isLive, D = D,
            onRetry = { session.retry() },
            onNext = { scope.launch { vm.zap(true)?.let { currentUrl = it } } },
            onClose = onBack,
        )

        if (overlayVisible && state.phase != Phase.ERROR && !drawerOpen && panel == Panel.None) {
            Header(item, title, vm, clock, isLive, D)
            Footer(
                isLive = isLive, pos = pos, dur = dur, playing = playing, programme = vm.nowProgramme.collectAsState().value, D = D, pauseFocus = pauseFocus,
                onToggle = { session.engine?.let { if (it.isPlaying) it.pause() else it.play() }; touch() },
                onSeek = { d -> session.engine?.let { it.seekTo((it.positionMs + d).coerceAtLeast(0)) }; touch() },
                onTracks = { panel = Panel.Tracks }, onOptions = { panel = Panel.Options },
                onRecord = { vm.recordLive(120, S.recordingQueuedTemplate) }, onChannels = { drawerOpen = true },
            )
        }
        if (statsOpen) StatsCard(session, D, Modifier.align(Alignment.TopEnd).padding(top = 220.design, end = 96.design))
        if (drawerOpen && isLive) LiveDrawer(vm = vm, onPick = { ch -> scope.launch { vm.zapTo(ch)?.let { currentUrl = it }; drawerOpen = false } }, onDismiss = { drawerOpen = false })
        if (panel != Panel.None) PlayerSidePanel(
            initial = if (panel == Panel.Tracks) SideTab.TRACKS else SideTab.PLAYER,
            title = item?.title ?: title, p = p, vm = vm, session = session, state = state, aspect = aspect, speed = speed, isLive = isLive, statsOpen = statsOpen, sleepActive = sleepDeadline > 0, D = D,
            onAspect = { aspect = it }, onSpeed = { speed = it }, onStats = { statsOpen = !statsOpen },
            onSleep = { min -> sleepDeadline = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L },
            onSwitch = { c -> session.switchTo(c) }, onBuffer = { b -> session.setBufferPreset(b) },
            onExternal = { runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(currentUrl), "video/*"); flags = Intent.FLAG_ACTIVITY_NEW_TASK }, S.recordingsOpenWith)) } },
            onClose = { panel = Panel.None },
        )
    }
}

private fun Modifier.androidx_focusable() = this.focusable()

// ───────────────────────── Surcouche ─────────────────────────

@Composable
private fun Header(item: PlaybackContext.Item?, fallbackTitle: String, vm: PlayerViewModel, clock: String, isLive: Boolean, D: DesignStrings) {
    val programme by vm.nowProgramme.collectAsState()
    Row(
        Modifier.fillMaxWidth().height(200.design).background(Color(0xD10A0A0C)).padding(horizontal = 96.design, vertical = 54.design),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.design), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                if (isLive) LiveBadge(D.live)
                // Un seul titre : avec un programme du guide la chaîne passe en petit ; sinon son nom EST le titre.
                val name = item?.title ?: fallbackTitle
                if (programme != null || !isLive) Text(name, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                item?.badge?.let { b -> Text(b, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, modifier = Modifier.clip(RoundedCornerShape(6.design)).background(Ux.Surface2).padding(horizontal = 8.design, vertical = 2.design)) }
            }
            Text(
                if (isLive) (programme?.title ?: item?.title ?: fallbackTitle) else (item?.title ?: fallbackTitle),
                color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, lineHeight = 48.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(clock, color = Color(0xFFE4E4E7), fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 36.spx, maxLines = 1)
    }
}

@Composable
private fun Footer(
    isLive: Boolean, pos: Long, dur: Long, playing: Boolean, programme: EpgEntity?, D: DesignStrings, pauseFocus: FocusRequester,
    onToggle: () -> Unit, onSeek: (Long) -> Unit, onTracks: () -> Unit, onOptions: () -> Unit, onRecord: () -> Unit, onChannels: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val frac: Float; val startLabel: String; val endLabel: String
    if (isLive) {
        val s = programme?.startMs; val e = programme?.endMs
        frac = if (s != null && e != null && e > s) ((now - s).toFloat() / (e - s)).coerceIn(0f, 1f) else 0f
        startLabel = s?.let { EpgClock.hm(it) }.orEmpty(); endLabel = e?.let { EpgClock.hm(it) }.orEmpty()
    } else {
        frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        startLabel = fmt(pos); endLabel = if (dur > 0) fmt(dur) else ""
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xE00A0A0C)).padding(start = 96.design, end = 96.design, top = 40.design, bottom = 54.design),
        verticalArrangement = Arrangement.spacedBy(28.design),
    ) {
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
            Text(startLabel, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).height(28.design)) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(10.design).clip(RoundedCornerShape(5.design)).background(Ux.Line))
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(frac).height(10.design).clip(RoundedCornerShape(5.design)).background(Ux.Accent))
                Box(Modifier.align(Alignment.CenterStart).offset(x = maxWidth * frac - 14.design).size(28.design).clip(CircleShape).background(Color.White))
            }
            Text(endLabel, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.design), verticalAlignment = Alignment.CenterVertically) {
                if (!isLive) RoundButton(72, "M11 5L4 12l7 7M20 5l-7 7 7 7", onClick = { onSeek(-10_000) })
                PauseButton(playing, pauseFocus, onToggle)
                if (!isLive) RoundButton(72, "M13 5l7 7-7 7M4 5l7 7-7 7", onClick = { onSeek(10_000) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                OptionPill(D.pTracks, "M4 6h16M4 12h10M4 18h6", onTracks)
                OptionPill(D.pPlayer, "M3 5h18v12H3zM8 21h8M12 17v4", onOptions)
                OptionPill(D.pDisplay, "M3 5h18v14H3zM8 9h8v6H8z", onOptions)
                if (isLive) {
                    OptionPill(D.pRecord, "M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z", onRecord)
                    OptionPill(D.pChannels, "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01", onChannels)
                }
            }
        }
    }
    }
}

private fun fmt(ms: Long): String { val s = ms / 1000; val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60; return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec) }

@Composable
private fun RoundButton(sizePx: Int, icon: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, bg = Ux.Surface2, modifier = Modifier.size(sizePx.design)) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DIcon(icon, 30.design, if (f) Ux.TextOnLight else Ux.Text) }
    }
}

@Composable
private fun PauseButton(playing: Boolean, focus: FocusRequester, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, bg = Ux.Cta, modifier = Modifier.size(96.design).focusRequester(focus)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (playing) DIcon("M7 4h3.5v16H7zM13.5 4H17v16h-3.5z", 36.design, Ux.TextOnLight, fill = true) else DIcon("M7 4v16l13-8z", 36.design, Ux.TextOnLight, fill = true)
        }
    }
}

@Composable
private fun OptionPill(label: String, icon: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(32.design), bg = Ux.Surface2, ringWidth = 5.design, modifier = Modifier.height(64.design)) { f ->
        Row(Modifier.padding(horizontal = 28.design).height(64.design), verticalAlignment = Alignment.CenterVertically) {
            DIcon(icon, 24.design, if (f) Ux.TextOnLight else Ux.Text)
            Spacer(Modifier.width(12.design))
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
        }
    }
}

@Composable
private fun LoadingVisual(logo: String?, name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.design)) {
            LogoBox(logo, name, Modifier.width(320.design).height(200.design), radius = 24, pad = 24, bg = Ux.Surface)
            androidx.compose.material3.CircularProgressIndicator(color = Ux.Accent, strokeWidth = 5.design, modifier = Modifier.size(64.design))
        }
    }
}

@Composable
private fun ErrorPanel(kind: PlayErrorKind, canNext: Boolean, D: DesignStrings, onRetry: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val (title, hint) = when (kind) {
        PlayErrorKind.REFUSED -> D.errRefused to D.errRefusedHint
        PlayErrorKind.NETWORK -> D.errNetwork to null
        PlayErrorKind.FORMAT, PlayErrorKind.DECODER, PlayErrorKind.NO_PICTURE -> D.errFormat to null
        PlayErrorKind.NOT_FOUND -> D.errNotFound to null
        else -> D.errNoResponse to null
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xE60A0A0C)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.design), modifier = Modifier.widthIn(max = 1000.design)) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hint?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, maxLines = 2) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                PillButton(LocalDs.current.retry, onRetry, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(first))
                if (canNext) PillButton(D.nextChannel, onNext)
                PillButton(D.close, onClose)
            }
        }
    }
}

@Composable
private fun StatsCard(session: PlaybackSession, D: DesignStrings, modifier: Modifier) {
    var s by remember { mutableStateOf(session.engine?.stats()) }
    LaunchedEffect(Unit) { while (true) { s = session.engine?.stats(); delay(1_000) } }
    val st by session.state.collectAsState()
    Column(modifier.width(520.design).clip(RoundedCornerShape(20.design)).background(Color(0xE60F0F12)).padding(28.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
        Text(D.statsLabel.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, letterSpacing = 2.sp(), maxLines = 1)
        val x = s
        fun row(k: String, v: String?) { }
        listOf(
            D.engine to (if (st.combo.engine == EngineKind.EXO) D.engineExo else D.engineVlc),
            D.decoding to (if (x?.hardwareDecoding == true) D.hardware else D.software),
            "↔" to (x?.resolution ?: "—"), "▶" to (x?.videoCodec ?: "—") + (x?.frameRate?.let { " · %.0f fps".format(it) } ?: ""),
            "♪" to (x?.audioCodec ?: "—") + (x?.audioChannels?.let { " · ${it}ch" } ?: ""),
            D.bufferMemory to (x?.bufferedSeconds?.let { "$it s" } ?: "—"),
            "kbps" to (x?.videoBitrateKbps?.toString() ?: "—"), "⚠" to (x?.droppedFrames?.toString() ?: "—"),
        ).forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1)
                Text(v, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1)
            }
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

// ───────────────────────── Panneau de réglages (maquette LecteurReglages) ─────────────────────────

/** Ligne d'option de 64 px : repos #141418 ; choisie = fond #1C1C21 + liseré accent ; focus = blanc + anneau (encre en thème clair, mais le lecteur reste sombre). */
@Composable
private fun OptionRow(label: String, hint: String?, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = if (selected) Ux.Surface else Ux.SurfaceDeep, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(64.design)) { f ->
        Box(Modifier.fillMaxSize().then(if (selected && !f) Modifier.border(2.design, Ux.Accent, RoundedCornerShape(16.design)) else Modifier)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = if (f) Ux.TextOnLight else if (selected) Ux.Text else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!hint.isNullOrEmpty()) Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1)
            }
        }
    }
}

@Composable
private fun OptionGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        Text(title.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.spx, letterSpacing = 2.sp(), maxLines = 1)
        Column(verticalArrangement = Arrangement.spacedBy(8.design)) { content() }
    }
}

@Composable
private fun PlayerSidePanel(
    initial: SideTab, title: String, p: UserPrefs, vm: PlayerViewModel, session: PlaybackSession, state: com.ultratv.tv.nativeapp.ui.player.engine.SessionState,
    aspect: AspectMode, speed: Float, isLive: Boolean, statsOpen: Boolean, sleepActive: Boolean, D: DesignStrings,
    onAspect: (AspectMode) -> Unit, onSpeed: (Float) -> Unit, onStats: () -> Unit, onSleep: (Int) -> Unit, onSwitch: (Combo) -> Unit, onBuffer: (BufferPreset) -> Unit,
    onExternal: () -> Unit, onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf(initial) }
    val e = session.engine
    val audio = remember { e?.audioTracks().orEmpty() }
    val subs = remember { e?.subtitleTracks().orEmpty() }
    val labels = listOf(SideTab.TRACKS to D.pTracks, SideTab.DISPLAY to D.pDisplay, SideTab.PLAYER to D.pPlayer, SideTab.STATS to D.statsShort)
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.fillMaxHeight().width(640.design).background(Ux.Rail).border(1.design, Ux.Surface2).padding(horizontal = 56.design, vertical = 54.design),
            verticalArrangement = Arrangement.spacedBy(22.design),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.design)) {
                labels.forEach { (t, l) ->
                    val sel = t == tab
                    FocusSurface(onClick = { tab = t }, shape = RoundedCornerShape(26.design), bg = if (sel) Ux.Cta else Ux.Surface, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.height(52.design)) { f ->
                        Box(Modifier.height(52.design).padding(horizontal = 18.design), contentAlignment = Alignment.Center) {
                            Text(l, color = if (f || sel) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1)
                        }
                    }
                }
            }
            Text(D.forThisChannel(title), color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.design)) {
                when (tab) {
                    SideTab.TRACKS -> {
                        OptionGroup(D.audio) {
                            if (audio.isEmpty()) OptionRow("—", null, false) {}
                            audio.forEach { t -> OptionRow(t.label, null, t.selected) { e?.selectAudio(t.id); onClose() } }
                        }
                        OptionGroup(D.subtitles) {
                            OptionRow(D.off, null, subs.none { it.selected }) { e?.selectSubtitle(null); onClose() }
                            subs.forEach { t -> OptionRow(t.label, null, t.selected) { e?.selectSubtitle(t.id); onClose() } }
                        }
                    }
                    SideTab.DISPLAY -> {
                        OptionGroup(D.pDisplay) {
                            listOf(AspectMode.FIT to D.aspectFit, AspectMode.FILL to D.aspectFill, AspectMode.ZOOM to D.aspectZoom, AspectMode.R16_9 to "16:9", AspectMode.R4_3 to "4:3")
                                .forEach { (m, l) -> OptionRow(l, null, aspect == m) { onAspect(m) } }
                        }
                        if (!isLive) OptionGroup(D.speed) { listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { v -> OptionRow("${v}x", null, speed == v) { onSpeed(v) } } }
                        OptionGroup(D.sleepTimer) {
                            listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h").forEach { (m, l) -> OptionRow(l, null, false) { onSleep(m) } }
                            OptionRow(D.off, null, !sleepActive) { onSleep(0) }
                        }
                    }
                    SideTab.PLAYER -> {
                        val engineName = if (state.combo.engine == EngineKind.EXO) D.engineExo else D.engineVlc
                        OptionGroup(D.engine) {
                            OptionRow(D.auto, if (p.playerEngine == "auto") engineName else null, p.playerEngine == "auto") { vm.setEngine("auto") }
                            OptionRow(D.engineExo, null, p.playerEngine == "exo") { vm.setEngine("exo"); onSwitch(Combo(EngineKind.EXO, state.combo.decoder)) }
                            OptionRow(D.engineVlc, D.vlcHint, p.playerEngine == "vlc") { vm.setEngine("vlc"); onSwitch(Combo(EngineKind.VLC, state.combo.decoder)) }
                        }
                        OptionGroup(D.decoding) {
                            OptionRow(D.auto, if (p.decoderMode == "auto") (if (state.combo.decoder == DecoderMode.SOFTWARE) D.software else D.hardware) else null, p.decoderMode == "auto") { vm.setDecoder("auto"); onSwitch(Combo(state.combo.engine, DecoderMode.AUTO)) }
                            OptionRow(D.hardware, null, p.decoderMode == "hw") { vm.setDecoder("hw"); onSwitch(Combo(state.combo.engine, DecoderMode.HARDWARE)) }
                            OptionRow(D.software, D.softwareHint, p.decoderMode == "sw") { vm.setDecoder("sw"); onSwitch(Combo(state.combo.engine, DecoderMode.SOFTWARE)) }
                        }
                        OptionGroup(D.bufferMemory) {
                            listOf(BufferPreset.AUTO to D.auto, BufferPreset.LOW_LATENCY to D.bufLow, BufferPreset.BALANCED to D.bufBalanced, BufferPreset.STABLE to D.bufStable)
                                .forEach { (b, l) -> OptionRow(l, null, state.bufferPreset == b) { onBuffer(b) } }
                        }
                        OptionRow(LocalStrings.current.playerExternal, null, false, onExternal)
                    }
                    SideTab.STATS -> {
                        OptionRow(D.statsLabel + " · " + D.onVideo, null, statsOpen, onStats)
                        StatsRows(session, D)
                    }
                }
            }
            val backToAuto: () -> Unit = {
                vm.setEngine("auto"); vm.setDecoder("auto"); onBuffer(BufferPreset.AUTO)
                onSwitch(Combo(state.combo.engine, DecoderMode.AUTO))
            }
            if (tab == SideTab.PLAYER) PillButton(D.backToAuto, onClick = backToAuto, heightPx = 64, hPadPx = 36, fontPx = 22, weight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            else PillButton(D.close, onClose, heightPx = 64, hPadPx = 36, fontPx = 22, weight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatsRows(session: PlaybackSession, D: DesignStrings) {
    var s by remember { mutableStateOf(session.engine?.stats()) }
    LaunchedEffect(Unit) { while (true) { s = session.engine?.stats(); delay(1_000) } }
    val st by session.state.collectAsState()
    val x = s
    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        listOf(
            D.engine to (if (st.combo.engine == EngineKind.EXO) D.engineExo else D.engineVlc),
            D.decoding to (if (x?.hardwareDecoding == true) D.hardware else D.software),
            D.statResolution to (x?.resolution ?: "—") + (x?.frameRate?.let { " · %.0f".format(it) } ?: ""),
            D.statCodec to (x?.videoCodec ?: "—"),
            D.statAudio to (x?.audioCodec ?: "—") + (x?.audioChannels?.let { " · ${it}ch" } ?: ""),
            D.bufferMemory to (x?.bufferedSeconds?.let { "$it s" } ?: "—"),
            D.statBitrate to (x?.videoBitrateKbps?.let { "%.1f Mb/s".format(it / 1000.0) } ?: "—"),
            D.statDropped to (x?.droppedFrames?.toString() ?: "—"),
        ).forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = Ux.Text3, fontFamily = Manrope, fontSize = 19.spx, maxLines = 1)
                Text(v, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 19.spx, maxLines = 1)
            }
        }
    }
}

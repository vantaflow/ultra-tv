package com.ultratv.tv.nativeapp.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.repo.CategoryManager
import com.ultratv.tv.nativeapp.data.repo.CategoryRow
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.leaveOnVerticalDpad
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.KeyHint
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Switch
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    providers: ProviderRepository,
    private val manager: CategoryManager,
) : ViewModel() {
    val kind = MutableStateFlow("LIVE")
    val query = MutableStateFlow("")
    private val pid = providers.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()
    val providerList = providers.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<CategoryRow>> = combine(pid, kind, query.debounce(150)) { p, k, q -> Triple(p, k, q) }
        .flatMapLatest { (p, k, q) -> if (p == null) flowOf(emptyList()) else manager.observe(p, k, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Compteurs des onglets (nombre de catégories par type). */
    val tabCounts: StateFlow<Map<String, Int>> = pid.flatMapLatest { p ->
        if (p == null) flowOf(emptyMap()) else combine(manager.observeTotals(p, "LIVE"), manager.observeTotals(p, "MOVIE"), manager.observeTotals(p, "SERIES")) { a, b, c -> mapOf("LIVE" to a, "MOVIE" to b, "SERIES" to c) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private suspend fun providerId(): Long? = providerList.value.let { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }

    fun toggle(r: CategoryRow) { viewModelScope.launch { providerId()?.let { manager.setEnabled(it, kind.value, listOf(r.remoteId), !r.enabled) } } }
    /** « Tout activer » / « Tout désactiver » : s'applique au résultat FILTRÉ (ex. filtrer « AR » puis tout désactiver). */
    fun setAll(enabled: Boolean) { viewModelScope.launch { providerId()?.let { manager.setEnabled(it, kind.value, rows.value.map { r -> r.remoteId }, enabled) } } }
    fun saveOrder(ids: List<String>) { viewModelScope.launch { providerId()?.let { manager.saveOrder(it, kind.value, ids) } } }
    val filterUnsupported: StateFlow<Boolean> = providerList.map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.categoryFilter == 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

/** Gérer les catégories (maquette Categories.dc.html) : onglets, filtre, Tout activer / désactiver, une ligne par catégorie (switch + ordre). */
@Composable
fun CategoriesScreen(onBack: () -> Unit = {}, vm: CategoriesViewModel = hiltViewModel()) {
    val D = LocalDs.current
    val kind by vm.kind.collectAsState()
    val q by vm.query.collectAsState()
    val rows by vm.rows.collectAsState()
    val tabs by vm.tabCounts.collectAsState()
    val unsupported by vm.filterUnsupported.collectAsState()
    // Réordonnancement : on sélectionne une ligne (poignée « Ordre »), ▲▼ la déplacent, OK valide.
    var moving by remember { mutableStateOf<String?>(null) }
    var local by remember(rows) { mutableStateOf(rows) }
    val shown = if (moving != null) local else rows
    BackHandler(enabled = moving != null) { moving = null; local = rows }

    Column(Modifier.fillMaxSize().padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design), verticalArrangement = Arrangement.spacedBy(24.design)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DIcon("M15 6l-6 6 6 6", 20.design, Ux.Text3, strokeWidth = 2.5f)
                    Spacer(Modifier.width(8.design))
                    Text(D.settingsTitle, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1)
                }
                Text(D.manageCategories, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                for ((k, label) in listOf("LIVE" to D.directTitle, "MOVIE" to D.moviesTitle, "SERIES" to D.seriesTitle)) {
                    val sel = kind == k
                    FocusSurface(onClick = { vm.kind.value = k; moving = null }, shape = RoundedCornerShape(28.design), bg = if (sel) Ux.Cta else Ux.Surface, modifier = Modifier.height(56.design)) { f ->
                        Box(Modifier.padding(horizontal = 28.design).height(56.design), contentAlignment = Alignment.Center) {
                            Text("$label · ${tabs[k] ?: 0}", color = if (f || sel) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.design), verticalAlignment = Alignment.CenterVertically) {
            FilterField(q, { vm.query.value = it }, D.filterHint, Modifier.weight(1f))
            PillButton(D.enableAll, { vm.setAll(true) }, heightPx = 64, hPadPx = 26, fontPx = 20, weight = FontWeight.Bold, bg = Ux.Surface)
            PillButton(D.disableAll, { vm.setAll(false) }, heightPx = 64, hPadPx = 26, fontPx = 20, weight = FontWeight.Bold, bg = Ux.Surface)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.design)) {
            Text("${D.categoryCol} · ${shown.size}", color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, letterSpacing = 1.4.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Box(Modifier.width(180.design), contentAlignment = Alignment.Center) { Text(D.activeCol, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, letterSpacing = 1.4.sp, maxLines = 1) }
            Spacer(Modifier.width(12.design))
            Box(Modifier.width(110.design), contentAlignment = Alignment.Center) { Text(D.orderCol, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, letterSpacing = 1.4.sp, maxLines = 1) }
        }
        val state = rememberLazyListState()
        val firstRow = remember { androidx.compose.ui.focus.FocusRequester() }
        var firstFocused by remember { mutableStateOf(false) }
        com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus(firstRow, hasFocus = { firstFocused }, key = shown.isNotEmpty())
        LazyColumn(Modifier.weight(1f), state = state, verticalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(vertical = 6.design)) {
            items(shown, key = { it.remoteId }, contentType = { "cat" }) { r ->
                CategoryLine(
                    r, kind, rowModifier = if (r.remoteId == shown.firstOrNull()?.remoteId) Modifier.focusRequester(firstRow).onFocusChanged { firstFocused = it.isFocused } else Modifier,
                    isMoving0 = null, isMoving = moving == r.remoteId, D = D,
                    onToggle = { vm.toggle(r) },
                    onStartMove = { moving = r.remoteId; local = rows },
                    onMove = { delta ->
                        val i = local.indexOfFirst { it.remoteId == r.remoteId }; val j = (i + delta).coerceIn(0, local.lastIndex)
                        if (i >= 0 && i != j) local = local.toMutableList().also { l -> l.add(j, l.removeAt(i)) }
                    },
                    onConfirmMove = { vm.saveOrder(local.map { it.remoteId }); moving = null },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (unsupported) D.noFilterSupport else D.categoriesFooter, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(24.design))
            if (moving != null) Text(D.moveHint, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1) else Row(horizontalArrangement = Arrangement.spacedBy(28.design)) { KeyHint("OK", D.toggle); KeyHint("OK ▸", D.holdToReorder) }
        }
    }
}

/**
 * Champ de filtre : un bouton tant qu'on ne l'édite pas (le clavier TV ne surgit donc JAMAIS au simple passage du focus) ;
 * OK ouvre la saisie, « Terminé » (IME) la referme.
 */
@Composable
private fun FilterField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    var editing by remember { mutableStateOf(false) }
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(editing) { if (editing) runCatching { requester.requestFocus() } }
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    if (!editing) {
        FocusSurface(onClick = { editing = true }, shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, ringWidth = 3.design, focusedScale = 1.0f, focusedBg = Ux.SurfaceDeep, modifier = modifier.height(64.design)) { f ->
            Box(Modifier.fillMaxSize().padding(horizontal = 24.design), contentAlignment = Alignment.CenterStart) {
                Text(value.ifEmpty { hint }, color = if (value.isEmpty()) Ux.Text3 else Ux.Text, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        Box(modifier.height(64.design).clip(RoundedCornerShape(16.design)).background(Ux.SurfaceDeep).border(3.design, Ux.Accent, RoundedCornerShape(16.design)).padding(horizontal = 24.design), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Ux.Text, fontFamily = Manrope, fontSize = 22.spx), cursorBrush = SolidColor(Ux.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrect = false),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { editing = false; fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth().focusRequester(requester).leaveOnVerticalDpad().onFocusChanged { if (!it.isFocused && editing) editing = false },
                decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1); inner() },
            )
        }
    }
}

/** Ligne de 76 px : badge, nom + compteur, interrupteur « Active », poignée d'ordre. Grisée si désactivée ; blanche + anneau au focus. */
@Composable
private fun CategoryLine(
    r: CategoryRow, kind: String, rowModifier: Modifier = Modifier, isMoving0: Boolean? = null, isMoving: Boolean, D: com.ultratv.tv.nativeapp.i18n.DesignStrings,
    onToggle: () -> Unit, onStartMove: () -> Unit, onMove: (Int) -> Unit, onConfirmMove: () -> Unit,
) {
    val count = r.count?.let { when (kind) { "LIVE" -> D.channelsOf(it); "MOVIE" -> D.moviesOf(it); else -> D.seriesOf(it) } }.orEmpty()
    FocusSurface(
        onClick = { if (isMoving) onConfirmMove() else onToggle() },
        onLongClick = { if (!isMoving) onStartMove() },
        shape = RoundedCornerShape(18.design),
        bg = if (r.enabled) Ux.SurfaceDeep else Ux.Rail, ringWidth = 5.design, focusedScale = 1.0f,
        modifier = rowModifier.fillMaxWidth().height(76.design).onPreviewKeyEvent { e ->
            if (!isMoving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) { Key.DirectionUp -> { onMove(-1); true }; Key.DirectionDown -> { onMove(1); true }; else -> false }
        },
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.defaultMinSize56().height(36.design).clip(RoundedCornerShape(10.design)).background(if (f) Color(0xFF0A0A0C) else Ux.Surface2).padding(horizontal = 8.design), contentAlignment = Alignment.Center) {
                    Text(r.badge.orEmpty(), color = if (f) Color.White else Color(0xFFE4E4E7), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.spx, letterSpacing = 0.6.sp, maxLines = 1)
                }
                Spacer(Modifier.width(16.design))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                    Text(r.label, color = if (f) Ux.TextOnLight else if (r.enabled) Ux.Text else Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
                    Text(count, color = if (f) Ux.OnFocus2 else if (r.enabled) Ux.Text3 else Ux.Muted2, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1)
                }
            }
            Box(Modifier.width(180.design), contentAlignment = Alignment.Center) { Switch(r.enabled, inverted = f) }
            Spacer(Modifier.width(12.design))
            Box(Modifier.width(110.design).clip(RoundedCornerShape(12.design)).then(if (isMoving) Modifier.background(Ux.Accent) else Modifier).padding(vertical = 8.design), contentAlignment = Alignment.Center) {
                DIcon("M8 10l4-4 4 4M8 14l4 4 4-4", 26.design, if (isMoving) Color.White else if (f) Ux.OnFocus2 else Ux.Text3, strokeWidth = 2.2f)
            }
        }
    }
}

private fun Modifier.defaultMinSize56() = this.then(defaultMinSize(minWidth = 56.design))


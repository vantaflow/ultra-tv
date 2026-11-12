package com.ultratv.tv.nativeapp.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
import com.ultratv.tv.nativeapp.ui.player.osd.liveOsdStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Le lecteur reste sombre quel que soit le thème : la feuille utilise toujours la palette sombre. */
private val P = DarkPalette

/**
 * Liste des chaînes dans le lecteur (maquette « Chaînes ») : feuille flottante à droite, titre + onglets Chaînes / Favoris,
 * colonne des catégories et liste des chaînes (numéro, logo, nom, programme en cours, progression, « EN COURS »).
 * La vidéo continue derrière. Logique inchangée (voir [LiveDrawerLogic]) : la catégorie qui prend le focus se charge sans OK,
 * « Toutes les chaînes » en tête, ◀ ▶ entre les colonnes, OK zappe, Retour ferme, le focus s'ouvre sur la chaîne regardée.
 */
@Composable
internal fun LiveDrawer(
    vm: PlayerViewModel,
    onPick: (com.ultratv.tv.nativeapp.data.db.ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries by vm.queue.collectAsState()
    val rawCats by vm.categories.collectAsState()
    val activeCat by vm.activeCategory.collectAsState()
    val pending by vm.pendingCategory.collectAsState()
    val totals by vm.drawerTotals.collectAsState()
    val D = LocalDs.current
    val O = liveOsdStrings()
    // « Toutes les chaînes » en tête, puis Favoris (s'il y en a), puis les catégories.
    val cats = remember(rawCats, totals, D.lang) { LiveDrawerLogic.categories(O.drawerAll, D.catFavorites, totals.all, totals.favorites, rawCats) }
    val current = entries.firstOrNull { it.isCurrent }
    // Catégorie de la chaîne regardée à l'ouverture : celle dont la liste est affichée tant que rien n'a été parcouru.
    val initialCat = remember(current != null) { current?.channel?.categoryId }
    val displayedCat = activeCat ?: initialCat
    // Catégorie mise en avant : celle qui a le focus (demandée, liste en route), sinon celle de la liste affichée.
    val currentCat = pending ?: displayedCat
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // À l'ouverture, le focus va sur la chaîne regardée (OK = zapper tout de suite, ▲▼ = parcourir autour).
    // Entrer dans la liste depuis une catégorie : même cible (la chaîne en cours, sinon la première).
    val entryFocus = remember { FocusRequester() }
    val focusIdx = LiveDrawerLogic.entryFocusIndex(entries.indexOfFirst { it.isCurrent }, entries.size)
    val catFocus = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    // Colonne des catégories repliée tant qu'on parcourt les chaînes (la vidéo reste visible) ; Gauche la déplie.
    var catsOpen by remember { mutableStateOf(false) }
    val catListState = rememberLazyListState()
    var enterWhenReady by remember { mutableStateOf(false) }
    // Garde-fou si la chaîne en cours est introuvable (aucun focus initial) : les catégories chargent après 600 ms.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(600); armed = true }
    fun enterList() {
        val idx = focusIdx ?: return
        scope.launch {
            listState.scrollToItem((idx - 2).coerceAtLeast(0))
            repeat(10) { if (runCatching { entryFocus.requestFocus() }.isSuccess) return@launch; delay(30) }
        }
    }
    // Totaux (Toutes les chaînes, Favoris) relus à chaque ouverture ; à la fermeture, la liste parcourue est oubliée
    // (au retour on retrouve la file de zapping, comme avant).
    LaunchedEffect(Unit) { vm.refreshDrawerTotals() }
    DisposableEffect(Unit) { onDispose { vm.resetBrowse() } }
    // OK / Droite demandés pendant le chargement : on entre dans la liste dès qu'elle est arrivée.
    LaunchedEffect(enterWhenReady, pending) { if (enterWhenReady && pending == null) { enterWhenReady = false; enterList() } }
    // Lignes sans programme (guide complet absent sur la box) : on complète les premières à l'ouverture.
    LaunchedEffect(entries.firstOrNull()?.channel?.id) {
        val start = (entries.indexOfFirst { it.isCurrent }.takeIf { it >= 0 } ?: 0)
        vm.fillProgrammes(entries.drop((start - 4).coerceAtLeast(0)).take(12).filter { it.now == null }.map { it.channel })
    }
    LaunchedEffect(entries.size, current?.channel?.id, activeCat) {
        // Liste de la chaîne en cours : centrée sur elle ; une autre catégorie (sans elle) repart du haut.
        val at = current?.let { c -> entries.indexOf(c).takeIf { it >= 0 } }
        listState.scrollToItem(if (at != null) (at - 2).coerceAtLeast(0) else 0)
        if (!focusedOnce && current != null) repeat(20) {
            if (runCatching { entryFocus.requestFocus() }.isSuccess) { focusedOnce = true; return@LaunchedEffect }
            kotlinx.coroutines.delay(50)
        }
    }
    BackHandler { onDismiss() }
    val rows = remember(entries) {
        val t = System.currentTimeMillis()
        entries.mapIndexed { k, e -> e.toRow(k + 1, t) }
    }
    ModalDark {
        DrawerSheet(
            title = D.pChannels, channelsTab = D.pChannels, favoritesTab = D.catFavorites,
            favoritesSelected = currentCat == CATEGORY_FAVORITES,
            // Onglets : « Favoris » sélectionne la catégorie Favoris existante, « Chaînes » revient à « Toutes les chaînes ».
            onTab = { fav -> vm.browse(if (fav) CATEGORY_FAVORITES else CATEGORY_ALL) },
            cats = cats, highlightId = currentCat, rtl = rtl, catFocus = catFocus,
            // La catégorie qui reçoit le focus charge ses chaînes tout de suite (anti-rebond dans le ViewModel).
            // Pas avant que le focus initial ait gagné la chaîne en cours : l'ouverture ne doit pas charger « Toutes les chaînes ».
            onCatFocused = { id -> enterWhenReady = false; if ((focusedOnce || armed) && LiveDrawerLogic.shouldBrowse(id, displayedCat)) vm.browse(id) },
            onCatEnter = { if (pending == null) enterList() else enterWhenReady = true },
            rows = rows, listState = listState,
            rowModifier = { k ->
                val back = Modifier.onPreviewKeyEvent { ev ->
                    // Retour de la liste vers la catégorie en cours (Gauche ; Droite en lecture de droite à gauche).
                    if (ev.key == (if (rtl) Key.DirectionRight else Key.DirectionLeft)) {
                        if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) {
                            catsOpen = true
                            scope.launch {
                                // La colonne vient d'être dépliée : on l'amène sur la catégorie en cours, puis le focus s'y pose.
                                val at = cats.indexOfFirst { it.id == currentCat }
                                if (at >= 0) runCatching { catListState.scrollToItem((at - 3).coerceAtLeast(0)) }
                                repeat(15) { if (runCatching { catFocus.requestFocus() }.isSuccess) return@launch; delay(30) }
                            }
                        }
                        true
                    } else false
                }
                if (k == focusIdx) back.focusRequester(entryFocus) else back
            },
            showCats = catsOpen || rows.isEmpty(), catState = catListState,
            onRowFocus = { k -> catsOpen = false; entries.getOrNull(k)?.let { e -> if (e.now == null) vm.fillProgrammes(listOf(e.channel)) } },
            onRowClick = { k -> entries.getOrNull(k)?.let { onPick(it.channel) } },
            onAirLabel = D.onAirPill, emptyText = if (rows.isEmpty() && currentCat == CATEGORY_FAVORITES && pending == null) D.noFavorites else null,
        )
    }
}

private fun PlayerViewModel.DrawerEntry.toRow(number: Int, nowMs: Long): DrawerRowUi {
    val n = now
    val fraction = if (n != null && n.endMs > n.startMs) ((nowMs - n.startMs).toFloat() / (n.endMs - n.startMs)).coerceIn(0f, 1f) else 0f
    return DrawerRowUi(
        key = channel.id, number = number, logo = channel.logo, name = channel.title,
        now = n?.let { listOf(it.title, EpgClock.hm(it.startMs)).filter { s -> s.isNotBlank() }.joinToString(" · ") },
        fraction = fraction, onAir = isCurrent,
    )
}

/** Une ligne de la liste des chaînes, déjà prête à l'affichage (pure : testable sans base ni ViewModel). */
internal data class DrawerRowUi(val key: Long, val number: Int, val logo: String?, val name: String, val now: String?, val fraction: Float, val onAir: Boolean)

/**
 * Feuille de la liste des chaînes : sans état, tout vient des paramètres (le tiroir ci-dessus porte la logique, les captures
 * de test construisent directement ces paramètres).
 */
@Composable
internal fun DrawerSheet(
    title: String, channelsTab: String, favoritesTab: String, favoritesSelected: Boolean, onTab: (Boolean) -> Unit,
    cats: List<DrawerCategory>, highlightId: String?, rtl: Boolean, catFocus: FocusRequester,
    onCatFocused: (String) -> Unit, onCatEnter: () -> Unit,
    rows: List<DrawerRowUi>, listState: LazyListState, rowModifier: (Int) -> Modifier,
    onRowFocus: (Int) -> Unit, onRowClick: (Int) -> Unit, onAirLabel: String, emptyText: String?,
    modifier: Modifier = Modifier,
    showCats: Boolean = true, catState: LazyListState = rememberLazyListState(),
) {
    // Feuille flottante à gauche (côté début), marge de 32 px ; angles 32. Fond translucide : la chaîne regardée reste
    // visible derrière ; sans la colonne des catégories (repliée pendant le parcours des chaînes), la feuille est étroite.
    Box(modifier.fillMaxSize().padding(32.design), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier.width(if (showCats) 860.design else 580.design).fillMaxHeight().clip(RoundedCornerShape(32.design)).background(P.rail.copy(alpha = 0.84f))
                .padding(start = 28.design, end = 28.design, top = 32.design, bottom = 24.design),
            verticalArrangement = Arrangement.spacedBy(22.design),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = P.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 38.spx, maxLines = 1)
                Row(Modifier.clip(CircleShape).background(Color(0xFF1A1E27)).padding(6.design), horizontalArrangement = Arrangement.spacedBy(6.design)) {
                    DrawerTab(channelsTab, selected = !favoritesSelected) { onTab(false) }
                    DrawerTab(favoritesTab, selected = favoritesSelected) { onTab(true) }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.design)) {
                if (showCats) DrawerCategoryColumn(
                    cats = cats, highlightId = highlightId, rtl = rtl, selectedFocus = catFocus,
                    onFocused = onCatFocused, onEnter = onCatEnter, modifier = Modifier.width(260.design).fillMaxHeight(), state = catState,
                )
                if (rows.isEmpty() && emptyText != null) {
                    Text(emptyText, color = P.text3, fontFamily = Manrope, fontSize = 22.spx, modifier = Modifier.weight(1f).padding(top = 12.design))
                } else LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState, verticalArrangement = Arrangement.spacedBy(8.design)) {
                    itemsIndexed(rows, key = { _, r -> r.key }) { k, r ->
                        ChannelRow(r, onAirLabel, onFocus = { onRowFocus(k) }, onClick = { onRowClick(k) }, modifier = rowModifier(k))
                    }
                }
            }
        }
    }
}

/** Onglet segmenté : sélectionné = fond clair (focus) ; l'anneau accent signale le focus quand il est sur l'onglet. */
@Composable
private fun DrawerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = CircleShape, bg = if (selected) P.focusFill else Color.Transparent, focusedBg = if (selected) P.focusFill else P.tile,
        focusedScale = 1f, ringWidth = 3.design, modifier = Modifier.height(48.design),
    ) { f ->
        Box(Modifier.height(48.design).padding(horizontal = 26.design), contentAlignment = Alignment.Center) {
            Text(
                label, color = if (selected) P.focusText else if (f) P.text else P.text2, fontFamily = Manrope,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 19.spx, maxLines = 1,
            )
        }
    }
}

/**
 * Colonne des catégories de la feuille. Le focus D-pad sur une catégorie la signale ([onFocused] : le tiroir charge alors ses
 * chaînes sans attendre un OK) ; Droite (Gauche en lecture de droite à gauche) ou OK passe dans la liste des chaînes ([onEnter]).
 * [selectedFocus] est posé sur la catégorie [highlightId] : la liste y revient avec Gauche.
 */
@Composable
internal fun DrawerCategoryColumn(
    cats: List<DrawerCategory>, highlightId: String?, rtl: Boolean, selectedFocus: FocusRequester,
    onFocused: (String) -> Unit, onEnter: () -> Unit, modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(modifier, state = state, verticalArrangement = Arrangement.spacedBy(6.design)) {
        items(cats, key = { it.id }) { c ->
            val sel = c.id == highlightId
            val enterKey = if (rtl) Key.DirectionLeft else Key.DirectionRight
            val base = Modifier.fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused(c.id) }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == enterKey) { onEnter(); true } else false
                }
            // Catégorie choisie = teinte « en cours » ; focus = fond clair (palette de focus), sans anneau ni zoom.
            FocusSurface(
                onClick = onEnter, shape = RoundedCornerShape(16.design), bg = if (sel) P.nowTint else Color.Transparent, focusedBg = P.focusFill,
                ringWidth = 0.dp, focusedScale = 1f, modifier = if (sel) base.focusRequester(selectedFocus) else base,
            ) { f ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.design).padding(horizontal = 18.design, vertical = 10.design),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design),
                ) {
                    Text(
                        c.label, color = if (f) P.focusText else if (sel) P.accentText else P.text2, fontFamily = Manrope,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 20.spx, lineHeight = 24.spx,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    c.count?.let { Text("$it", color = if (f) P.focusText2 else if (sel) P.accentText else P.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun ModalDark(content: @Composable () -> Unit) = com.ultratv.tv.nativeapp.ui.common.ModalFocusScope(onBack = null, modifier = Modifier.background(Color(0x33000000))) { Box(Modifier.fillMaxSize()) { content() } }

@Composable
private fun ChannelRow(r: DrawerRowUi, onAirLabel: String, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(20.design), bg = P.card, focusedBg = P.focusFill, focusedScale = 1f, ringWidth = 0.dp,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
    ) { f ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.design, vertical = 12.design),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design),
        ) {
            Text("${r.number}", color = if (f) P.focusText else P.text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 24.spx, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(44.design))
            LogoBox(r.logo, r.name, Modifier.size(56.design), radius = 14, pad = 6, bg = if (f) P.focusText else P.tile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(r.name, color = if (f) P.focusText else P.text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.now.orEmpty(), color = if (f) P.focusText2 else P.text3, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Progression 4 px : fond translucide, remplissage accent.
                Box(Modifier.fillMaxWidth().height(4.design).clip(RoundedCornerShape(2.design)).background(if (f) Color(0x260A0C10) else Color(0x1AF4F5F7))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(r.fraction.coerceIn(0f, 1f)).background(Ux.Accent))
                }
            }
            if (r.onAir) Text(
                onAirLabel, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 14.spx, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.clip(CircleShape).background(Ux.Accent).padding(horizontal = 12.design, vertical = 6.design),
            )
        }
    }
}

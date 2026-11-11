package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.ultratv.tv.nativeapp.ui.player.osd.liveOsdStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Guide dans le lecteur (maquette LecteurGuide) : à gauche, catégories + chaînes de la catégorie (numéro, logo, nom,
 * progression, programme, « EN COURS ») ; en bas à droite, le programme de la chaîne sélectionnée. La vidéo continue
 * derrière. Le lecteur reste sombre quel que soit le thème.
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
    var selected by remember { mutableStateOf<PlayerViewModel.DrawerEntry?>(null) }
    val shown = selected ?: current ?: entries.firstOrNull()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // À l'ouverture, le focus va sur la chaîne regardée (OK = zapper tout de suite, ▲▼ = parcourir autour).
    // Entrer dans la liste depuis une catégorie : même cible (la chaîne en cours, sinon la première).
    val entryFocus = remember { FocusRequester() }
    val focusIdx = LiveDrawerLogic.entryFocusIndex(entries.indexOfFirst { it.isCurrent }, entries.size)
    val catFocus = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
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
    ModalDark {
        Row(Modifier.fillMaxSize()) {
            Row(
                Modifier.width(1100.design).fillMaxHeight().background(Color(0xF00A0A0C)).padding(start = 96.design, top = 54.design, end = 40.design, bottom = 54.design),
                horizontalArrangement = Arrangement.spacedBy(32.design),
            ) {
                Column(Modifier.width(300.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
                    Label(D.categoriesLabel, Modifier.padding(bottom = 8.design))
                    DrawerCategoryColumn(
                        cats = cats, highlightId = currentCat, rtl = rtl, selectedFocus = catFocus,
                        // La catégorie qui reçoit le focus charge ses chaînes tout de suite (anti-rebond dans le ViewModel).
                        // Pas avant que le focus initial ait gagné la chaîne en cours : l'ouverture ne doit pas charger « Toutes les chaînes ».
                        onFocused = { id -> enterWhenReady = false; if ((focusedOnce || armed) && LiveDrawerLogic.shouldBrowse(id, displayedCat)) vm.browse(id) },
                        onEnter = { if (pending == null) enterList() else enterWhenReady = true },
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.design)) {
                    val cat = cats.firstOrNull { it.id == currentCat }
                    val title = cat?.label ?: D.channelsWord
                    // Vrai nombre de la catégorie (jamais 0 pendant le chargement), pas la taille de la liste affichée.
                    val count = LiveDrawerLogic.counter(entries.size, cat?.count, listIsCategory = pending != null || activeCat != null, loading = pending != null)
                    Label("${title.uppercase()} · $count", Modifier.padding(bottom = 8.design))
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.design)) {
                        itemsIndexed(entries, key = { _, it -> it.channel.id }) { k, e ->
                            val back = Modifier.onPreviewKeyEvent { ev ->
                                // Retour de la liste vers la catégorie en cours (Gauche ; Droite en lecture de droite à gauche).
                                if (ev.type == KeyEventType.KeyDown && ev.key == (if (rtl) Key.DirectionRight else Key.DirectionLeft))
                                    runCatching { catFocus.requestFocus() }.isSuccess
                                else false
                            }
                            ChannelLine(e, index = k + 1, variant = run { var n = 1; var j = k - 1; while (j >= 0 && n < 9 && com.ultratv.tv.nativeapp.ui.live.sameStream(entries[j].channel, e.channel)) { n++; j-- }; n }, D = D, onFocus = { selected = e; if (e.now == null) vm.fillProgrammes(listOf(e.channel)) }, onClick = { onPick(e.channel) },
                                modifier = if (k == focusIdx) back.focusRequester(entryFocus) else back)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomStart) {
                Column(Modifier.fillMaxWidth().background(Color(0xE00A0A0C)).padding(start = 48.design, end = 96.design, top = 32.design, bottom = 54.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
                    Label(D.selectedChannel, color = Ux.AccentInk)
                    Text(shown?.now?.title ?: shown?.channel?.title.orEmpty(), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    shown?.now?.let { n ->
                        Text(
                            "${EpgClock.range(n.startMs, n.endMs)}" + (shown.next?.let { " · ${LocalStrings.current.liveThen} ${it.title}" } ?: ""),
                            color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(O.drawerHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, modifier = Modifier.padding(top = 6.design))
                }
            }
        }
    }
}

/**
 * Colonne des catégories du tiroir. Le focus D-pad sur une catégorie la signale ([onFocused] : le tiroir charge alors ses
 * chaînes sans attendre un OK) ; Droite (Gauche en lecture de droite à gauche) ou OK passe dans la liste des chaînes ([onEnter]).
 * [selectedFocus] est posé sur la catégorie [highlightId] : la liste y revient avec Gauche.
 */
@Composable
internal fun DrawerCategoryColumn(
    cats: List<DrawerCategory>, highlightId: String?, rtl: Boolean, selectedFocus: FocusRequester,
    onFocused: (String) -> Unit, onEnter: () -> Unit, modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.design)) {
        items(cats, key = { it.id }) { c ->
            val sel = c.id == highlightId
            val enterKey = if (rtl) Key.DirectionLeft else Key.DirectionRight
            val base = Modifier.fillMaxWidth().height(56.design)
                .onFocusChanged { if (it.isFocused) onFocused(c.id) }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == enterKey) { onEnter(); true } else false
                }
            FocusSurface(onClick = onEnter, shape = RoundedCornerShape(14.design), bg = if (sel) Ux.Surface2 else Color.Transparent, ringWidth = 4.design, focusedScale = 1f, modifier = if (sel) base.focusRequester(selectedFocus) else base) { f ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    if (sel && !f) Box(Modifier.width(4.design).fillMaxHeight().background(Ux.Accent)) else Box(Modifier.width(4.design))
                    Text(c.label, color = if (f) Ux.TextOnLight else if (sel) Ux.White else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 14.design))
                    c.count?.let { Text("$it", color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1, modifier = Modifier.padding(end = 14.design)) }
                }
            }
        }
    }
}

@Composable
private fun ModalDark(content: @Composable () -> Unit) = com.ultratv.tv.nativeapp.ui.common.ModalFocusScope(onBack = null, modifier = Modifier.background(Color(0x66000000))) { Box(Modifier.fillMaxSize()) { content() } }

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, color: Color = Ux.Text3) =
    Text(text, color = color, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, letterSpacing = TextUnit(1.8f, TextUnitType.Sp), maxLines = 1, modifier = modifier)

@Composable
private fun ChannelLine(e: PlayerViewModel.DrawerEntry, index: Int, variant: Int = 1, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = e.now
    val fraction = if (now != null && now.endMs > now.startMs) ((System.currentTimeMillis() - now.startMs).toFloat() / (now.endMs - now.startMs)).coerceIn(0f, 1f) else 0f
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, ringWidth = 4.design, focusedScale = 1f, modifier = modifier.fillMaxWidth().height(96.designT).onFocusChanged { if (it.isFocused) onFocus() }) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 18.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            Text("$index", color = if (f) Ux.OnFocus2 else Ux.Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, modifier = Modifier.width(48.design))
            LogoBox(e.channel.logo, e.channel.title, Modifier.width(64.design).height(42.design), radius = 8, pad = 4, bg = if (f) Ux.Surface else Ux.Surface2)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(e.channel.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressLine(fraction, Modifier.fillMaxWidth().clip(RoundedCornerShape(2.design)), heightPx = 4, track = if (f) Ux.OnFocus2.copy(alpha = 0.4f) else Ux.Surface2)
                Text(now?.title.orEmpty(), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            com.ultratv.tv.nativeapp.ui.live.StreamBadges(e.channel.quality, e.channel.flags, f, variant)
            if (e.isCurrent) Text(D.onAirPill, color = if (f) Ux.TextOnLight else Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, letterSpacing = TextUnit(1.3f, TextUnitType.Sp), maxLines = 1)
        }
    }
}

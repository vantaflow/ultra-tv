package com.ultratv.tv.nativeapp.ui.components

import kotlinx.coroutines.launch
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.mobile.navigateTopLevel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.sportNav
import com.ultratv.tv.nativeapp.ui.common.LocalLowRam
import com.ultratv.tv.nativeapp.ui.design.Manrope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoMark
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/** Groupes du rail (maquette « Menu latéral ») : contenus, listes de l'utilisateur, puis compte et réglages en bas. */
internal enum class RailGroup { WATCH, MINE, FOOT }

internal data class RailItem(
    val route: String,
    val icon: String,
    /** Libellé pris dans [com.ultratv.tv.nativeapp.i18n.DesignStrings] (prioritaire sur [label] quand il est fourni). */
    val labelD: ((com.ultratv.tv.nativeapp.i18n.DesignStrings) -> String)? = null,
    val group: RailGroup = RailGroup.WATCH,
    // Dernier paramètre : les items existants gardent leur construction RailItem(route, icon) { it.navX }.
    val label: (com.ultratv.tv.nativeapp.i18n.Strings) -> String,
)

// Ordre et icônes de Sidebar.dc.html : Accueil, Direct, Guide, Films, Séries, Favoris, Enregistrements,
// Réglages. La Recherche n'est PAS une destination : c'est le bouton du haut (sous le logo), ouvert aussi
// par les touches Recherche / micro de la télécommande depuis n'importe quel écran.
internal val railItems = listOf(
    RailItem("home", Icons.Home) { it.navHome },
    RailItem("live", Icons.Live) { it.navLive },
    RailItem("guide", Icons.Guide) { it.navGuide },
    RailItem("movies", Icons.Movies) { it.navMovies },
    RailItem("series", Icons.Series) { it.navSeries },
    RailItem("favorites", Icons.Heart, group = RailGroup.MINE) { it.navFavorites },
    RailItem("recordings", Icons.Record, group = RailGroup.MINE) { it.navRecordings },
    RailItem("downloads", com.ultratv.tv.nativeapp.ui.downloads.DownloadIcons.Download, group = RailGroup.MINE) { com.ultratv.tv.nativeapp.ui.downloads.DownloadStrings.of(it).navTitle },
    RailItem("account", Icons.Account, group = RailGroup.FOOT) { it.navAccount },
    RailItem("settings", Icons.Settings, group = RailGroup.FOOT) { it.navSettings },
)

/** Entrée facultative « Sport » (Réglages › Affichage), juste après le Guide. */
private val sportRailItem = RailItem("sport", com.ultratv.tv.nativeapp.ui.sport.SportIcons.Trophy, labelD = { it.sportNav }) { "" }

/** Rail avec (ou sans) la rubrique Sport. */
internal fun railItemsFor(sport: Boolean): List<RailItem> =
    if (!sport) railItems else railItems.flatMap { if (it.route == "guide") listOf(it, sportRailItem) else listOf(it) }

/** Largeur repliée / dépliée du rail, en px de maquette 1920×1080 (112 = 5,8 % de la largeur). */
const val RAIL_COLLAPSED_PX = 112
// 360 (et non 340) : « Téléchargements » était coupé au bord du rail déplié.
const val RAIL_EXPANDED_PX = 360

/**
 * Rail latéral (maquette Sidebar.dc.html) : 112 px, logo, neuf items de 64×64 (icône 30), profil.
 * Quand le focus y entre il s'élargit à 280 px et affiche les libellés ; il passe PAR-DESSUS le
 * contenu avec un voile, sans le décaler (aucun relayout du contenu). L'animation est supprimée
 * en low-RAM (bascule instantanée). Actif = fond accent ; focus = fond blanc, texte noir, ×1,06.
 * À placer dans un Box plein écran, au-dessus du contenu décalé de [RAIL_COLLAPSED_PX].
 */
@androidx.tv.material3.ExperimentalTvMaterial3Api
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SidebarNav(navController: NavController) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: "home"
    val S = LocalStrings.current
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val syncVm: com.ultratv.tv.nativeapp.ui.common.SyncStatusViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val sportOn = com.ultratv.tv.nativeapp.ui.sport.LocalSportEnabled.current
    val items = remember(sportOn) { railItemsFor(sportOn) }
    val lowRam = LocalLowRam.current
    var expanded by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    // Sortie du menu vers le contenu (à droite, à gauche en arabe).
    val leaveRail: () -> Unit = {
        focusManager.moveFocus(if (rtl) androidx.compose.ui.focus.FocusDirection.Left else androidx.compose.ui.focus.FocusDirection.Right)
    }
    val target = (if (expanded) RAIL_EXPANDED_PX else RAIL_COLLAPSED_PX).design
    // Animations réduites (réglage système) ou low-RAM : bascule instantanée, sans état intermédiaire.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val noAnim = lowRam || remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    // Largeur animée lue UNIQUEMENT à la mesure (Modifier.layout) et les libellés par seuil (derivedStateOf) :
    // pendant les ~140 ms de l'animation, le rail se re-mesure mais ne se RE-COMPOSE plus (il comptait neuf
    // FocusSurface recomposées à chaque image, soit autant de travail CPU sur une box d'entrée de gamme).
    val widthState: androidx.compose.runtime.State<androidx.compose.ui.unit.Dp> =
        if (noAnim) androidx.compose.runtime.rememberUpdatedState(target) else animateDpAsState(target, tween(140), label = "rail")
    // Libellés et logotype : visibles seulement APRÈS 70 % de l'élargissement, masqués dès le début du repli.
    val labels by remember(noAnim) {
        androidx.compose.runtime.derivedStateOf {
            val progress = ((widthState.value - RAIL_COLLAPSED_PX.design) / (RAIL_EXPANDED_PX.design - RAIL_COLLAPSED_PX.design)).coerceIn(0f, 1f)
            expanded && (noAnim || progress >= 0.7f)
        }
    }
    val labelAlphaState = androidx.compose.animation.core.animateFloatAsState(if (labels) 1f else 0f, tween(if (noAnim) 0 else 80), label = "railLabels")
    // Libellés COMPOSÉS dès le début de l'ouverture (transparents jusqu'au seuil) : les créer seulement au seuil
    // coûtait une image de mise en page sur une box modeste, d'où des entrées vides un instant avant le texte.
    val showLabels by remember { androidx.compose.runtime.derivedStateOf { expanded || labelAlphaState.value > 0f } }

    val activeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Renvoi du focus vers la page REPORTÉ à l'image suivante : déplacé pendant le traitement de l'événement de focus
    // (contenu disparu, ex. sources retirées), il forçait la liste de la page à se composer au milieu d'une
    // recomposition — « Compose Runtime internal error (pending composition has not been applied) », Fire TV 1.2.96.
    val railScope = androidx.compose.runtime.rememberCoroutineScope()
    var railHasFocus by remember { mutableStateOf(false) }
    val hasActive = route == "search" || items.any { isSelected(route, it.route) }

    Box(Modifier.fillMaxSize()) {
        // Voile de la maquette MenuOuvert (rgba(10,10,12,.72)) : dessiné, jamais mesuré par le contenu.
        if (expanded) Box(Modifier.fillMaxSize().background(Ux.Scrim))
        Row(
            Modifier.fillMaxHeight().layout { m, c ->
                val w = widthState.value.roundToPx().coerceIn(c.minWidth, c.maxWidth)
                val p = m.measure(c.copy(minWidth = w, maxWidth = w))
                layout(p.width, p.height) { p.place(0, 0) }
            },
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Ux.Rail)
                    .clipToBounds()
                    // Entrer dans le menu (← depuis la page) place le focus sur la PAGE OUVERTE, pas sur Rechercher.
                    .focusProperties { enter = { if (hasActive) activeFocus else androidx.compose.ui.focus.FocusRequester.Default } }
                    .focusGroup()
                    // Ne s'ouvre que sur une VRAIE touche : un focus récupéré tout seul (liste rafraîchie par une synchro
                    // en arrière-plan) ouvrait le menu sans action de l'utilisateur ; on rend alors le focus à la page.
                    .onFocusChanged { st ->
                        railHasFocus = st.hasFocus
                        if (!st.hasFocus) expanded = false
                        else if (com.ultratv.tv.nativeapp.ui.common.InputClock.recentNavKey()) expanded = true
                        // Focus arrivé sans touche (élément disparu, fenêtre fermée, clavier) : retour à l'élément mémorisé
                        // de l'écran ; à défaut seulement, déplacement vers la droite. Jamais pendant cet événement.
                        else if (!expanded) railScope.launch {
                            androidx.compose.runtime.withFrameNanos { }
                            if (!railHasFocus || expanded) return@launch
                            val back = com.ultratv.tv.nativeapp.ui.common.CurrentScreenFocus.requester
                            if (back == null || !runCatching { back.requestFocus() }.getOrDefault(false)) runCatching { leaveRail() }
                        }
                    }
                    .onPreviewKeyEvent { ev -> if (!expanded && ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) expanded = true; false }
                    // Géométrie identique replié / déplié : les icônes gardent le même x pendant tout l'élargissement.
                    .padding(vertical = 54.design, horizontal = 24.design),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(Modifier.padding(start = 4.design), verticalAlignment = Alignment.CenterVertically) {
                    LogoMark()
                    if (showLabels) {
                        Spacer(Modifier.width(16.design))
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Row(Modifier.graphicsLayer { alpha = labelAlphaState.value }) {
                            Text("ULTRA ", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.spx, letterSpacing = 1.6.sp, color = Ux.Text, maxLines = 1, softWrap = false)
                            Text("TV", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 26.spx, letterSpacing = 1.6.sp, color = Ux.Text3, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.design))
                // Bouton Recherche : sous le logo, séparé des destinations (comme sur Google TV).
                val searchActive = route == "search"
                FocusSurface(
                    onClick = { if (!searchActive) navController.navigate("search") { launchSingleTop = true } },
                    shape = RoundedCornerShape(32.design),
                    bg = if (searchActive) Ux.Accent else Ux.Surface,
                    focusedScale = 1.05f,
                    ringWidth = 5.design,
                    modifier = Modifier.height(64.designT).fillMaxWidth().testTag("rail-search").then(if (searchActive) Modifier.focusRequester(activeFocus) else Modifier),
                ) { focused ->
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(16.design))
                        Box(Modifier.size(32.design), contentAlignment = Alignment.Center) {
                            DIcon(Icons.Search, 30.design, when { focused -> Ux.TextOnLight; searchActive -> Ux.OnAccent; expanded -> Ux.Text; else -> Ux.Text2 })
                        }
                        if (showLabels) {
                            Spacer(Modifier.width(16.design))
                            Text(D.searchPill, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx,
                                color = when { focused -> Ux.TextOnLight; searchActive -> Ux.OnAccent; else -> Ux.Text2 }, maxLines = 1, softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip, modifier = Modifier.graphicsLayer { alpha = labelAlphaState.value })
                        }
                    }
                }
                Spacer(Modifier.height(12.design))
                // Hauteur des entrées selon la place réelle, avec deux têtes de groupe (« Regarder », « Ma liste ») et le compte /
                // les réglages en bas : 56 px au plus (maquette), jamais écrasées en une barre.
                // Seule la HAUTEUR est mesurée (onSizeChanged, état modifié seulement si elle change) : un BoxWithConstraints
                // recomposait les entrées à chaque image de l'élargissement du menu (largeur animée).
                val density = androidx.compose.ui.platform.LocalDensity.current
                var areaPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { if (it.height != areaPx) areaPx = it.height }) {
                val n = items.size
                val slots = 2 * GROUP_SLOT_PX + 16   // deux têtes de groupe + écart minimal avant le bas
                val maxHeight = if (areaPx > 0) with(density) { areaPx.toDp() } else (56 * n + 6 * (n - 1) + slots).design
                val gapPx = if (maxHeight >= (56 * n + 6 * (n + 1) + slots).design) 6 else 2
                val itemDp = ((maxHeight - (gapPx * (n + 1) + slots).design) / n).coerceIn(40.design, 56.design)
                val railEntry: @Composable (RailItem) -> Unit = { item ->
                        val active = isSelected(route, item.route)
                        FocusSurface(
                            onClick = {
                                // Choisir une page REFERME le menu : le nouvel écran prend le focus
                                // (focus initial normal, pas de markNavDriven). Page déjà affichée :
                                // on rend simplement le focus au contenu.
                                if (route != item.route) {
                                    // Refermé tout de suite : sinon le menu et son voile restaient sur l'ancien écran
                                    // jusqu'à ce que la nouvelle page prenne le focus (superposition visible).
                                    expanded = false
                                    // Mêmes règles de pile que le mobile (Accueil : retour réel au départ).
                                    navController.navigateTopLevel(item.route)
                                } else leaveRail()
                            },
                            shape = RoundedCornerShape(16.design),
                            // Page ouverte : fond teinté + barre d'accent (plus d'aplat plein) ; le focus garde la pastille claire.
                            bg = if (active) Ux.Accent.copy(alpha = 0.18f) else Color.Transparent,
                            focusedScale = 1.04f,
                            ringWidth = 5.design,
                            modifier = Modifier.height(itemDp).fillMaxWidth().then(if (active) Modifier.focusRequester(activeFocus) else Modifier),
                        ) { focused ->
                            if (active) Box(Modifier.align(Alignment.CenterStart).width(4.design).height(32.design).clip(RoundedCornerShape(2.design)).background(Ux.Accent))
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(16.design))
                                Box(Modifier.size(32.design), contentAlignment = Alignment.Center) {
                                    DIcon(item.icon, 30.design, when { focused -> Ux.TextOnLight; active -> Ux.AccentInk; expanded -> Ux.Text; else -> Ux.Text3 })
                                }
                                if (showLabels) {
                                    Spacer(Modifier.width(16.design))
                                    Text(
                                        item.labelD?.invoke(D) ?: item.label(S), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx,
                                        color = when { focused -> Ux.TextOnLight; active -> Ux.Text; else -> Ux.Text2 },
                                        maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 12.design).graphicsLayer { alpha = labelAlphaState.value },
                                    )
                                }
                            }
                        }
                }
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(gapPx.design),
                    horizontalAlignment = Alignment.Start,
                ) {
                    GroupSlot(D.railGroupWatch, showLabels, labelAlphaState)
                    items.filter { it.group == RailGroup.WATCH }.forEach { railEntry(it) }
                    GroupSlot(D.railGroupMine, showLabels, labelAlphaState)
                    items.filter { it.group == RailGroup.MINE }.forEach { railEntry(it) }
                    Spacer(Modifier.weight(1f))
                    items.filter { it.group == RailGroup.FOOT }.forEach { railEntry(it) }
                }
                }
                SyncPill(syncVm.pill, showLabels, labelAlphaState, D.syncing)
                Spacer(Modifier.height(40.design))
                val profileVm: com.ultratv.tv.nativeapp.ui.profile.ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val prof by profileVm.current.collectAsState()
                FocusSurface(
                    onClick = { profileVm.requestSwitch() }, shape = RoundedCornerShape(28.design), bg = Color.Transparent,
                    focusedScale = 1.05f, ringWidth = 5.design,
                    modifier = Modifier.fillMaxWidth().testTag("rail-profile"),
                ) { focused ->
                    Row(Modifier.padding(start = 4.design, top = 4.design), verticalAlignment = Alignment.CenterVertically) {
                        com.ultratv.tv.nativeapp.ui.profile.ProfileAvatar(prof?.initial ?: "K", prof?.color ?: 0xFF26262D.toInt(), 56)
                        if (showLabels) {
                            Spacer(Modifier.width(16.design))
                            Column(Modifier.graphicsLayer { alpha = labelAlphaState.value }, verticalArrangement = Arrangement.spacedBy(2.design)) {
                                Text(prof?.name ?: D.railProfile, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, color = if (focused) Ux.TextOnLight else Ux.Text, maxLines = 1, softWrap = false)
                                Text(D.railSwitchProfile, fontFamily = Manrope, fontSize = 22.spx, color = if (focused) Ux.OnFocus2 else Ux.Text3, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
            // Filet de séparation (1 px #1C1C21 replié, #26262D déplié).
            Box(Modifier.fillMaxHeight().width(0.5f.dp1()).background(if (expanded) Ux.Surface2 else Ux.Surface))
        }
    }
}

private fun Float.dp1() = androidx.compose.ui.unit.Dp(this)

internal fun isSelected(route: String, candidate: String): Boolean = when {
    route == candidate -> true
    candidate == "live" && (route.startsWith("player") || route == "simple-channels") -> true
    candidate == "movies" && route.startsWith("movies/") -> true
    candidate == "series" && route.startsWith("series/") -> true
    else -> false
}

/** Hauteur d'une tête de groupe du rail, en px de maquette. */
private const val GROUP_SLOT_PX = 30

/**
 * Tête de groupe : trait court quand le rail est replié, libellé (« REGARDER », « MA LISTE ») quand il est ouvert.
 * Même hauteur dans les deux états : les entrées ne bougent pas verticalement à l'ouverture.
 */
@Composable
private fun GroupSlot(label: String, showLabels: Boolean, labelAlpha: androidx.compose.runtime.State<Float>) {
    Box(Modifier.fillMaxWidth().height(GROUP_SLOT_PX.design), contentAlignment = Alignment.CenterStart) {
        if (showLabels) Text(
            label.uppercase(), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.spx, letterSpacing = 2.sp, color = Ux.Text3,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 20.design).graphicsLayer { alpha = labelAlpha.value },
        )
        else Box(Modifier.padding(start = 12.design).width(40.design).height(2.design).clip(RoundedCornerShape(1.design)).background(Ux.Surface2))
    }
}

/**
 * Synchro du rail, dans son PROPRE composable : lue dans SidebarNav, chaque progression (2 par seconde) recomposait
 * tout le menu. Replié : anneau de progression avec le pourcentage ; ouvert : libellé, pourcentage et barre.
 */
@Composable
private fun SyncPill(
    pillFlow: kotlinx.coroutines.flow.StateFlow<com.ultratv.tv.nativeapp.data.repo.SyncStatusBus.Status?>,
    showLabels: Boolean,
    labelAlpha: androidx.compose.runtime.State<Float>,
    syncing: String,
) {
    val p = pillFlow.collectAsState().value ?: return
    val pct = (p.percent ?: 0).coerceIn(0, 100)
    val accent = Ux.Accent
    val track = Ux.Surface2
    Spacer(Modifier.height(16.design))
    Row(Modifier.fillMaxWidth().padding(start = 4.design), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.design), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(52.design)) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.design.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                if (pct > 0) drawArc(accent, -90f, 360f * pct / 100f, false, style = stroke)
            }
            Text("$pct", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.spx, color = Ux.Text, maxLines = 1)
        }
        if (showLabels) {
            Spacer(Modifier.width(16.design))
            Column(Modifier.weight(1f).padding(end = 12.design).graphicsLayer { alpha = labelAlpha.value }, verticalArrangement = Arrangement.spacedBy(8.design)) {
                Row {
                    Text(syncing, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, color = Ux.Text2, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$pct %", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, color = Ux.Text2, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(6.design).clip(RoundedCornerShape(3.design)).background(track)) {
                    Box(Modifier.fillMaxWidth(pct / 100f).height(6.design).background(accent))
                }
            }
        }
    }
}

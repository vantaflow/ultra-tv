package com.ultratv.tv.nativeapp.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.LocalLowRam
import com.ultratv.tv.nativeapp.ui.design.Manrope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

internal data class RailItem(val route: String, val icon: String, val label: (com.ultratv.tv.nativeapp.i18n.Strings) -> String)

// Ordre et icônes de Sidebar.dc.html : Accueil, Direct, Guide, Films, Séries, Favoris, Enregistrements,
// Réglages. La Recherche n'est PAS une destination : c'est le bouton du haut (sous le logo), ouvert aussi
// par les touches Recherche / micro de la télécommande depuis n'importe quel écran.
internal val railItems = listOf(
    RailItem("home", Icons.Home) { it.navHome },
    RailItem("live", Icons.Live) { it.navLive },
    RailItem("guide", Icons.Guide) { it.navGuide },
    RailItem("movies", Icons.Movies) { it.navMovies },
    RailItem("series", Icons.Series) { it.navSeries },
    RailItem("favorites", Icons.Heart) { it.navFavorites },
    RailItem("recordings", Icons.Record) { it.navRecordings },
    RailItem("settings", Icons.Settings) { it.navSettings },
)

/** Largeur repliée / dépliée du rail, en px de maquette 1920×1080 (112 = 5,8 % de la largeur). */
const val RAIL_COLLAPSED_PX = 112
const val RAIL_EXPANDED_PX = 340

/**
 * Rail latéral (maquette Sidebar.dc.html) : 112 px, logo, neuf items de 64×64 (icône 30), profil.
 * Quand le focus y entre il s'élargit à 280 px et affiche les libellés ; il passe PAR-DESSUS le
 * contenu avec un voile, sans le décaler (aucun relayout du contenu). L'animation est supprimée
 * en low-RAM (bascule instantanée). Actif = fond accent ; focus = fond blanc, texte noir, ×1,06.
 * À placer dans un Box plein écran, au-dessus du contenu décalé de [RAIL_COLLAPSED_PX].
 */
@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
fun SidebarNav(navController: NavController) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: "home"
    val S = LocalStrings.current
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val syncVm: com.ultratv.tv.nativeapp.ui.common.SyncStatusViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val pill by syncVm.pill.collectAsState()
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
    val showLabels by remember { androidx.compose.runtime.derivedStateOf { labelAlphaState.value > 0f } }

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
                    .onFocusChanged { expanded = it.hasFocus }
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
                    modifier = Modifier.height(64.design).fillMaxWidth().testTag("rail-search"),
                ) { focused ->
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(16.design))
                        Box(Modifier.size(32.design), contentAlignment = Alignment.Center) {
                            DIcon(Icons.Search, 30.design, when { focused -> Ux.TextOnLight; searchActive -> Ux.White; expanded -> Ux.Text; else -> Ux.Text2 })
                        }
                        if (showLabels) {
                            Spacer(Modifier.width(16.design))
                            Text(D.searchPill, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx,
                                color = when { focused -> Ux.TextOnLight; searchActive -> Ux.White; else -> Ux.Text2 }, maxLines = 1, softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip, modifier = Modifier.graphicsLayer { alpha = labelAlphaState.value })
                        }
                    }
                }
                Spacer(Modifier.height(20.design))
                Box(Modifier.fillMaxWidth().height(2.design).background(Ux.Surface2))
                Spacer(Modifier.height(12.design))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.design, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.Start,
                ) {
                    railItems.forEach { item ->
                        val active = isSelected(route, item.route)
                        val h = 64
                        FocusSurface(
                            onClick = {
                                // Choisir une page REFERME le menu : le nouvel écran prend le focus
                                // (focus initial normal, pas de markNavDriven). Page déjà affichée :
                                // on rend simplement le focus au contenu.
                                if (route != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else leaveRail()
                            },
                            shape = RoundedCornerShape(16.design),
                            bg = if (active) Ux.Accent else Color.Transparent,
                            focusedScale = 1.05f,
                            ringWidth = 5.design,
                            modifier = Modifier.height(h.design).fillMaxWidth(),
                        ) { focused ->
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(16.design))
                                Box(Modifier.size(32.design), contentAlignment = Alignment.Center) {
                                    DIcon(item.icon, 30.design, when { focused -> Ux.TextOnLight; active -> Ux.White; expanded -> Ux.Text; else -> Ux.Text3 })
                                }
                                if (showLabels) {
                                    Spacer(Modifier.width(16.design))
                                    Text(
                                        item.label(S), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx,
                                        color = when { focused -> Ux.TextOnLight; active -> Ux.White; else -> Ux.Text2 },
                                        maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                                        modifier = Modifier.graphicsLayer { alpha = labelAlphaState.value },
                                    )
                                }
                            }
                        }
                    }
                }
                val p = pill
                if (p != null) {
                    Spacer(Modifier.height(20.design))
                    Row(Modifier.padding(start = 4.design), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.design).clip(CircleShape).background(Ux.Surface), contentAlignment = Alignment.Center) {
                            Text("${p.percent ?: 0}", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.spx, color = Ux.Text, maxLines = 1)
                        }
                        if (showLabels) {
                            Spacer(Modifier.width(16.design))
                            Text("${D.syncing} · ${p.percent ?: 0} %", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, color = Ux.Text2, maxLines = 1, softWrap = false, modifier = Modifier.graphicsLayer { alpha = labelAlphaState.value })
                        }
                    }
                }
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
    candidate == "live" && route.startsWith("player") -> true
    candidate == "movies" && route.startsWith("movies/") -> true
    candidate == "series" && route.startsWith("series/") -> true
    else -> false
}

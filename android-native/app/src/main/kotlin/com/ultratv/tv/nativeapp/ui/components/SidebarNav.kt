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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.LocalLowRam
import com.ultratv.tv.nativeapp.ui.design.Manrope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.common.NavFocusHint
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoMark
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

private data class RailItem(val route: String, val icon: String, val label: (com.ultratv.tv.nativeapp.i18n.Strings) -> String)

// Ordre et icônes de Sidebar.dc.html : Accueil, Direct, Guide, Films, Séries, Recherche,
// Favoris, Enregistrements, Réglages.
private val railItems = listOf(
    RailItem("home", Icons.Home) { it.navHome },
    RailItem("live", Icons.Live) { it.navLive },
    RailItem("guide", Icons.Guide) { it.navGuide },
    RailItem("movies", Icons.Movies) { it.navMovies },
    RailItem("series", Icons.Series) { it.navSeries },
    RailItem("search", Icons.Search) { it.navSearch },
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
    val target = (if (expanded) RAIL_EXPANDED_PX else RAIL_COLLAPSED_PX).design
    val width = if (lowRam) target else animateDpAsState(target, tween(140), label = "rail").value

    Box(Modifier.fillMaxSize()) {
        // Voile de la maquette MenuOuvert (rgba(10,10,12,.72)) : dessiné, jamais mesuré par le contenu.
        if (expanded) Box(Modifier.fillMaxSize().background(Ux.Scrim))
        Row(Modifier.fillMaxHeight().width(width)) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Ux.Rail)
                    .onFocusChanged { expanded = it.hasFocus }
                    .padding(vertical = 54.design, horizontal = if (expanded) 24.design else 0.design),
                horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Row(Modifier.padding(start = if (expanded) 4.design else 0.design), verticalAlignment = Alignment.CenterVertically) {
                    LogoMark()
                    if (expanded) {
                        Spacer(Modifier.width(16.design))
                        Row {
                            Text("ULTRA ", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.spx, letterSpacing = 1.6.sp, color = Ux.Text)
                            Text("TV", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 26.spx, letterSpacing = 1.6.sp, color = Ux.Text3)
                        }
                    }
                }
                Spacer(Modifier.height(40.design))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (expanded) 8.design else 12.design, Alignment.CenterVertically),
                    horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    railItems.forEach { item ->
                        val active = isSelected(route, item.route)
                        val h = if (expanded) 72 else 64
                        FocusSurface(
                            onClick = {
                                if (route != item.route) {
                                    NavFocusHint.markNavDriven()
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            shape = RoundedCornerShape((if (expanded) 18 else 16).design),
                            bg = if (active) Ux.Accent else Color.Transparent,
                            focusedScale = if (expanded) 1.04f else 1.06f,
                            ringWidth = (if (expanded) 5 else 6).design,
                            modifier = Modifier.height(h.design).then(if (expanded) Modifier.fillMaxWidth() else Modifier.width(64.design)),
                        ) { focused ->
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                if (expanded) Spacer(Modifier.width(16.design))
                                Box(if (expanded) Modifier.size(32.design) else Modifier.size(64.design), contentAlignment = Alignment.Center) {
                                    DIcon(item.icon, (if (expanded) 32 else 30).design, when { focused -> Ux.TextOnLight; active -> Ux.White; expanded -> Ux.Text; else -> Ux.Text3 })
                                }
                                if (expanded) {
                                    Spacer(Modifier.width(16.design))
                                    Text(
                                        item.label(S), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx,
                                        color = when { focused -> Ux.TextOnLight; active -> Ux.White; else -> Ux.Text2 },
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                val p = pill
                if (p != null) {
                    Spacer(Modifier.height(20.design))
                    Row(Modifier.padding(start = if (expanded) 4.design else 0.design), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.design).clip(CircleShape).background(Ux.Surface), contentAlignment = Alignment.Center) {
                            Text("${p.percent ?: 0}", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.spx, color = Ux.Text, maxLines = 1)
                        }
                        if (expanded) {
                            Spacer(Modifier.width(16.design))
                            Text("${D.syncing} · ${p.percent ?: 0} %", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, color = Ux.Text2, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(40.design))
                val profileVm: com.ultratv.tv.nativeapp.ui.profile.ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val prof by profileVm.current.collectAsState()
                FocusSurface(
                    onClick = { profileVm.requestSwitch() }, shape = RoundedCornerShape(if (expanded) 18.design else 28.design), bg = Color.Transparent,
                    focusedScale = if (expanded) 1.04f else 1.06f, ringWidth = 5.design,
                    modifier = Modifier.then(if (expanded) Modifier.fillMaxWidth() else Modifier.size(64.design)).testTag("rail-profile"),
                ) { focused ->
                    Row(Modifier.padding(start = if (expanded) 4.design else 4.design, top = 4.design), verticalAlignment = Alignment.CenterVertically) {
                        com.ultratv.tv.nativeapp.ui.profile.ProfileAvatar(prof?.initial ?: "K", prof?.color ?: 0xFF26262D.toInt(), 56)
                        if (expanded) {
                            Spacer(Modifier.width(16.design))
                            Column(verticalArrangement = Arrangement.spacedBy(2.design)) {
                                Text(prof?.name ?: D.railProfile, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, color = if (focused) Ux.TextOnLight else Ux.Text, maxLines = 1)
                                Text(D.railSwitchProfile, fontFamily = Manrope, fontSize = 22.spx, color = if (focused) Ux.OnFocus2 else Ux.Text3, maxLines = 1)
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

private fun isSelected(route: String, candidate: String): Boolean = when {
    route == candidate -> true
    candidate == "live" && route.startsWith("player") -> true
    candidate == "movies" && route.startsWith("movies/") -> true
    candidate == "series" && route.startsWith("series/") -> true
    else -> false
}

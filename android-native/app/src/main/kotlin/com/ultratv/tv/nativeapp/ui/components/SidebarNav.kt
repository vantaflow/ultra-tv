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
import androidx.compose.runtime.getValue
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

private data class RailItem(val route: String, val icon: String)

// Ordre et icônes de Sidebar.dc.html : Accueil, Direct, Guide, Films, Séries, Recherche,
// Favoris, Enregistrements, Réglages.
private val railItems = listOf(
    RailItem("home", Icons.Home),
    RailItem("live", Icons.Live),
    RailItem("guide", Icons.Guide),
    RailItem("movies", Icons.Movies),
    RailItem("series", Icons.Series),
    RailItem("search", Icons.Search),
    RailItem("favorites", Icons.Heart),
    RailItem("recordings", Icons.Record),
    RailItem("settings", Icons.Settings),
)

/**
 * Rail latéral de 112 px (maquette Claude Design) : logo, neuf icônes, profil. L'entrée
 * active est sur fond accent ; l'entrée focalisée passe en blanc (échelle 1,06 + anneau).
 * Largeur fixe : plus d'animation d'ouverture, donc aucune recomposition au focus.
 */
@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
fun SidebarNav(navController: NavController) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: "home"

    Column(
        Modifier
            .fillMaxHeight()
            .width(56.design)
            .background(Ux.Rail)
            .padding(vertical = 27.design),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoMark()
        Spacer(Modifier.height(20.design))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.design, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            railItems.forEach { item ->
                val active = isSelected(route, item.route)
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
                    shape = RoundedCornerShape(8.design),
                    bg = if (active) Ux.Accent else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.size(32.design),
                ) { focused ->
                    Box(Modifier.size(32.design), contentAlignment = Alignment.Center) {
                        DIcon(
                            item.icon, 16.design,
                            when { focused -> Ux.TextOnLight; active -> Ux.White; else -> Ux.Text3 },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.design))
        Box(
            Modifier.size(28.design).clip(CircleShape).background(Ux.Surface2),
            contentAlignment = Alignment.Center,
        ) { Text("K", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.spx, color = Ux.Text) }
    }
    // Filet de séparation de la maquette (1 px #1C1C21).
    Box(Modifier.fillMaxHeight().width(0.5f.dp1()).background(Ux.Surface))
}

private fun Float.dp1() = androidx.compose.ui.unit.Dp(this)

private fun isSelected(route: String, candidate: String): Boolean = when {
    route == candidate -> true
    candidate == "live" && route.startsWith("player") -> true
    candidate == "movies" && route.startsWith("movies/") -> true
    candidate == "series" && route.startsWith("series/") -> true
    else -> false
}

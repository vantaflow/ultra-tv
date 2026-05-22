package com.ultratv.tv.nativeapp.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import com.ultratv.tv.nativeapp.ui.theme.UltraType

private data class NavEntry(
    val route: String,
    val labelOf: (com.ultratv.tv.nativeapp.i18n.Strings) -> String,
    val icon: UltraIcon,
)

private val navItems = listOf(
    NavEntry("home",       { it.navHome },       UltraIcon.Home),
    NavEntry("live",       { it.navLive },       UltraIcon.Live),
    NavEntry("movies",     { it.navMovies },     UltraIcon.Film),
    NavEntry("series",     { it.navSeries },     UltraIcon.Series),
    NavEntry("guide",      { it.navGuide },      UltraIcon.Guide),
    NavEntry("favorites",  { it.navFavorites },  UltraIcon.Heart),
    NavEntry("categories", { it.navCategories }, UltraIcon.Folder),
    NavEntry("recordings", { "Recordings" },     UltraIcon.Record),
    NavEntry("settings",   { it.navSettings },   UltraIcon.Gear),
)

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
fun SidebarNav(navController: NavController) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: "home"
    val strings = LocalStrings.current

    var anyFocused by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (anyFocused) UltraTokens.SidebarExpanded else UltraTokens.SidebarCollapsed,
        animationSpec = tween(durationMillis = 220),
        label = "sidebar-width",
    )
    // Gate the labels (and active-stripe) on the animated width crossing a
    // midpoint so labels don't pop into a 92 dp container and reflow while
    // the bar is still expanding — that was the flicker users hit when
    // returning to the sidebar with the left D-pad.
    val expanded = width >= 170.dp

    Column(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                Brush.horizontalGradient(
                    0f to UltraTokens.ScrimStrong,
                    0.7f to UltraTokens.Scrim,
                    1f to Color.Transparent,
                )
            )
            .onFocusChanged { anyFocused = it.hasFocus }
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        // Logo
        Row(
            Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(UltraTokens.Accent, UltraTokens.Accent2))),
                contentAlignment = Alignment.Center,
            ) { UltraIcon(UltraIcon.Play, size = 18.dp, color = Color.Black) }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("ULTRA", color = UltraTokens.Fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("TV", color = UltraTokens.Fg3, fontSize = 10.sp, letterSpacing = 3.sp)
                }
            }
        }

        navItems.forEach { entry ->
            val selected = isSelected(route, entry.route)
            SidebarItem(
                icon = entry.icon,
                label = entry.labelOf(strings),
                selected = selected,
                expanded = expanded,
                onClick = {
                    if (route != entry.route) {
                        navController.navigate(entry.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.weight(1f, fill = true))

        // Profile chip
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3057B7), Color(0xFF6839B5)))),
                contentAlignment = Alignment.Center,
            ) { Text("K", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Khalil", color = UltraTokens.Fg2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Salon · UHD", color = UltraTokens.Fg3, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: UltraIcon,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val highlight = focused || selected
    val bg = when {
        focused -> UltraTokens.SurfaceStrong
        selected -> UltraTokens.AccentSoft
        else -> Color.Transparent
    }
    val fg = if (highlight) UltraTokens.Fg else UltraTokens.Fg3
    Box(Modifier.padding(horizontal = 18.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected || focused) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 22.dp)
                        .background(UltraTokens.Accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            UltraIcon(icon, size = 22.dp, color = fg)
            if (expanded) {
                Spacer(Modifier.width(16.dp))
                Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun isSelected(route: String, candidate: String): Boolean = when {
    route == candidate -> true
    candidate == "live" && route.startsWith("player") -> true
    candidate == "movies" && route.startsWith("movies/") -> true
    candidate == "series" && route.startsWith("series/") -> true
    else -> false
}

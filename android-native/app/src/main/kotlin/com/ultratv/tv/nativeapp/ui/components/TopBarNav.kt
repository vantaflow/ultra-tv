package com.ultratv.tv.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class TopBarItem(
    val route: String,
    val labelOf: (com.ultratv.tv.nativeapp.i18n.Strings) -> String,
    val icon: UltraIcon,
)

private val items = listOf(
    TopBarItem("home",       { it.navHome },       UltraIcon.Home),
    TopBarItem("live",       { it.navLive },       UltraIcon.Live),
    TopBarItem("movies",     { it.navMovies },     UltraIcon.Film),
    TopBarItem("series",     { it.navSeries },     UltraIcon.Series),
    TopBarItem("guide",      { it.navGuide },      UltraIcon.Guide),
    TopBarItem("settings",   { it.navSettings },   UltraIcon.Gear),
)

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
fun TopBarNav(navController: NavController) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: "home"
    val strings = LocalStrings.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(UltraTokens.TopBarHeight)
            .background(Color.Transparent)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Nav pills (scrolls on small screens)
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEach { item ->
                val selected = isSelected(route, item.route)
                NavPill(
                    icon = item.icon,
                    label = item.labelOf(strings),
                    selected = selected,
                    onClick = {
                        if (route != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        UtilityCluster(
            onSearchClick = {
                if (route != "search") {
                    navController.navigate("search") { launchSingleTop = true }
                }
            },
            searchActive = route == "search",
        )
    }
}

@Composable
private fun NavPill(icon: UltraIcon, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) UltraTokens.AccentSoft else UltraTokens.Surface2
    val border = if (selected) UltraTokens.Accent else UltraTokens.Line2
    val fg = if (selected) UltraTokens.Fg else UltraTokens.Fg2
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UltraIcon(icon, size = 14.dp, color = fg)
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun UtilityCluster(
    onSearchClick: () -> Unit,
    searchActive: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Search pill — visible everywhere
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (searchActive) UltraTokens.AccentSoft else UltraTokens.Surface2)
                .border(
                    1.dp,
                    if (searchActive) UltraTokens.Accent else UltraTokens.Line2,
                    RoundedCornerShape(999.dp),
                )
                .clickable(onClick = onSearchClick)
                .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
                .width(220.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UltraIcon(UltraIcon.Search, size = 16.dp, color = if (searchActive) UltraTokens.Fg else UltraTokens.Fg2)
            Spacer(Modifier.width(10.dp))
            Text(
                "Films, séries, chaînes…",
                color = UltraTokens.Fg4,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .height(22.dp)
                    .width(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(UltraTokens.Surface3)
                    .border(1.dp, UltraTokens.Line2, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("/", color = UltraTokens.Fg2, fontSize = 11.sp, fontFamily = UltraFonts.Mono) }
        }

        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(width = 1.dp, height = 18.dp).background(UltraTokens.Line))
        Spacer(Modifier.width(14.dp))

        // Weather (stub)
        Text("☀ 18°", color = UltraTokens.Fg3, fontSize = 13.sp)
        Spacer(Modifier.width(14.dp))
        Text("≡ 1.4 Gbps", color = UltraTokens.Fg3, fontSize = 13.sp)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(width = 1.dp, height = 18.dp).background(UltraTokens.Line))
        Spacer(Modifier.width(14.dp))
        Clock()
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = UltraTokens.Fg2, fontSize = 14.sp, fontFamily = UltraFonts.Mono, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text("· $date", color = UltraTokens.Fg3, fontSize = 12.sp)
    }
}

private fun isSelected(route: String, candidate: String): Boolean = when {
    route == candidate -> true
    candidate == "live" && route.startsWith("player") -> true
    candidate == "movies" && route.startsWith("movies/") -> true
    candidate == "series" && route.startsWith("series/") -> true
    else -> false
}

package com.ultratv.tv.nativeapp.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoMark
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.profile.ProfileAvatar

object MobileIcons {
    const val More = "M5 12h.01M12 12h.01M19 12h.01"
    const val Back = "M15 6l-6 6 6 6"
    const val Close = "M6 6l12 12M18 6L6 18"
    const val Cast = "M2 16a6 6 0 0 1 6 6M2 12a10 10 0 0 1 10 10M2 8V5h20v14h-6"
    const val Pip = "M3 5h18v14H3zM12 12h7v5h-7z"
    const val Tune = "M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4"
    const val Pause = "M7 4h3.5v16H7zM13.5 4H17v16h-3.5z"
    const val Brightness = "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5"
    const val Volume = "M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 0 1 0 6M18.5 6.5a8 8 0 0 1 0 11"
    const val Mute = "M4 10v4h4l5 4V6L8 10zM17 9l5 6M22 9l-5 6"
    const val Fullscreen = "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
    const val User = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0"
    const val Download = "M12 4v12M7 11l5 5 5-5M5 20h14"
    const val Trailer = "M3 6h18v12H3zM10 9v6l5-3z"
    const val Moon = "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"
    const val Replay = "M3 12a9 9 0 1 0 3-6.7M3 4v5h5"
    const val Subs = "M3 6h18v12H3zM7 11h3M12 11h5M7 15h6"
    const val Refresh = "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"
}

private data class TabDef(val tab: MobileTab, val route: String, val icon: String, val label: (com.ultratv.tv.nativeapp.i18n.Strings, com.ultratv.tv.nativeapp.i18n.DesignStrings, MobileStrings) -> String)

private val tabs = listOf(
    TabDef(MobileTab.HOME, Routes.HOME, Icons.Home) { s, _, _ -> s.navHome },
    TabDef(MobileTab.LIVE, Routes.LIVE, Icons.Live) { _, d, _ -> d.directTitle },
    TabDef(MobileTab.GUIDE, Routes.GUIDE, Icons.Guide) { s, _, _ -> s.navGuide },
    TabDef(MobileTab.MOVIES, Routes.MOVIES, Icons.Movies) { s, _, _ -> s.navMovies },
    TabDef(MobileTab.MORE, "", MobileIcons.More) { _, _, m -> m.tabMore },
)

/** Navigation d'un onglet / d'une entrée de rail : mêmes réglages de pile que la TV (état sauvegardé, un seul exemplaire). */
fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Barre d'onglets du bas (maquette MobileTabs) : Accueil, Direct, Guide, Films, Plus. 5 colonnes égales,
 * pastille 56×30 accent sous l'onglet actif, libellé 11 sp. « Plus » ouvre la feuille des autres destinations.
 */
@Composable
fun BottomTabs(navController: NavController, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val active = tabForRoute(route)
    val S = LocalStrings.current
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val M = LocalMobileStrings.current
    Row(
        modifier
            .fillMaxWidth()
            .background(Ux.Rail)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics { contentDescription = M.a11yMenu },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { t ->
            val selected = t.tab == active
            val ink = if (selected) Color.White else Ux.Text3
            val label = t.label(S, D, M)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(
                        selected = selected, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = Ux.Text),
                        onClick = { if (t.tab == MobileTab.MORE) onMore() else if (route != t.route) navController.navigateTopLevel(t.route) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) Ux.Accent else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { DIcon(t.icon, 22.dp, ink, strokeWidth = 2f) }
                Spacer4()
                Text(label, color = if (selected) Ux.Text else Ux.Text3, fontFamily = Manrope, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))

private data class MoreDef(val route: String, val icon: String, val label: (com.ultratv.tv.nativeapp.i18n.Strings, com.ultratv.tv.nativeapp.i18n.DesignStrings, MobileStrings) -> String)

private val moreItems = listOf(
    MoreDef(Routes.SERIES, Icons.Series) { s, _, _ -> s.navSeries },
    MoreDef(Routes.FAVORITES, Icons.Heart) { s, _, _ -> s.navFavorites },
    MoreDef("recordings", Icons.Record) { s, _, _ -> s.navRecordings },
    MoreDef(Routes.SETTINGS, Icons.Settings) { _, d, _ -> d.settingsTitle },
    MoreDef("profiles", MobileIcons.User) { _, _, m -> m.navProfiles },
)

/** Feuille « Plus » : Séries, Recherche, Favoris, Enregistrements, Réglages, Profils. Retour système = fermer. */
@Composable
fun MoreSheet(navController: NavController, onDismiss: () -> Unit, onProfiles: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val S = LocalStrings.current
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val M = LocalMobileStrings.current
    Box(
        Modifier.fillMaxSize().background(Ux.Scrim).pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Ux.SurfaceDeep)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ux.Line))
            Text(M.moreTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            moreItems.forEach { item ->
                val label = item.label(S, D, M)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = label) {
                            onDismiss()
                            if (item.route == "profiles") onProfiles() else navController.navigateTopLevel(item.route)
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DIcon(item.icon, 24.dp, Ux.Text2)
                    Text(label, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private data class RailDef(val route: String, val icon: String, val label: (com.ultratv.tv.nativeapp.i18n.Strings, com.ultratv.tv.nativeapp.i18n.DesignStrings) -> String)

private val railDefs = listOf(
    RailDef(Routes.HOME, Icons.Home) { s, _ -> s.navHome },
    RailDef(Routes.LIVE, Icons.Live) { _, d -> d.directTitle },
    RailDef(Routes.GUIDE, Icons.Guide) { s, _ -> s.navGuide },
    RailDef(Routes.MOVIES, Icons.Movies) { s, _ -> s.navMovies },
    RailDef(Routes.SERIES, Icons.Series) { s, _ -> s.navSeries },
    RailDef(Routes.FAVORITES, Icons.Heart) { s, _ -> s.navFavorites },
    RailDef("recordings", Icons.Record) { s, _ -> s.navRecordings },
    RailDef(Routes.SETTINGS, Icons.Settings) { _, d -> d.settingsTitle },
)

/** Rail latéral tactile (maquette TabletteDirect) : 88 dp, logo, entrées 64 dp (pastille 52×32), profil en bas. */
@Composable
fun TouchRail(navController: NavController, profile: ProfileChip?, onProfile: () -> Unit, modifier: Modifier = Modifier) {
    val entry by navController.currentBackStackEntryAsState()
    val active = railRouteFor(entry?.destination?.route)
    val S = LocalStrings.current
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val M = LocalMobileStrings.current
    Column(
        modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(Ux.Rail)
            .padding(vertical = 20.dp)
            .semantics { contentDescription = M.a11yMenu },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoMark(88)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            railDefs.forEach { r ->
                val selected = r.route == active
                val label = r.label(S, D)
                Column(
                    Modifier
                        .width(72.dp)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .selectable(selected = selected, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = Ux.Text), onClick = { if (!selected) navController.navigateTopLevel(r.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) Ux.Accent else Color.Transparent), contentAlignment = Alignment.Center) {
                        DIcon(r.icon, 22.dp, if (selected) Color.White else Ux.Text3)
                    }
                    Spacer4()
                    Text(label, color = if (selected) Ux.Text else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(
            Modifier.padding(top = 12.dp).size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = M.a11yProfile) { onProfile() }
                .semantics { contentDescription = M.a11yProfile },
            contentAlignment = Alignment.Center,
        ) { ProfileAvatar(profile?.initial ?: "K", profile?.color ?: 0xFF26262D.toInt(), 80) }
    }
}

/** Profil actif affiché en bas du rail (initiale + couleur). */
data class ProfileChip(val initial: String, val color: Int)

/**
 * Coque tactile : barre d'onglets en compact, rail latéral en medium / expanded, jamais sur le lecteur (plein écran).
 * Le contenu est fourni par l'appelant (le NavHost) ; les marges système viennent des insets.
 */
@Composable
fun MobileScaffold(
    layout: NavLayout,
    navController: NavController,
    fullscreen: Boolean,
    profile: ProfileChip?,
    onProfile: () -> Unit,
    banner: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var moreOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val insets = WindowInsets.safeDrawing
    when (layout) {
        NavLayout.SIDE_RAIL -> Row(Modifier.fillMaxSize().background(Ux.Bg)) {
            if (!fullscreen) TouchRail(navController, profile, onProfile, Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)))
            Box(Modifier.weight(1f).fillMaxSize().then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.End)))) {
                Column(Modifier.fillMaxSize()) { banner(); Box(Modifier.weight(1f)) { content() } }
            }
        }
        else -> {
            Column(Modifier.fillMaxSize().background(Ux.Bg)) {
                Box(Modifier.weight(1f).then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))) {
                    Column(Modifier.fillMaxSize()) { banner(); Box(Modifier.weight(1f)) { content() } }
                }
                if (!fullscreen) BottomTabs(navController, onMore = { moreOpen = true })
            }
            if (moreOpen && !fullscreen) MoreSheet(navController, onDismiss = { moreOpen = false }, onProfiles = onProfile)
        }
    }
}

/** Ouvre la recherche plein écran (empile sans vider : Retour ramène à l'écran d'origine). Fourni par la racine. */
val LocalOpenSearch = androidx.compose.runtime.compositionLocalOf<() -> Unit> { {} }
/** Retour (pile de navigation) pour les écrans plein écran tactiles. */
val LocalNavBack = androidx.compose.runtime.compositionLocalOf<() -> Unit> { {} }

/** Loupe de la barre du haut des écrans principaux (tactile seulement). */
@Composable
fun SearchAction(modifier: Modifier = Modifier) {
    if (!LocalTouch.current) return
    val M = LocalMobileStrings.current
    val open = LocalOpenSearch.current
    IconCircle(Icons.Search, M.a11ySearch, Ux.Surface, Ux.Text, open, modifier)
}

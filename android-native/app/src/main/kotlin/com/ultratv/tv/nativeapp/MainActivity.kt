package com.ultratv.tv.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.ultratv.tv.nativeapp.data.prefs.SidebarPosition
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.HistoryRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.sync.SyncScheduler
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.AppViewModel
import com.ultratv.tv.nativeapp.ui.categories.CategoriesScreen
import com.ultratv.tv.nativeapp.ui.common.FormFactor
import com.ultratv.tv.nativeapp.ui.common.ScreenFocusHost
import com.ultratv.tv.nativeapp.ui.common.rememberFormFactor
import com.ultratv.tv.nativeapp.ui.components.BottomBarNav
import com.ultratv.tv.nativeapp.ui.components.SidebarNav
import com.ultratv.tv.nativeapp.ui.favorites.FavoritesScreen
import com.ultratv.tv.nativeapp.ui.guide.GuideGridScreen
import com.ultratv.tv.nativeapp.ui.home.HomeScreen
import com.ultratv.tv.nativeapp.ui.live.LiveScreen
import com.ultratv.tv.nativeapp.ui.movies.MovieDetailScreen
import com.ultratv.tv.nativeapp.ui.player.PlayerScreen
import com.ultratv.tv.nativeapp.ui.search.SearchScreen
import com.ultratv.tv.nativeapp.ui.series.SeriesDetailScreen
import com.ultratv.tv.nativeapp.ui.settings.SettingsScreen
import com.ultratv.tv.nativeapp.ui.theme.UltraTvTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Carries a one-shot "open this URL+title in the player as soon as the
 * Composition is up" intent. Set by [MainActivity.kickoffStartupTasks] when
 * `autoPlayLastOnLaunch` is enabled; consumed by [UltraTvAppRoot] once.
 */
object StartupNav {
    data class Pending(val url: String, val title: String)
    val pending = MutableStateFlow<Pending?>(null)
    /** Route à ouvrir une fois l'application affichée (ex. « Regarder le direct » depuis le chargement). */
    val pendingRoute = MutableStateFlow<String?>(null)
    /** Débogage uniquement : rubrique de Réglages à ouvrir. */
    val debugRub = MutableStateFlow<Int?>(null)
    /** Build debug : requête préremplie (Recherche) et thème forcé (captures). */
    val debugQuery = MutableStateFlow<String?>(null)
    val debugTheme = MutableStateFlow<String?>(null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefsStore: UserPreferencesStore
    @Inject lateinit var providerRepo: ProviderRepository
    @Inject lateinit var historyRepo: HistoryRepository
    @Inject lateinit var playback: PlaybackContext
    @Inject lateinit var syncCoordinator: com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
    @Inject lateinit var deepLinks: com.ultratv.tv.nativeapp.nav.DeepLinkHandler
    @Inject lateinit var recordingScheduler: com.ultratv.tv.nativeapp.data.recording.RecordingScheduler
    @Inject lateinit var remindersScheduler: com.ultratv.tv.nativeapp.data.reminders.RemindersScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        // SplashScreen API : affiche le thème de lancement tout de suite et évite
        // l'écran noir pendant l'init Hilt/Room.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        RemoteLog.info("activity", "onCreate restoredState=${savedInstanceState != null}")
        handleDebugIntent(intent)
        handleDeepLink(intent)
        // Ré-arme rappels et enregistrements programmés (alarmes perdues après une mise à jour ou un arrêt forcé).
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { remindersScheduler.rescheduleAll() }
            runCatching { recordingScheduler.rearmAll() }
        }
        setContent { Root() }
        kickoffStartupTasks()
        // Auto-update flow: query GitHub Releases on launch and, if a newer
        // version is found, download + fire the system install Intent without
        // asking the user first. They still get the OS's "Install this app?"
        // prompt — that one can't be skipped without device-owner privileges.
        lifecycleScope.launch {
            // Pas pendant le démarrage : on laisse l'interface devenir interactive d'abord
            // (et l'invite système d'installation ne vole pas le focus au lancement).
            kotlinx.coroutines.delay(30_000)
            val info = com.ultratv.tv.nativeapp.update.UpdateChecker.checkForUpdate()
                ?: return@launch
            com.ultratv.tv.nativeapp.RemoteLog.info(
                "update",
                "auto-installing ${info.tag}",
            )
            runCatching {
                com.ultratv.tv.nativeapp.update.UpdateChecker
                    .downloadAndInstall(this@MainActivity, info)
            }.onFailure {
                com.ultratv.tv.nativeapp.RemoteLog.warn(
                    "update",
                    "auto-install failed: ${it.javaClass.simpleName} ${it.message}",
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
        handleDeepLink(intent)
    }

    /** Lien profond `ultratv://…` (notification de rappel, Google TV) : résolu dans le catalogue local. */
    private fun handleDeepLink(intent: android.content.Intent?) {
        // Recherche vocale : ACTION_SEARCH + requête ; sinon lien profond ultratv://… (VIEW).
        val voiceQuery = if (intent?.action == android.content.Intent.ACTION_SEARCH) intent.getStringExtra(android.app.SearchManager.QUERY)?.trim()?.takeIf { it.isNotEmpty() } else null
        val link = voiceQuery?.let { com.ultratv.tv.nativeapp.nav.DeepLink.Search(it) }
            ?: com.ultratv.tv.nativeapp.nav.DeepLink.parse(intent?.data?.toString()) ?: return
        lifecycleScope.launch { runCatching { deepLinks.handle(link) } }
    }

    /** Build debug seulement : `am start --es debug_route settings --ei debug_rub 1` ouvre un écran précis (captures, tests). */
    private fun handleDebugIntent(intent: android.content.Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        intent.getStringExtra("debug_route")?.let { StartupNav.pendingRoute.value = it }
        intent.getStringExtra("debug_query")?.let { StartupNav.debugQuery.value = it }
        intent.getStringExtra("debug_theme")?.let { StartupNav.debugTheme.value = it }
        com.ultratv.tv.nativeapp.ui.common.DebugConnectivity.forceOffline = intent.getBooleanExtra("debug_offline", false)
        if (intent.hasExtra("debug_rub")) StartupNav.debugRub.value = intent.getIntExtra("debug_rub", 0)
    }

    /**
     * When the user presses Home while a stream is playing, enter PiP so the
     * stream keeps going in a corner. Falls back silently on devices that
     * don't support it (some TV firmwares).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (playback.current.value == null) return
        runCatching {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    /**
     * Best-effort startup tasks. Both are gated by user prefs.
     *
     *  1. Auto-sync providers if `autoSyncOnLaunch` is on AND the configured
     *     [UserPrefs.syncIntervalHours] interval has elapsed since the last
     *     successful sync. Interval 0 means "every launch".
     *  2. Auto-play the most recently watched item by emitting a Pending
     *     entry on [StartupNav.pending], which the NavGraph picks up.
     */
    private fun kickoffStartupTasks() {
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = prefsStore.flow.first()

            // (Re-)apply the background sync schedule from the stored prefs
            // every time the app starts so a re-install / OS restart picks up
            // where we left off.
            SyncScheduler.schedule(this@MainActivity, prefs.syncIntervalHours)

            // Synchro incrémentale : le TTL (par partie du catalogue) décide de ce qui est rechargé ;
            // une source jamais synchronisée ou vide l'est TOUJOURS, même si la synchro auto est coupée.
            val all = providerRepo.observeProviders().first()
            // Mode : auto (TTL selon l'appareil/le réseau) · à chaque lancement · planifiée (travail périodique) · manuelle.
            all.forEach { p -> if (p.lastLiveSyncAt == 0L || (prefs.syncMode != "manual" && prefs.syncMode != "scheduled" && prefs.autoSyncOnLaunch)) syncCoordinator.request(p.id) }
            if (prefs.syncMode == "scheduled") SyncScheduler.scheduleDaily(this@MainActivity, prefs.syncHour, prefs.syncUnmeteredOnly) else if (prefs.syncMode != "auto") SyncScheduler.schedule(this@MainActivity, 0)

            if (prefs.autoPlayLastOnLaunch) {
                val firstProvider = providerRepo.observeProviders().first().firstOrNull()
                if (firstProvider != null) {
                    val last = historyRepo.recent(firstProvider.id, 1).first().firstOrNull()
                    if (last != null) {
                        playback.set(PlaybackContext.Item(
                            providerId = last.providerId, kind = last.kind, remoteId = last.remoteId,
                            title = last.title, poster = last.poster, streamUrl = last.streamUrl,
                            parentRemoteId = last.parentRemoteId,
                        ))
                        StartupNav.pending.value = StartupNav.Pending(last.streamUrl, last.title)
                    }
                }
            }
        }
    }
}

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
private fun Root(vm: AppViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsState()
    val ctxForDevice = androidx.compose.ui.platform.LocalContext.current
    val lang = com.ultratv.tv.nativeapp.i18n.AppLang.fromCode(prefs.language)
    val strings = com.ultratv.tv.nativeapp.i18n.stringsFor(lang)
    val direction = if (lang == com.ultratv.tv.nativeapp.i18n.AppLang.Arabic)
        androidx.compose.ui.unit.LayoutDirection.Rtl
    else
        androidx.compose.ui.unit.LayoutDirection.Ltr
    val adaptiveVm: com.ultratv.tv.nativeapp.adaptive.AdaptiveViewModel = hiltViewModel()
    val adaptive by adaptiveVm.state.collectAsState()
    val lowRam = adaptive.auto.lowRam
    androidx.compose.runtime.CompositionLocalProvider(
        com.ultratv.tv.nativeapp.ui.common.LocalLowRam provides lowRam,
        com.ultratv.tv.nativeapp.adaptive.LocalAdaptive provides adaptive,
        com.ultratv.tv.nativeapp.i18n.LocalStrings provides strings,
        com.ultratv.tv.nativeapp.i18n.LocalDs provides com.ultratv.tv.nativeapp.i18n.designStringsFor(lang),
        androidx.compose.ui.platform.LocalLayoutDirection provides direction,
    ) {
        val dbgTheme by StartupNav.debugTheme.collectAsState()
        com.ultratv.tv.nativeapp.ui.theme.ApplyUxTheme(dbgTheme?.let { com.ultratv.tv.nativeapp.data.prefs.AppTheme.parse(it) } ?: prefs.theme)
        com.ultratv.tv.nativeapp.ui.common.ProvideUiScale {
        UltraTvTheme {
            // L'assistant de premier lancement REMPLACE l'application au lieu de
            // se superposer : avant, l'accueil restait composé derrière (premier
            // focalisable = barre latérale), donc la télécommande pilotait un
            // écran invisible et « Suivant » ne recevait jamais le focus.
            val onboarding: com.ultratv.tv.nativeapp.ui.onboarding.OnboardingViewModel = hiltViewModel()
            val showOnboarding by onboarding.show.collectAsState()
            val profileGate: com.ultratv.tv.nativeapp.ui.profile.ProfileGateViewModel = hiltViewModel()
            val askProfile by profileGate.needsSelection.collectAsState()
            when {
                askProfile == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                askProfile == true && showOnboarding == false -> com.ultratv.tv.nativeapp.ui.profile.WhoIsWatchingScreen()
                else -> when (showOnboarding) {
                null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                true -> com.ultratv.tv.nativeapp.ui.onboarding.OnboardingWizard(
                    onOpenSettings = { /* user can re-enter Settings via sidebar */ },
                    vm = onboarding,
                )
                false -> {
                    val first: com.ultratv.tv.nativeapp.ui.sync.FirstSyncViewModel = hiltViewModel()
                    val firstState by first.state.collectAsState()
                    val ui = firstState?.ui
                    when {
                        firstState == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        ui != null -> com.ultratv.tv.nativeapp.ui.sync.FirstSyncScreen(
                            ui,
                            onWatchLive = { StartupNav.pendingRoute.value = Routes.LIVE; first.leaveToLive() },
                            onRetry = { first.retry(ui.providerId) },
                            onFixSource = { StartupNav.pendingRoute.value = Routes.SETTINGS; first.dismiss() },
                        )
                        else -> Box(Modifier.fillMaxSize()) { UltraTvAppRoot(prefs.sidebarPosition) }
                    }
                }
                }
            }
        }
        }
    }
}

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
private fun UltraTvAppRoot(sidebarPosition: SidebarPosition) {
    val nav = rememberNavController()
    // Le lecteur reste sombre : état DÉRIVÉ des destinations visibles (écrit avant NavHost, donc
    // avant toute lecture des jetons par le lecteur ; aucun compteur à tenir à jour).
    val visibleEntries by nav.visibleEntries.collectAsState()
    val playerShown = com.ultratv.tv.nativeapp.ui.theme.isPlayerShown(visibleEntries.map { it.destination.route })
    if (com.ultratv.tv.nativeapp.ui.design.Ux.playerActive != playerShown) com.ultratv.tv.nativeapp.ui.design.Ux.playerActive = playerShown
    val form = rememberFormFactor()
    // Effective nav style: phone-portrait collapses to a bottom bar regardless
    // of the user's "sidebar / top bar" preference, otherwise we honour it.
    val useBottomBar = form == FormFactor.Compact

    // One-shot: as soon as we have a NavController, consume any pending
    // auto-play request set during startup.
    val pending by StartupNav.pending.collectAsState()
    val pendingRoute by StartupNav.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val r = pendingRoute ?: return@LaunchedEffect
        nav.navigate(r)
        StartupNav.pendingRoute.value = null
    }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        nav.navigate(Routes.player(p.url, p.title))
        StartupNav.pending.value = null
    }

    val overlays = androidx.compose.runtime.remember { com.ultratv.tv.nativeapp.ui.common.OverlayHost() }
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
      androidx.compose.runtime.CompositionLocalProvider(com.ultratv.tv.nativeapp.ui.common.LocalOverlayHost provides overlays) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        when {
            useBottomBar -> Column(Modifier.fillMaxSize()) {
                com.ultratv.tv.nativeapp.ui.common.SyncStatusBanner(onFixSource = { nav.navigate(Routes.SETTINGS) })
                Box(
                    Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(PaddingValues(horizontal = 12.dp)),
                ) { NavGraph(nav) }
                BottomBarNav(navController = nav)
            }
            else -> Column(Modifier.fillMaxSize()) {
                com.ultratv.tv.nativeapp.ui.common.SyncStatusBanner(onFixSource = { nav.navigate(Routes.SETTINGS) })
                // Le contenu est décalé de la largeur REPLIÉE du rail ; le rail déplié passe par-dessus
                // (même Box) sans jamais décaler ni re-mesurer le contenu.
                // Le lecteur est plein écran : ni rail ni marge (sinon le rail volerait le focus et recouvrirait la vidéo).
                val backEntry by nav.currentBackStackEntryAsState()
                val fullscreen = backEntry?.destination?.route?.startsWith("player") == true
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(start = if (fullscreen) 0.dp else com.ultratv.tv.nativeapp.ui.components.RAIL_COLLAPSED_PX.let { (it / 2f).dp }),
                    ) { NavGraph(nav) }
                    if (!fullscreen) SidebarNav(navController = nav)
                }
            }
        }
        com.ultratv.tv.nativeapp.ui.common.OverlayLayer(overlays)
        com.ultratv.tv.nativeapp.ui.common.ToasterHost()
        com.ultratv.tv.nativeapp.ui.common.ReminderBannerHost()
        }
      }
    }
}

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
private fun NavGraph(nav: androidx.navigation.NavHostController) {
    // Ship the current route to the worker on every back-stack change. Lets us
    // see in /logs which screen the user was on right before a silent crash.
    androidx.compose.runtime.LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect { entry ->
            RemoteLog.info("nav", "→ ${entry.destination.route ?: "(unknown)"}")
        }
    }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        // Pas de fondu de 700 ms entre écrans : sur un CPU de Chromecast c'est
        // du travail de composition en double, et pendant la transition le focus
        // pouvait atterrir dans l'écran sortant.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        screen(Routes.HOME) {
            HomeScreen(
                onGoLive = { nav.navigate(Routes.LIVE) },
                onGoMovies = { nav.navigate(Routes.MOVIES) },
                onGoSeries = { nav.navigate(Routes.SERIES) },
                onGoSettings = { nav.navigate(Routes.SETTINGS) },
                onGoGuide = { nav.navigate(Routes.GUIDE) },
                onPlay = { url, title -> nav.navigate(Routes.player(url, title)) },
                onOpenMovie = { id -> nav.navigate(Routes.movieDetail(id)) },
                onOpenSeries = { id -> nav.navigate(Routes.seriesDetail(id)) },
            )
        }
        screen(Routes.LIVE) {
            LiveScreen(onPlay = { url, title -> nav.navigate(Routes.player(url, title)) })
        }
        screen(Routes.MOVIES) {
            com.ultratv.tv.nativeapp.ui.catalog.CatalogGridScreen(com.ultratv.tv.nativeapp.ui.catalog.CatalogKind.MOVIES, onOpen = { id -> nav.navigate(Routes.movieDetail(id)) })
        }
        screen(
            Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            MovieDetailScreen(
                movieId = id,
                onPlay = { url, title -> nav.navigate(Routes.player(url, title)) },
                onBack = { nav.popBackStack() },
            )
        }
        screen(Routes.SERIES) {
            com.ultratv.tv.nativeapp.ui.catalog.CatalogGridScreen(com.ultratv.tv.nativeapp.ui.catalog.CatalogKind.SERIES, onOpen = { id -> nav.navigate(Routes.seriesDetail(id)) })
        }
        screen(
            Routes.SERIES_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            SeriesDetailScreen(
                seriesId = id,
                onPlayEpisode = { url, title -> nav.navigate(Routes.player(url, title)) },
                onBack = { nav.popBackStack() },
            )
        }
        screen(Routes.SEARCH) {
            SearchScreen(
                onOpenChannel = { url, title -> nav.navigate(Routes.player(url, title)) },
                onOpenMovie = { id -> nav.navigate(Routes.movieDetail(id)) },
                onOpenSeries = { id -> nav.navigate(Routes.seriesDetail(id)) },
            )
        }
        screen(Routes.GUIDE) {
            GuideGridScreen(
                onPlayChannel = { ch -> nav.navigate(Routes.player(ch.streamUrl, ch.name)) },
                onPlayUrl = { url, title -> nav.navigate(Routes.player(url, title)) },
            )
        }
        screen("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
        screen("diagnostic") { com.ultratv.tv.nativeapp.ui.settings.DiagnosticScreen() }
        screen("locked-channels") { com.ultratv.tv.nativeapp.ui.parental.LockedChannelsScreen(onBack = { nav.popBackStack() }) }
        screen("recordings") {
            com.ultratv.tv.nativeapp.ui.recordings.RecordingsScreen(
                onPlayLocal = { url, title -> nav.navigate(Routes.player(url, title)) },
            )
        }
        screen(Routes.FAVORITES) {
            FavoritesScreen(
                onPlayChannel = { url, title -> nav.navigate(Routes.player(url, title)) },
                onBrowseLive = { nav.navigate(Routes.LIVE) },
                onOpenMovie = { id -> nav.navigate(Routes.movieDetail(id)) },
                onOpenSeries = { id -> nav.navigate(Routes.seriesDetail(id)) },
            )
        }
        screen(Routes.SETTINGS) { SettingsScreen(onNavigate = { route -> nav.navigate(route) }) }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val rawUrl = entry.arguments?.getString("url").orEmpty()
            val rawTitle = entry.arguments?.getString("title").orEmpty()
            val url = java.net.URLDecoder.decode(rawUrl, "UTF-8")
            val title = java.net.URLDecoder.decode(rawTitle, "UTF-8")
            PlayerScreen(url = url, title = title, onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(Routes.HOME, false) })
        }
    }
}


/** `composable` + focus D-pad initial et restauration (voir [ScreenFocusHost]). */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        ScreenFocusHost { content(entry) }
    }
}

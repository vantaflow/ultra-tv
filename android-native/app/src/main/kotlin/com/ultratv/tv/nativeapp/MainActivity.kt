package com.ultratv.tv.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.components.SidebarNav
import com.ultratv.tv.nativeapp.ui.favorites.FavoritesScreen
import com.ultratv.tv.nativeapp.ui.guide.GuideScreen
import com.ultratv.tv.nativeapp.ui.home.HomeScreen
import com.ultratv.tv.nativeapp.ui.live.LiveScreen
import com.ultratv.tv.nativeapp.ui.movies.MovieDetailScreen
import com.ultratv.tv.nativeapp.ui.movies.MoviesScreen
import com.ultratv.tv.nativeapp.ui.multiview.MultiViewScreen
import com.ultratv.tv.nativeapp.ui.player.PlayerScreen
import com.ultratv.tv.nativeapp.ui.search.SearchScreen
import com.ultratv.tv.nativeapp.ui.series.SeriesDetailScreen
import com.ultratv.tv.nativeapp.ui.series.SeriesScreen
import com.ultratv.tv.nativeapp.ui.settings.SettingsScreen
import com.ultratv.tv.nativeapp.ui.theme.UltraTvTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { UltraTvTheme { UltraTvAppRoot() } }
    }
}

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
private fun UltraTvAppRoot() {
    val nav = rememberNavController()
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Row(Modifier.fillMaxSize()) {
            SidebarNav(navController = nav)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(PaddingValues(start = 12.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)),
            ) {
                NavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onGoLive = { nav.navigate(Routes.LIVE) },
                            onGoMovies = { nav.navigate(Routes.MOVIES) },
                            onGoSeries = { nav.navigate(Routes.SERIES) },
                            onGoSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                    }
                    composable(Routes.LIVE) {
                        LiveScreen(onPlay = { url, title -> nav.navigate(Routes.player(url, title)) })
                    }
                    composable(Routes.MOVIES) {
                        MoviesScreen(onOpen = { id -> nav.navigate(Routes.movieDetail(id)) })
                    }
                    composable(
                        Routes.MOVIE_DETAIL,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: -1L
                        MovieDetailScreen(
                            movieId = id,
                            onPlay = { url, title -> nav.navigate(Routes.player(url, title)) },
                        )
                    }
                    composable(Routes.SERIES) {
                        SeriesScreen(onOpen = { id -> nav.navigate(Routes.seriesDetail(id)) })
                    }
                    composable(
                        Routes.SERIES_DETAIL,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: -1L
                        SeriesDetailScreen(
                            seriesId = id,
                            onPlayEpisode = { url, title -> nav.navigate(Routes.player(url, title)) },
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            onOpenChannel = { /* TODO: deep-link */ },
                            onOpenMovie = { id -> nav.navigate(Routes.movieDetail(id)) },
                            onOpenSeries = { id -> nav.navigate(Routes.seriesDetail(id)) },
                        )
                    }
                    composable(Routes.MULTIVIEW) { MultiViewScreen() }
                    composable(Routes.GUIDE) { GuideScreen() }
                    composable(Routes.FAVORITES) {
                        FavoritesScreen(
                            onOpenMovie = { id -> nav.navigate(Routes.movieDetail(id)) },
                            onOpenSeries = { id -> nav.navigate(Routes.seriesDetail(id)) },
                        )
                    }
                    composable(Routes.SETTINGS) { SettingsScreen() }
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
                        PlayerScreen(url = url, title = title, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

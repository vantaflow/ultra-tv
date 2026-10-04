package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.design.Ux

private val Accent = Color(0xFFD91E2B)

private val Dark = darkColorScheme(
    primary = Accent, onPrimary = Color.White,
    background = Color(0xFF0A0A0C), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF141418), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF1C1C21), onSurfaceVariant = Color(0xFFC4C4CC),
    border = Color(0xFF3F3F46),
    inverseSurface = Color.White, inverseOnSurface = Color(0xFF0A0A0C),
)

// Palette claire (maquettes AccueilClair / SidebarClair) : focus inversé = encre + texte blanc.
private val Light = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    background = Color(0xFFF4F3EF), onBackground = Color(0xFF16151A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF16151A),
    surfaceVariant = Color(0xFFECEAE5), onSurfaceVariant = Color(0xFF3C3B42),
    border = Color(0xFFD9D6CF),
    inverseSurface = Color(0xFF16151A), inverseOnSurface = Color.White,
)

/** Résout le réglage (Sombre / Clair / Automatique) en « clair ? » selon le thème système. */
fun isLightTheme(theme: AppTheme, systemDark: Boolean): Boolean = when (theme) {
    AppTheme.LIGHT -> true
    AppTheme.DARK -> false
    AppTheme.AUTO -> !systemDark
}

/** Le lecteur reste TOUJOURS sombre, quel que soit le réglage. */
fun isLightEffective(themeLight: Boolean, playerShown: Boolean): Boolean = themeLight && !playerShown

/**
 * Vrai si un écran lecteur figure parmi les destinations VISIBLES de la navigation (y compris
 * celle qui sort en transition). État dérivé : un lien profond qui empile un second lecteur
 * avant la sortie du premier ne peut pas le désynchroniser, contrairement à un compteur.
 */
fun isPlayerShown(visibleRoutes: List<String?>): Boolean = visibleRoutes.any { it == com.ultratv.tv.nativeapp.nav.Routes.PLAYER }

/** Publie le thème dans les jetons [Ux] ; à appeler AVANT toute composition qui les lit. */
@Composable
fun ApplyUxTheme(theme: AppTheme) {
    val light = isLightTheme(theme, isSystemInDarkTheme())
    if (Ux.themeLight != light) Ux.themeLight = light
}

@Composable
fun UltraTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isLightEffective(Ux.themeLight, Ux.playerActive)) Light else Dark, content = content)
}

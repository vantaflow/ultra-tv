package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.LightPalette
import com.ultratv.tv.nativeapp.ui.design.LocalUxPalette
import com.ultratv.tv.nativeapp.ui.design.OledPalette
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.paletteFor

private val Accent = Color(0xFFFF3B47)

// Schémas Material (rares composants restants) alignés sur la palette v2 (Palette.kt).
private val Dark = darkColorScheme(
    primary = Accent, onPrimary = Color.White,
    background = DarkPalette.bg, onBackground = DarkPalette.text,
    surface = DarkPalette.card2, onSurface = DarkPalette.text,
    surfaceVariant = DarkPalette.button, onSurfaceVariant = DarkPalette.text2,
    border = DarkPalette.line,
    inverseSurface = DarkPalette.focusFill, inverseOnSurface = DarkPalette.focusText,
)

// Palette claire : focus inversé = encre + texte clair.
private val Light = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    background = LightPalette.bg, onBackground = LightPalette.text,
    surface = LightPalette.card, onSurface = LightPalette.text,
    surfaceVariant = LightPalette.button, onSurfaceVariant = LightPalette.text2,
    border = LightPalette.line,
    inverseSurface = LightPalette.focusFill, inverseOnSurface = LightPalette.focusText,
)

/**
 * Résout le réglage (Sombre / Clair / Automatique) en « clair ? » selon le thème système.
 * Automatique sur une TV (qui n'a pas de vrai réglage clair/sombre système) = Sombre.
 */
fun isLightTheme(theme: AppTheme, systemDark: Boolean, isTv: Boolean = false): Boolean = when (theme) {
    AppTheme.LIGHT, AppTheme.SAND -> true
    AppTheme.DARK, AppTheme.OLED, AppTheme.MIDNIGHT, AppTheme.FOREST, AppTheme.PLUM, AppTheme.CONTRAST -> false
    AppTheme.AUTO -> if (isTv) false else !systemDark
}

/** Thème « Noir (OLED) » choisi (fond #000000). */
fun isOledTheme(theme: AppTheme): Boolean = theme == AppTheme.OLED

/** Le lecteur reste TOUJOURS sombre, quel que soit le réglage. */
fun isLightEffective(themeLight: Boolean, playerShown: Boolean): Boolean = themeLight && !playerShown

/**
 * Vrai si un écran lecteur figure parmi les destinations VISIBLES de la navigation (y compris
 * celle qui sort en transition). État dérivé : un lien profond qui empile un second lecteur
 * avant la sortie du premier ne peut pas le désynchroniser, contrairement à un compteur.
 */
fun isPlayerShown(visibleRoutes: List<String?>): Boolean = visibleRoutes.any { it == com.ultratv.tv.nativeapp.nav.Routes.PLAYER }

/** Publie le thème, l'accent et la taille du texte dans les jetons [Ux] ; à appeler AVANT toute composition qui les lit. */
@Composable
fun ApplyUxTheme(
    theme: AppTheme,
    accent: com.ultratv.tv.nativeapp.ui.design.AccentChoice = com.ultratv.tv.nativeapp.ui.design.AccentChoice.DEFAULT,
    textSize: com.ultratv.tv.nativeapp.ui.design.TextSize = com.ultratv.tv.nativeapp.ui.design.TextSize.NORMAL,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tv = androidx.compose.runtime.remember(ctx) { com.ultratv.tv.nativeapp.ui.common.isTelevision(ctx) }
    val light = isLightTheme(theme, isSystemInDarkTheme(), tv)
    if (Ux.themeLight != light) Ux.themeLight = light
    val oled = isOledTheme(theme)
    if (Ux.themeOled != oled) Ux.themeOled = oled
    if (Ux.themeKind != theme) Ux.themeKind = theme
    if (Ux.accentChoice != accent) Ux.accentChoice = accent
    if (Ux.textScale != textSize.scale) Ux.textScale = textSize.scale
}

@Composable
fun UltraTvTheme(content: @Composable () -> Unit) {
    // Accent choisi par l'utilisateur (le jeu de couleurs Material ne sert qu'aux rares composants Material restants).
    val accent = Ux.Accent; val onAccent = Ux.OnAccent
    val palette = paletteFor(isLightEffective(Ux.themeLight, Ux.playerActive), Ux.themeKind)
    val base = if (palette.light) Light else Dark
    // Schéma Material aligné sur la palette du thème choisi (fonds, surfaces, focus).
    val scheme = androidx.compose.runtime.remember(base, accent, palette) {
        base.copy(
            primary = accent, onPrimary = onAccent,
            background = palette.bg, onBackground = palette.text, surface = palette.card2, onSurface = palette.text,
            surfaceVariant = palette.button, onSurfaceVariant = palette.text2, border = palette.line,
            inverseSurface = palette.focusFill, inverseOnSurface = palette.focusText,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalUxPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

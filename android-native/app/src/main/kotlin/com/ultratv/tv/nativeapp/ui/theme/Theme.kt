package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.ultratv.tv.nativeapp.data.prefs.AppTheme

// ─── Color schemes — driven by the design tokens.
// Accent is the same Ultra red across themes; bg / fg flip per theme.
// Keep Material `primary` = Accent so default focus/CTA tint comes from the
// design system, then override surfaces per palette.

private val Amoled = darkColorScheme(
    primary = UltraTokens.Accent,
    onPrimary = Color.White,
    background = Color(0xFF000000),
    onBackground = UltraTokens.Fg,
    surface = Color(0xFF060608),
    onSurface = UltraTokens.Fg,
    surfaceVariant = Color(0xFF0C0C10),
    onSurfaceVariant = UltraTokens.Fg2,
    border = UltraTokens.Line,
    inverseSurface = Color(0xFF181820),
    inverseOnSurface = UltraTokens.Fg,
)

private val Dark = darkColorScheme(
    primary = UltraTokens.Accent,
    onPrimary = Color.White,
    background = Color(0xFF0A0A0D),
    onBackground = UltraTokens.Fg,
    surface = Color(0xFF101015),
    onSurface = UltraTokens.Fg,
    surfaceVariant = Color(0xFF181820),
    onSurfaceVariant = UltraTokens.Fg2,
    border = UltraTokens.Line,
    inverseSurface = Color(0xFF1F1F28),
    inverseOnSurface = UltraTokens.Fg,
)

private val Blue = darkColorScheme(
    primary = UltraTokens.Accent,
    onPrimary = Color.White,
    background = Color(0xFF070B1A),
    onBackground = UltraTokens.Fg,
    surface = Color(0xFF0C1226),
    onSurface = UltraTokens.Fg,
    surfaceVariant = Color(0xFF141B35),
    onSurfaceVariant = UltraTokens.Fg2,
    border = UltraTokens.Line,
    inverseSurface = Color(0xFF1B2447),
    inverseOnSurface = UltraTokens.Fg,
)

@Composable
fun UltraTvTheme(theme: AppTheme = AppTheme.AMOLED, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        AppTheme.DARK -> Dark
        AppTheme.AMOLED -> Amoled
        AppTheme.BLUE -> Blue
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

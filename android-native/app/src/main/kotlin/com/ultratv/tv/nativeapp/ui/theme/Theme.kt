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
    // TV Button/Card focused state reads inverseOnSurface as the text colour;
    // keeping it near-white made focus white-on-white. A near-black inverse-on
    // means focused state renders as a white pill with dark text — readable.
    inverseOnSurface = Color(0xFF0A0A0D),
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
    // TV Button/Card focused state reads inverseOnSurface as the text colour;
    // keeping it near-white made focus white-on-white. A near-black inverse-on
    // means focused state renders as a white pill with dark text — readable.
    inverseOnSurface = Color(0xFF0A0A0D),
)

// Light scheme — cream / ink palette, mirrored from the prototype's
// `body[data-theme="light"]` block. Same accent red, inverted neutrals.
private val Light = lightColorScheme(
    primary = UltraTokens.Accent,
    onPrimary = Color.White,
    background = Color(0xFFF4F3EF),
    onBackground = Color(0xFF14110E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14110E),
    surfaceVariant = Color(0xFFEBEAE5),
    onSurfaceVariant = Color(0xFF3D3A36),
    border = Color(0x1A14110E),
    inverseSurface = Color(0xFF14110E),
    inverseOnSurface = Color.White,
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
    // TV Button/Card focused state reads inverseOnSurface as the text colour;
    // keeping it near-white made focus white-on-white. A near-black inverse-on
    // means focused state renders as a white pill with dark text — readable.
    inverseOnSurface = Color(0xFF0A0A0D),
)

@Composable
fun UltraTvTheme(theme: AppTheme = AppTheme.AMOLED, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        AppTheme.DARK -> Dark
        AppTheme.AMOLED -> Amoled
        AppTheme.BLUE -> Blue
        AppTheme.LIGHT -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

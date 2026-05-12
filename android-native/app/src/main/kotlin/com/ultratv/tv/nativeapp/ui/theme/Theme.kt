package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.ultratv.tv.nativeapp.data.prefs.AppTheme

private val Dark = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF0B1020),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF131A30),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1B2240),
    onSurfaceVariant = Color(0xFF8A93AC),
    border = Color(0x14FFFFFF),
)

// Pure black AMOLED — saves OLED battery and gives the deepest contrast.
private val Amoled = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF080808),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF8A8A8A),
    border = Color(0x14FFFFFF),
)

// Deep blue — the "Netflix-style" warmer dark with bluer surfaces.
private val Blue = darkColorScheme(
    primary = Color(0xFF82C0FF),
    onPrimary = Color(0xFF001120),
    background = Color(0xFF0A1530),
    onBackground = Color(0xFFE6F0FF),
    surface = Color(0xFF12224A),
    onSurface = Color(0xFFE6F0FF),
    surfaceVariant = Color(0xFF1B3066),
    onSurfaceVariant = Color(0xFF9CB3D9),
    border = Color(0x1F88AAFF),
)

@Composable
fun UltraTvTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        AppTheme.DARK -> Dark
        AppTheme.AMOLED -> Amoled
        AppTheme.BLUE -> Blue
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val UltraColors = darkColorScheme(
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

@Composable
fun UltraTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = UltraColors, content = content)
}

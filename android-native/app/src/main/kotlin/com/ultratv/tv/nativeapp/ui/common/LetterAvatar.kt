package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Coloured rounded square showing the first non-decorative letter of [text].
 * Used as a fallback when a channel / movie / series doesn't expose a logo
 * or poster URL — far less grim than the previous "📺 placeholder" emoji.
 *
 * The background colour is deterministic per text (first-letter hash modulo
 * a palette) so the same item always renders with the same colour.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun LetterAvatar(text: String, fontSize: TextUnit = 28.sp, modifier: Modifier = Modifier) {
    val letter = text.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "·"
    val palette = listOf(
        Color(0xFF1F77B4), Color(0xFFFF7F0E), Color(0xFF2CA02C),
        Color(0xFFD62728), Color(0xFF9467BD), Color(0xFF8C564B),
        Color(0xFFE377C2), Color(0xFF17BECF), Color(0xFF7F7F7F),
    )
    val bg = palette[(letter.hashCode().let { if (it < 0) -it else it }) % palette.size]
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generated channel logo — mirrors the prototype's ChannelLogo: a rounded
 * square with a hue-based linear gradient, two-letter short code, and an
 * optional UHD badge clipping out the bottom-right corner.
 *
 * Falls back to a real logo URL when provided.
 */
@Composable
fun ChannelLogo(
    name: String,
    logoUrl: String? = null,
    short: String? = null,
    hueSeed: Int = name.hashCode(),
    hd: String? = null,                // "HD" or "UHD"
    size: Dp = 44.dp,
    showBadge: Boolean = true,
) {
    val shortCode = (short ?: deriveShort(name)).take(2).uppercase()
    val hueDeg = ((hueSeed % 360) + 360) % 360
    val (lightColor, darkColor) = hueToOkLchPair(hueDeg)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(
                Brush.linearGradient(
                    listOf(lightColor, darkColor),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .border(
                width = 1.dp,
                color = Color(0x14FFFFFF),
                shape = RoundedCornerShape(size * 0.22f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null) {
            AsyncImage(model = logoUrl, contentDescription = name, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                shortCode,
                color = Color.White,
                style = TextStyle(
                    fontSize = (size.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
            )
        }
        if (hd == "UHD" && showBadge) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(UltraTokens.Uhd)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    "UHD",
                    color = Color(0xFF2B1700),
                    style = TextStyle(
                        fontSize = (size.value * 0.16f).coerceAtLeast(7f).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                    ),
                )
            }
        }
    }
}

private fun deriveShort(name: String): String {
    val parts = name.split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
        name.length >= 2 -> name.take(2)
        else -> name
    }
}

/**
 * Maps a hue (0-360°) to a (light, dark) Color pair simulating the prototype's
 * `oklch(60% 0.18 hue) → oklch(25% 0.12 hue)` gradient. We approximate with HSL
 * since Android's Color doesn't expose OKLCH directly, then dampen saturation
 * for the darker stop so the band reads as one tonal family.
 */
private fun hueToOkLchPair(hue: Int): Pair<Color, Color> {
    val light = hslToRgb(hue.toFloat(), 0.55f, 0.45f)
    val dark  = hslToRgb(hue.toFloat(), 0.55f, 0.18f)
    return Color(light) to Color(dark)
}

private fun hslToRgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - kotlin.math.abs(2 * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    fun b(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (b(r1) shl 16) or (b(g1) shl 8) or b(b1)
}

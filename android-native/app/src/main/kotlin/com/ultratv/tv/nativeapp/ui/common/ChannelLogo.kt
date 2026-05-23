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
    epgChannelId: String? = null,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Override from local folder if the user picked one and a file matches.
    val effectiveUrl = androidx.compose.runtime.remember(name, epgChannelId, com.ultratv.tv.nativeapp.data.repo.LocalLogos.treeUri) {
        com.ultratv.tv.nativeapp.data.repo.LocalLogos.resolveByName(ctx, epgChannelId, name)?.toString() ?: logoUrl
    }
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
        if (effectiveUrl != null) {
            AsyncImage(model = effectiveUrl, contentDescription = name, modifier = Modifier.fillMaxSize())
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

private fun hueToOkLchPair(hue: Int): Pair<Color, Color> = HueGradient.pair(hue)

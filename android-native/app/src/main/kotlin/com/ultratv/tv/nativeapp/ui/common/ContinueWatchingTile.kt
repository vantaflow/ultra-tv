package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ultratv.tv.nativeapp.ui.components.UltraIcon
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens

/**
 * 16:9 continue-watching tile matching the prototype's <ContinueTile>: large
 * play badge top-left, remaining-time chip top-right, serif title + episode
 * label bottom, accent progress bar across the entire bottom edge.
 */
@Composable
fun ContinueWatchingTile(
    title: String,
    poster: String?,
    epLabel: String?,
    remaining: String?,
    progress: Float, // 0..1
    hueSeed: Int = title.hashCode(),
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(280),
        label = "tile-scale",
    )
    val hue = ((hueSeed % 360) + 360) % 360
    val (light, dark) = hueGradient(hue)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(300f / 170f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(light, dark)))
            .then(
                if (focused) Modifier.border(3.dp, UltraTokens.Accent, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = title, modifier = Modifier.fillMaxSize())
        }
        // Dark gradient bottom 30%
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color(0xCC000000),
                    )
                )
        )
        // Top row: play badge + remaining chip
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x8C000000))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) { UltraIcon(UltraIcon.Play, size = 14.dp, color = Color.White) }
            if (remaining != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x8C000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        remaining,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = UltraFonts.Mono,
                    )
                }
            }
        }
        // Bottom: title + episode label
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 18.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 22.sp,
                fontFamily = UltraFonts.Serif,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
            if (epLabel != null) {
                Text(
                    epLabel,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = UltraFonts.Sans,
                    maxLines = 1,
                )
            }
        }
        // Progress bar
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0x26FFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(UltraTokens.Accent)
            )
        }
    }
}

private fun hueGradient(hue: Int): Pair<Color, Color> {
    val light = HueGradient.hsl(hue, 0.6f, 0.38f)
    val dark = HueGradient.hsl((hue + 30) % 360, 0.45f, 0.15f)
    return light to dark
}

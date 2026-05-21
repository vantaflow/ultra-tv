package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens

/**
 * Poster card: Apple-TV-style focus scale + accent ring, editorial serif title
 * overlay matching the design bundle's <Poster>.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    title: String,
    poster: String?,
    subtitle: String? = null,
    imageHeight: Dp = 300.dp,
    @Suppress("UNUSED_PARAMETER") placeholderEmoji: String = "🎬",
    aspect: Float = 2f / 3f,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 280),
        label = "poster-scale",
    )

    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
        modifier = Modifier
            .scale(scale)
            .then(
                if (focused) Modifier.border(3.dp, UltraTokens.Accent, RoundedCornerShape(14.dp))
                else Modifier
            ),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .let { if (aspect > 0f) it.aspectRatio(aspect) else it.height(imageHeight) }
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF231A28), Color(0xFF0B0A12))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (poster != null) {
                    AsyncImage(model = poster, contentDescription = title, modifier = Modifier.fillMaxSize())
                } else {
                    LetterAvatar(text = title, fontSize = 48.sp, modifier = Modifier.fillMaxSize())
                }
                // Bottom scrim with editorial title
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color(0xCC000000),
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = UltraFonts.Serif,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                        )
                    }
                }
                // UHD / HDR / Live badge
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(UltraTokens.Uhd)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            badge,
                            color = Color(0xFF2B1700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }
            }
        }
    }
}

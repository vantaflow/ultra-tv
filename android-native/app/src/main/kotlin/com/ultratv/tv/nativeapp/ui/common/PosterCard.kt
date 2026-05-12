package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage

/**
 * Poster card with a "Netflix-style" focus scale-up animation. Hooks the Card's
 * interaction source to animate scale 1.0 → 1.08 in ~150ms when focused.
 *
 * The scale modifier is on the Card itself, so the focus ring grows with the
 * artwork (no double-outline glitch).
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    title: String,
    poster: String?,
    subtitle: String? = null,
    imageHeight: Dp = 220.dp,
    placeholderEmoji: String = "🎬",
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 160),
        label = "poster-scale",
    )

    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
        modifier = Modifier.scale(scale),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (poster != null) {
                    AsyncImage(model = poster, contentDescription = title, modifier = Modifier.fillMaxSize())
                } else {
                    Text(placeholderEmoji, fontSize = 64.sp)
                }
            }
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

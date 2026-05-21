package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ultratv.tv.nativeapp.ui.components.UltraIcon
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import com.ultratv.tv.nativeapp.ui.theme.UltraType

/**
 * Editorial hero — serif title, accent eyebrow, white CTA with accent ring.
 * Falls back to a radial gradient when no image is provided. Sized for the
 * 1920×720 zone the design bundle defines (paddingLeft 80, top 110).
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun HeroBanner(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    image: String? = null,
    rating: Int? = null,
    meta: List<String> = emptyList(),
    synopsis: String? = null,
    cast: String? = null,
    primaryLabel: String = "Lecture",
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    rightContent: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(720.dp),
    ) {
        // Backdrop
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3C1F26),
                            Color(0xFF160910),
                            Color(0xFF050203),
                        ),
                        center = Offset(1450f, 360f),
                        radius = 1100f,
                    )
                ),
        )
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Left-side dim
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF2000000),
                        0.45f to Color(0x73000000),
                        0.8f to Color.Transparent,
                    )
                ),
        )
        // Vignette to bg
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.92f to UltraTokens.Surface1,
                        1f to Color(0xFF000000),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 760.dp)
                .padding(start = 80.dp, top = 110.dp, end = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (eyebrow != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 24.dp, height = 1.dp).background(UltraTokens.Accent))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        eyebrow,
                        color = UltraTokens.Accent,
                        fontSize = 13.sp,
                        letterSpacing = 2.3.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
            Text(
                title,
                color = UltraTokens.Fg,
                fontFamily = UltraFonts.Serif,
                fontSize = 84.sp,
                lineHeight = 84.sp,
                letterSpacing = (-2.1).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    subtitle,
                    color = UltraTokens.Fg2,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 3,
                )
            }
            if (rating != null || meta.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$rating",
                                color = UltraTokens.Accent,
                                fontFamily = UltraFonts.Mono,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "match",
                                color = UltraTokens.Fg3,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    meta.forEach { m ->
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.size(3.dp).clip(CircleShape).background(UltraTokens.Fg4))
                        Spacer(Modifier.width(14.dp))
                        Text(m, color = UltraTokens.Fg2, fontSize = 13.sp)
                    }
                }
            }
            if (synopsis != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    synopsis,
                    color = UltraTokens.Fg2,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    maxLines = 3,
                    modifier = Modifier.widthIn(max = 620.dp),
                )
            }
            if (cast != null) {
                Spacer(Modifier.height(8.dp))
                Text(cast, color = UltraTokens.Fg3, fontSize = 13.sp, letterSpacing = 0.3.sp)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroPrimary(primaryLabel, onPrimary)
                if (secondaryLabel != null && onSecondary != null) {
                    HeroSecondary(secondaryLabel, UltraIcon.Info, onSecondary)
                }
            }
        }

        if (rightContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 80.dp, top = 110.dp),
            ) { rightContent() }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ButtonDefaults.colors(
            containerColor = UltraTokens.CtaBg,
            contentColor = UltraTokens.CtaFgOnCta,
        ),
        modifier = Modifier
            .border(3.dp, UltraTokens.Accent, RoundedCornerShape(14.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UltraIcon(UltraIcon.Play, size = 18.dp, color = UltraTokens.CtaFgOnCta)
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = UltraTokens.CtaFgOnCta)
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroSecondary(label: String, icon: UltraIcon, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ButtonDefaults.colors(
            containerColor = UltraTokens.Surface2,
            contentColor = UltraTokens.Fg,
        ),
        modifier = Modifier.border(1.dp, UltraTokens.Line2, RoundedCornerShape(14.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UltraIcon(icon, size = 16.dp, color = UltraTokens.Fg)
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = UltraTokens.Fg)
        }
    }
}

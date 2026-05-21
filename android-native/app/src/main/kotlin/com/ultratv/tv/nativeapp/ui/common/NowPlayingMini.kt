package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens

data class NowPlayingItem(
    val channelNumber: Int,
    val channelName: String,
    val channelLogoUrl: String?,
    val channelShort: String?,
    val hueSeed: Int,
    val hd: String?,
    val nowTitle: String,
    val endsInMinutes: Int,
)

/**
 * Right-side "En direct maintenant" column shown on top of HeroBanner.
 * Renders up to N mini cards with channel logo, programme title and a thin
 * accent progress bar.
 */
@Composable
fun NowPlayingMiniColumn(
    items: List<NowPlayingItem>,
    onSeeAll: (() -> Unit)? = null,
) {
    Column(Modifier.widthIn(min = 280.dp, max = 320.dp)) {
        Text(
            "EN DIRECT MAINTENANT",
            color = UltraTokens.Fg3,
            fontSize = 11.sp,
            letterSpacing = 2.3.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        items.forEach { p ->
            NowMini(p)
            Spacer(Modifier.height(10.dp))
        }
        if (onSeeAll != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Voir tout le guide  →",
                color = UltraTokens.Fg3,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun NowMini(p: NowPlayingItem) {
    val pct = (100 - (p.endsInMinutes.toFloat() / 90f) * 100f).coerceIn(0f, 100f) / 100f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color(0x66000000))
            .border(1.dp, UltraTokens.Line, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(
            name = p.channelName,
            logoUrl = p.channelLogoUrl,
            short = p.channelShort,
            hueSeed = p.hueSeed,
            hd = p.hd,
            size = 36.dp,
            showBadge = false,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02d".format(p.channelNumber),
                    color = UltraTokens.Fg2,
                    fontSize = 11.sp,
                    fontFamily = UltraFonts.Mono,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "· ${p.channelName}",
                    color = UltraTokens.Fg3,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                p.nowTitle,
                color = UltraTokens.Fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(androidx.compose.ui.graphics.Color(0x1AFFFFFF)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(pct)
                            .height(2.dp)
                            .background(UltraTokens.Accent),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${p.endsInMinutes}m",
                    color = UltraTokens.Fg3,
                    fontSize = 10.sp,
                    fontFamily = UltraFonts.Mono,
                )
            }
        }
    }
}

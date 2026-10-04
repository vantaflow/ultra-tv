package com.ultratv.tv.nativeapp.ui.player.zap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.player.PlayerExtraStrings

/** Saisie du numéro (maquette Zapping) : trois cases de 84 × 108, la prochaine vide cerclée d'accent, l'aperçu dessous. */
@Composable
fun ZapNumberBox(preview: NumberPreview, X: PlayerExtraStrings, modifier: Modifier = Modifier) {
    val slots = maxOf(3, preview.digits.length + 1).coerceAtMost(4)
    Column(
        modifier.clip(RoundedCornerShape(24.design)).background(Color(0xE60A0A0C)).padding(horizontal = 36.design, vertical = 28.design),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.design),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.design)) {
            for (i in 0 until slots) {
                val ch = preview.digits.getOrNull(i)
                Box(
                    Modifier.width(84.design).height(108.design).clip(RoundedCornerShape(18.design))
                        .background(if (ch != null) Ux.Surface2 else Ux.SurfaceDeep)
                        .then(if (ch == null && i == preview.digits.length) Modifier.border(3.design, Ux.Accent, RoundedCornerShape(18.design)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ch != null) Text(ch.toString(), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 64.spx)
                }
            }
        }
        Text(
            if (preview.channelTitle == null) X.noChannelNumber else listOfNotNull(preview.digits, preview.channelTitle, preview.nowTitle).joinToString(" · "),
            color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Liste « Récentes » en bas d'écran (maquette Zapping) : cartes de 260 × 152, la chaîne en cours est blanche. */
@Composable
fun ZapRecentStrip(entries: List<RecentEntry>, currentRemoteId: String?, X: PlayerExtraStrings, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Column(
        modifier.fillMaxWidth().background(Color(0xE60A0A0C)).padding(start = 96.design, end = 96.design, top = 32.design, bottom = 54.design),
        verticalArrangement = Arrangement.spacedBy(18.design),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(X.recent, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1)
            Text(X.zapHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.design)) {
            entries.take(6).forEach { r ->
                val cur = r.channel.remoteId == currentRemoteId
                Column(
                    Modifier.width(260.design).height(152.design).clip(RoundedCornerShape(18.design)).background(if (cur) Color(0xFFE4E4E7) else Ux.Surface2).padding(18.design),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.design)) {
                        LogoBox(r.channel.logo, r.channel.title, Modifier.width(64.design).height(42.design), radius = 8, pad = 4, bg = if (cur) Color(0xFFE4E4E7) else Ux.Surface)
                        if (r.number > 0) Text(r.number.toString(), color = if (cur) Color(0xFF3F3F46) else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1)
                    }
                    Text(r.channel.title, color = if (cur) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.now.orEmpty(), color = if (cur) Color(0xFF3F3F46) else Ux.Text3, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

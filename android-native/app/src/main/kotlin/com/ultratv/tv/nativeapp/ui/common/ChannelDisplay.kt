package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/** Largeur de la colonne des noms de chaînes du Guide (réglage par profil). Défaut : Normale, plus large que l'ancienne colonne fixe. */
enum class GuideNameWidth(val key: String, val tvPx: Int, val touchDp: Int) {
    NARROW("narrow", 260, 116),
    NORMAL("normal", 340, 148),
    WIDE("wide", 440, 184),
    ;

    companion object {
        fun parse(key: String?): GuideNameWidth = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** Réglages d'affichage des chaînes, fournis à toute l'interface (Direct, Guide, aperçus). */
data class ChannelDisplay(val showLogos: Boolean = true, val guideNameWidth: GuideNameWidth = GuideNameWidth.NORMAL)

val LocalChannelDisplay = compositionLocalOf { ChannelDisplay() }

/** Numéro d'affichage d'une chaîne : séquentiel stable de la catégorie, sinon celui de la source ; null si inconnu. */
fun channelNumber(seq: Int, num: Int): Int? = when {
    seq > 0 -> seq
    num > 0 -> num
    else -> null
}

/** Libellé « numéro · nom » d'une chaîne (le numéro est omis s'il est inconnu). */
fun channelHeading(number: Int?, title: String): String = if (number == null) title else "$number · $title"

/** Largeur (px de maquette) de la zone du nom dans une ligne du Guide TV : la colonne moins la marge de fin, le logo et son écart. */
fun guideNameAreaPx(columnPx: Int, showLogos: Boolean): Int = columnPx - 16 - if (showLogos) 64 + 14 else 0

/**
 * Grand repère d'une chaîne (aperçus) : son logo, ou, logos masqués, le numéro et le nom sur le fond de l'aperçu.
 * [number] est le numéro à afficher (voir [channelNumber]).
 */
@Composable
fun ChannelBigMark(c: ChannelEntity, number: Int?, modifier: Modifier, radius: Int, pad: Int, bg: Color, touchScale: Boolean = false) {
    if (LocalChannelDisplay.current.showLogos) { LogoBox(c.logo, c.title, modifier, radius = radius, pad = pad, bg = bg); return }
    Box(modifier.background(bg, RoundedCornerShape(radius.design)).padding(horizontal = 24.design), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.design)) {
            if (number != null) androidx.tv.material3.Text(number.toString(), color = Ux.Muted, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touchScale) 22.sp else 40.spx, maxLines = 1)
            androidx.tv.material3.Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touchScale) 18.sp else 36.spx, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

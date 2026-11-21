package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design

/** Arrêts (position, alpha) des deux voiles posés sur une image plein écran. */
data class ScrimStops(val startAlpha: List<Pair<Float, Float>>, val bottomAlpha: List<Pair<Float, Float>>)

/** Valeurs de la maquette : voile de début (62 % de la largeur) et voile bas (40 % de la hauteur). */
fun imageScrimStops() = ScrimStops(
    startAlpha = listOf(0f to 1f, 0.45f to 0.86f, 1f to 0f),
    bottomAlpha = listOf(0f to 0f, 1f to 1f),
)

/** Le voile de début est à gauche en LTR, à droite en RTL (toujours du côté du texte). */
fun scrimSideIsLeft(rtl: Boolean): Boolean = !rtl

private const val START_SPAN = 0.62f
private const val BOTTOM_SPAN = 0.40f
/** Sur une image, le voile reste sombre dans les deux thèmes. */
private val ScrimDark = Color(0xFF0A0C10)

/**
 * Voiles de lisibilité sur une image : dessinés une fois, pinceaux recréés seulement si la taille change.
 * Le voile bas fond vers [pageColor] (appelant : `Ux.Bg`).
 */
fun Modifier.imageScrims(start: Boolean = true, bottom: Boolean = true, rtl: Boolean, pageColor: Color): Modifier =
    drawWithCache {
        val stops = imageScrimStops()
        val w = size.width
        val h = size.height
        val left = scrimSideIsLeft(rtl)
        val startBrush = if (start) {
            val a = if (left) 0f else w
            val b = if (left) w * START_SPAN else w * (1f - START_SPAN)
            Brush.linearGradient(
                colorStops = stops.startAlpha.map { (p, al) -> p to ScrimDark.copy(alpha = al) }.toTypedArray(),
                start = Offset(a, 0f), end = Offset(b, 0f),
            )
        } else null
        val bottomBrush = if (bottom) Brush.verticalGradient(
            colorStops = stops.bottomAlpha.map { (p, al) -> p to pageColor.copy(alpha = al) }.toTypedArray(),
            startY = h * (1f - BOTTOM_SPAN), endY = h,
        ) else null
        onDrawBehind {
            startBrush?.let { drawRect(it) }
            bottomBrush?.let { drawRect(it, topLeft = Offset(0f, h * (1f - BOTTOM_SPAN))) }
        }
    }

/** Pastille « EN DIRECT » (texte déjà traduit). */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.design)).background(Ux.Accent).padding(horizontal = 12.design, vertical = 4.design)) {
        Text(LocalDs.current.live, color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1)
    }
}

/** Étiquette de test du remplissage de [ProgressLine]. */
const val PROGRESS_FILL_TAG = "progress_fill"

/** Fraction bornée à 0..1 ; NaN vaut 0. */
fun progressFraction(f: Float): Float = if (f.isNaN()) 0f else f.coerceIn(0f, 1f)

/** Barre de progression de 6 unités : piste blanche à 22 %, remplissage accent. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val f = progressFraction(fraction)
    Box(modifier.height(6.design).clip(RoundedCornerShape(3.design)).background(Color.White.copy(alpha = 0.22f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Ux.Accent).testTag(PROGRESS_FILL_TAG))
    }
}

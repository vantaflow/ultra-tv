package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
const val BOTTOM_SPAN = 0.40f
/** Sur une image, le voile reste sombre dans les deux thèmes. */
private val ScrimDark = Color(0xFF0A0C10)

/**
 * Voiles de lisibilité sur une image : dessinés une fois, pinceaux recréés seulement si la taille change.
 * Le voile bas fond vers [pageColor] sur la part [bottomSpan] de la hauteur (par défaut 40 %).
 */
fun Modifier.imageScrims(start: Boolean = true, bottom: Boolean = true, rtl: Boolean, pageColor: Color, bottomSpan: Float = BOTTOM_SPAN): Modifier =
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
            startY = h * (1f - bottomSpan), endY = h,
        ) else null
        onDrawBehind {
            startBrush?.let { drawRect(it) }
            bottomBrush?.let { drawRect(it, topLeft = Offset(0f, h * (1f - bottomSpan)), size = Size(w, h * bottomSpan)) }
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

/** Couleurs d'un bouton posé sur une image : identiques dans les deux thèmes (l'image est toujours voilée de sombre). */
data class OnImageColors(val bg: Color, val ink: Color)

private val OnImageInk = Color(0xFF0A0C10)

/** Repos : primaire blanc / texte sombre, secondaire blanc à 16 % / texte blanc. Focus (les deux) : blanc / texte sombre. */
fun onImageButtonColors(primary: Boolean, focused: Boolean): OnImageColors = when {
    focused || primary -> OnImageColors(Color.White, OnImageInk)
    else -> OnImageColors(Color.White.copy(alpha = 0.16f), Color.White)
}

/** Anneau blanc de 5 puis liseré sombre de 5, dessinés DANS le bouton (déjà rogné à sa forme) : aucun calque en plus. */
private fun Modifier.onImageRing(heightPx: Int): Modifier = this
    .border(5.design, Color.White, RoundedCornerShape((heightPx / 2).design))
    .padding(5.design)
    .border(5.design, OnImageInk, RoundedCornerShape((heightPx / 2 - 5).design))

/**
 * Bouton pilule posé sur une image (bandeau, fiche, lecteur). Construit sur [FocusSurface] comme [PillButton] (même
 * échelle au focus, même gestion tactile et cible minimale). Au focus : anneau blanc de 5, séparé du bouton par un
 * liseré sombre de 5 ; seul le bouton focalisé le porte, donc le focus se lit dans les deux thèmes.
 */
@Composable
fun OnImageButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
    iconPath: String? = null, iconFill: Boolean = false, heightPx: Int = 76,
) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val rest = onImageButtonColors(primary, focused = false)
    val on = onImageButtonColors(primary, focused = true)
    val fontPx = if (primary) 28 else 26
    val hPadPx = if (primary) 40 else 36
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape((heightPx / 2).design), bg = rest.bg, focusedBg = on.bg, ringWidth = 0.dp,
        modifier = if (touch) modifier.heightIn(min = heightPx.designT) else modifier.height(heightPx.designT),
    ) { f ->
        val ink = if (f) on.ink else rest.ink
        val ring = if (f && !touch) Modifier.onImageRing(heightPx) else Modifier
        Row(
            Modifier.then(ring).then(if (touch) Modifier.padding(horizontal = hPadPx.design, vertical = 8.design).heightIn(min = heightPx.designT) else Modifier.padding(horizontal = hPadPx.design).fillMaxHeight()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconPath != null) {
                DIcon(iconPath, (fontPx - 4).design, ink, fill = iconFill, strokeWidth = 2.2f)
                Spacer(Modifier.width(14.design))
            }
            Text(text, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = fontPx.spx, maxLines = if (touch) 2 else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/**
 * Bouton rond, icône seule, posé sur une image : mêmes couleurs, même anneau de focus et même taille que
 * [OnImageButton] (carré de [heightPx]). [description] est lue par les lecteurs d'écran ; [state] (facultatif) dit l'état
 * courant (ex. « dans la liste »).
 */
@Composable
fun OnImageIconButton(
    iconPath: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconFill: Boolean = false,
    heightPx: Int = 76, state: String? = null,
) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val rest = onImageButtonColors(primary = false, focused = false)
    val on = onImageButtonColors(primary = false, focused = true)
    FocusSurface(
        onClick = onClick, shape = CircleShape, bg = rest.bg, focusedBg = on.bg, ringWidth = 0.dp,
        modifier = modifier.size(heightPx.designT).semantics {
            contentDescription = description
            if (state != null) stateDescription = state
        },
    ) { f ->
        val ring = if (f && !touch) Modifier.onImageRing(heightPx) else Modifier
        Box(Modifier.then(ring).fillMaxSize(), contentAlignment = Alignment.Center) {
            DIcon(iconPath, 28.design, if (f) on.ink else rest.ink, fill = iconFill, strokeWidth = 2.6f)
        }
    }
}

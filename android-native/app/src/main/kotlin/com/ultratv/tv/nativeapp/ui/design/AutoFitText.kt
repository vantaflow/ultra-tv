package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * Texte de l'application (même signature que celui d'androidx.tv.material3), avec UNE règle commune à tous les écrans et
 * tous les appareils : un texte limité à une ligne (« … ») PASSE À LA LIGNE quand il dépasse (deux lignes). Si son
 * conteneur n'a pas la hauteur de deux lignes, il reste sur une ligne (jamais de chevauchement) ; sur téléphone et
 * tablette, il réduit alors sa taille (jusqu'à [AutoFit.MIN_SCALE]) avant d'être coupé.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = androidx.tv.material3.LocalTextStyle.current,
) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val base = if (fontSize.isSpecified) fontSize else style.fontSize
    if (!AutoFit.applies(maxLines, overflow, softWrap)) {
        androidx.tv.material3.Text(
            text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
            letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
            softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
        )
        return
    }
    // 1) deux lignes ; 2) le conteneur n'a pas la hauteur de deux lignes : retour à une ligne ;
    // 3) téléphone / tablette : réduction de la taille (jusqu'à 80 %) avant « … ».
    var lines by remember(text, base) { mutableIntStateOf(AutoFit.WRAP_LINES) }
    var scale by remember(text, base) { mutableFloatStateOf(1f) }
    var ready by remember(text, base) { mutableStateOf(false) }
    val size = if (base.isSpecified) base * scale else base
    androidx.tv.material3.Text(
        text = text, modifier = modifier.drawWithContent { if (ready) drawContent() }, color = color, fontSize = size,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing * scale else letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = lines, minLines = minLines,
        onTextLayout = { r ->
            val cut = r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)
            when (val step = AutoFit.next(lines, scale, cut = cut, tooTall = r.didOverflowHeight && r.lineCount > 1, touch = touch && base.isSpecified)) {
                null -> ready = true
                else -> { lines = step.first; scale = step.second }
            }
            onTextLayout(r)
        },
        style = style,
    )
}

/** Règle d'ajustement (pure, testée). */
object AutoFit {
    const val MIN_SCALE = 0.8f
    const val WRAP_LINES = 2
    private const val STEP = 0.05f

    /** Concerne les textes « une ligne + … » qui peuvent passer à la ligne (pas les pastilles en softWrap = false). */
    fun applies(maxLines: Int, overflow: TextOverflow, softWrap: Boolean): Boolean =
        maxLines == 1 && overflow == TextOverflow.Ellipsis && softWrap

    /**
     * Étape suivante, ou null quand le rendu est définitif. [tooTall] : le conteneur n'a pas la hauteur des lignes demandées.
     * [cut] : la dernière ligne finit par « … ».
     */
    fun next(lines: Int, scale: Float, cut: Boolean, tooTall: Boolean, touch: Boolean): Pair<Int, Float>? = when {
        tooTall && lines > 1 -> 1 to scale
        cut && lines == 1 && touch && scale > MIN_SCALE -> 1 to maxOf(MIN_SCALE, scale - STEP)
        else -> null
    }
}

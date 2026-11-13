package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * Texte de l'application (même signature que celui d'androidx.tv.material3), avec UNE règle commune à tous les écrans :
 * sur téléphone et tablette, un texte limité à une ligne et terminé par « … » réduit d'abord sa taille (jusqu'à
 * [AutoFit.MIN_SCALE]) pour tenir en entier avant d'être coupé. La hauteur de la ligne ne change pas : aucune mise en
 * page n'est décalée. Sur TV, comportement d'origine à l'identique.
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
    if (!AutoFit.applies(touch, maxLines, overflow, base.isSpecified)) {
        androidx.tv.material3.Text(
            text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
            letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
            softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
        )
        return
    }
    var scale by remember(text, base) { mutableFloatStateOf(1f) }
    var ready by remember(text, base) { mutableStateOf(false) }
    androidx.tv.material3.Text(
        text = text, modifier = modifier.drawWithContent { if (ready) drawContent() }, color = color, fontSize = base * scale,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing * scale else letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = { r ->
            val cut = r.hasVisualOverflow || (r.lineCount > 0 && r.isLineEllipsized(0))
            val next = AutoFit.nextScale(scale, cut)
            if (next != scale) scale = next else ready = true
            onTextLayout(r)
        },
        style = style,
    )
}

/** Règle d'ajustement (pure, testée). */
object AutoFit {
    const val MIN_SCALE = 0.8f
    private const val STEP = 0.05f

    fun applies(touch: Boolean, maxLines: Int, overflow: TextOverflow, sizeKnown: Boolean): Boolean =
        touch && maxLines == 1 && overflow == TextOverflow.Ellipsis && sizeKnown

    /** Échelle suivante : on réduit tant que le texte déborde, sans descendre sous [MIN_SCALE]. */
    fun nextScale(current: Float, overflowing: Boolean): Float =
        if (!overflowing || current <= MIN_SCALE) current else maxOf(MIN_SCALE, current - STEP)
}

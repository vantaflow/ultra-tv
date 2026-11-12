package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette de la nouvelle interface (maquettes v2), sombre et claire. Source unique des couleurs : les jetons [Ux]
 * y puisent, et [LocalUxPalette] la publie à tout l'arbre Compose (le lecteur force [DarkPalette] : il est posé sur la vidéo).
 */
@Immutable
data class UxPalette(
    val light: Boolean,
    val bg: Color, val rail: Color, val card: Color, val card2: Color, val button: Color, val tile: Color,
    val text: Color, val text2: Color, val text3: Color, val muted: Color,
    val accent: Color, val accentText: Color, val nowTint: Color,
    /** Focus : fond et texte posés dessus (sombre = fond clair / texte foncé ; clair = l'inverse). */
    val focusFill: Color, val focusText: Color, val focusText2: Color,
    val line: Color, val lineKey: Color, val muted2: Color, val err: Color, val scrim: Color,
)

val DarkPalette = UxPalette(
    light = false,
    bg = Color(0xFF0A0C10), rail = Color(0xFF0E1117), card = Color(0xFF161A22), card2 = Color(0xFF12151C),
    button = Color(0xFF1C2029), tile = Color(0xFF2A3140),
    text = Color(0xFFF4F5F7), text2 = Color(0xFFC9CDD6), text3 = Color(0xFFA9AFBC), muted = Color(0xFF7B8291),
    accent = Color(0xFFFF3B47), accentText = Color(0xFFFF8088), nowTint = Color(0xFF2A1418),
    focusFill = Color(0xFFF4F5F7), focusText = Color(0xFF0A0C10), focusText2 = Color(0xFF3A4252),
    line = Color(0xFF2A3140), lineKey = Color(0xFF4A5263), muted2 = Color(0xFF5B6375), err = Color(0xFFFF8A8A),
    scrim = Color(0xB80A0C10),
)

val LightPalette = UxPalette(
    light = true,
    bg = Color(0xFFF5F6F8), rail = Color(0xFFFFFFFF), card = Color(0xFFFFFFFF), card2 = Color(0xFFFFFFFF),
    button = Color(0xFFE8EBF0), tile = Color(0xFFDCE1EA),
    text = Color(0xFF12141A), text2 = Color(0xFF3A4252), text3 = Color(0xFF5B6375), muted = Color(0xFF6B7385),
    accent = Color(0xFFE0202C), accentText = Color(0xFFC4161F), nowTint = Color(0xFFFBE3E5),
    focusFill = Color(0xFF12141A), focusText = Color(0xFFF5F6F8), focusText2 = Color(0xFFC9CDD6),
    line = Color(0xFFDCE1EA), lineKey = Color(0xFF8A93A6), muted2 = Color(0xFFB4BAC7), err = Color(0xFFB3261E),
    scrim = Color(0xCCF5F6F8),
)

fun paletteFor(light: Boolean): UxPalette = if (light) LightPalette else DarkPalette

/** Palette courante pour les écrans qui préfèrent la lire par CompositionLocal ; défaut sombre. */
val LocalUxPalette = staticCompositionLocalOf { DarkPalette }

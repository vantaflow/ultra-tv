package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ultratv.tv.nativeapp.data.prefs.AppTheme

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
    /** Texte posé sur l'accent (blanc, ou foncé sur un accent clair comme le jaune ou le cyan). */
    val onAccent: Color = Color.White,
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

/**
 * Thème « Noir (OLED) » : fond #000000 (pixels éteints sur écran OLED), surfaces très sombres. Même disposition que
 * [DarkPalette] (texte, accent, focus inchangés : tous les contrastes du thème sombre ne font qu'augmenter).
 */
val OledPalette = DarkPalette.copy(
    bg = Color(0xFF000000), rail = Color(0xFF050506), card = Color(0xFF0D0F13), card2 = Color(0xFF08090B),
    button = Color(0xFF14171D), tile = Color(0xFF20242D),
    line = Color(0xFF1C2029), scrim = Color(0xE6000000),
)

/** Bleu nuit : fond bleu très sombre, accent cyan. */
val MidnightPalette = UxPalette(
    light = false,
    bg = Color(0xFF070B16), rail = Color(0xFF0A1020), card = Color(0xFF111A2E), card2 = Color(0xFF0D1526),
    button = Color(0xFF18233A), tile = Color(0xFF22304D),
    text = Color(0xFFF2F5FA), text2 = Color(0xFFC3CCDD), text3 = Color(0xFF9AA6BD), muted = Color(0xFF6F7C95),
    accent = Color(0xFF22B8E6), accentText = Color(0xFF7FD7F2), nowTint = Color(0xFF0F2A3A),
    focusFill = Color(0xFFF2F5FA), focusText = Color(0xFF070B16), focusText2 = Color(0xFF2E3A52),
    line = Color(0xFF22304D), lineKey = Color(0xFF41506E), muted2 = Color(0xFF52607C), err = Color(0xFFFF8A8A),
    scrim = Color(0xB8070B16), onAccent = Color(0xFF04121A),
)

/** Forêt : fond vert sombre, accent émeraude. */
val ForestPalette = UxPalette(
    light = false,
    bg = Color(0xFF07110D), rail = Color(0xFF0A1712), card = Color(0xFF10201A), card2 = Color(0xFF0C1914),
    button = Color(0xFF16291F), tile = Color(0xFF20382C),
    text = Color(0xFFF1F6F3), text2 = Color(0xFFC2D1C8), text3 = Color(0xFF99AFA2), muted = Color(0xFF6E8577),
    accent = Color(0xFF10B981), accentText = Color(0xFF6EE7B7), nowTint = Color(0xFF0E2A1E),
    focusFill = Color(0xFFF1F6F3), focusText = Color(0xFF07110D), focusText2 = Color(0xFF2C3E34),
    line = Color(0xFF20382C), lineKey = Color(0xFF3F5A4B), muted2 = Color(0xFF51695B), err = Color(0xFFFF8A8A),
    scrim = Color(0xB807110D), onAccent = Color(0xFF04140D),
)

/** Prune : fond aubergine, accent mauve. */
val PlumPalette = UxPalette(
    light = false,
    bg = Color(0xFF100A14), rail = Color(0xFF150E1B), card = Color(0xFF1F1527), card2 = Color(0xFF19111F),
    button = Color(0xFF281C32), tile = Color(0xFF362645),
    text = Color(0xFFF6F2F8), text2 = Color(0xFFD4C9DB), text3 = Color(0xFFAE9FBA), muted = Color(0xFF84738F),
    accent = Color(0xFFB65CF0), accentText = Color(0xFFD9A6FA), nowTint = Color(0xFF2A1638),
    focusFill = Color(0xFFF6F2F8), focusText = Color(0xFF100A14), focusText2 = Color(0xFF3E2E4A),
    line = Color(0xFF362645), lineKey = Color(0xFF574368), muted2 = Color(0xFF685579), err = Color(0xFFFF8A8A),
    scrim = Color(0xB8100A14),
)

/** Sable : thème CLAIR chaud, accent orange. */
val SandPalette = UxPalette(
    light = true,
    bg = Color(0xFFF7F2EA), rail = Color(0xFFFFFCF7), card = Color(0xFFFFFCF7), card2 = Color(0xFFFFFCF7),
    button = Color(0xFFEDE4D6), tile = Color(0xFFE2D6C3),
    text = Color(0xFF1E1A14), text2 = Color(0xFF4A4134), text3 = Color(0xFF6B5F4D), muted = Color(0xFF7E715D),
    accent = Color(0xFFC2410C), accentText = Color(0xFFA3360A), nowTint = Color(0xFFFBE4D6),
    focusFill = Color(0xFF1E1A14), focusText = Color(0xFFF7F2EA), focusText2 = Color(0xFFD6CBB9),
    line = Color(0xFFE2D6C3), lineKey = Color(0xFF9A8B73), muted2 = Color(0xFFBFB19B), err = Color(0xFFB3261E),
    scrim = Color(0xCCF7F2EA),
)

/** Contraste élevé : noir et blanc purs, accent jaune (lisibilité maximale). */
val ContrastPalette = UxPalette(
    light = false,
    bg = Color(0xFF000000), rail = Color(0xFF000000), card = Color(0xFF111111), card2 = Color(0xFF0A0A0A),
    button = Color(0xFF1A1A1A), tile = Color(0xFF2A2A2A),
    text = Color(0xFFFFFFFF), text2 = Color(0xFFFFFFFF), text3 = Color(0xFFE0E0E0), muted = Color(0xFFC8C8C8),
    accent = Color(0xFFFFD60A), accentText = Color(0xFFFFE45C), nowTint = Color(0xFF2A2400),
    focusFill = Color(0xFFFFFFFF), focusText = Color(0xFF000000), focusText2 = Color(0xFF1A1A1A),
    line = Color(0xFF5A5A5A), lineKey = Color(0xFF9A9A9A), muted2 = Color(0xFF8A8A8A), err = Color(0xFFFF9A9A),
    scrim = Color(0xE6000000), onAccent = Color(0xFF000000),
)

/** Palette d'un thème : clair > Noir (OLED) > sombre. [light] est déjà « effectif » (le lecteur le force à faux). */
fun paletteFor(light: Boolean, oled: Boolean = false): UxPalette = if (light) LightPalette else if (oled) OledPalette else DarkPalette

/**
 * Palette du thème [theme]. [light] est l'état EFFECTIF : vrai pour Clair / Sable / Automatique en clair, hors lecteur ;
 * le lecteur le force à faux, donc un thème clair (Sable compris) y retombe sur la palette sombre.
 */
fun paletteFor(light: Boolean, theme: AppTheme): UxPalette {
    return if (light) (if (theme == AppTheme.SAND) SandPalette else LightPalette)
    else when (theme) {
        AppTheme.OLED -> OledPalette
        AppTheme.MIDNIGHT -> MidnightPalette
        AppTheme.FOREST -> ForestPalette
        AppTheme.PLUM -> PlumPalette
        AppTheme.CONTRAST -> ContrastPalette
        else -> DarkPalette
    }
}

/** Palette courante pour les écrans qui préfèrent la lire par CompositionLocal ; défaut sombre. */
val LocalUxPalette = staticCompositionLocalOf { DarkPalette }

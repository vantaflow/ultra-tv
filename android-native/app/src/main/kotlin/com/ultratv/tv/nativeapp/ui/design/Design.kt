package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.ui.common.design

/**
 * Système de design « Claude Design » (maquettes 1920×1080). Une cote de la maquette en
 * pixels se convertit avec [design] (dp) et [spx] (sp) : l'échelle d'écran est déjà
 * normalisée sur la hauteur (voir ProvideUiScale), donc 720p, 1080p et 4K sont identiques.
 */
object Ux {
    /**
     * Thème courant. État Compose : toute lecture d'un jeton ci-dessous dans une composition
     * s'abonne, donc un changement de thème recompose l'interface sans rien repasser en paramètre.
     * Le lecteur reste toujours sombre ([playerActive]).
     */
    var themeLight by mutableStateOf(false)
    /** Dérivé de la navigation (voir `isPlayerShown`), jamais d'un compteur d'entrées/sorties. */
    var playerActive by mutableStateOf(false)
    private val light: Boolean get() = com.ultratv.tv.nativeapp.ui.theme.isLightEffective(themeLight, playerActive)

    // Surfaces (sombre → clair : maquettes Accueil / AccueilClair / SidebarClair)
    val Bg: Color get() = if (light) Color(0xFFF4F3EF) else Color(0xFF0A0A0C)
    val Rail: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF0F0F12)
    val Surface: Color get() = if (light) Color(0xFFECEAE5) else Color(0xFF1C1C21)
    val Surface2: Color get() = if (light) Color(0xFFD9D6CF) else Color(0xFF26262D)
    val SurfaceDeep: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF141418)
    /** Fond des visuels de remplacement (affiches, logos sans image). */
    val Tone: Color get() = if (light) Color(0xFFE3E1DB) else Color(0xFF1F1F25)

    // Texte
    val Text: Color get() = if (light) Color(0xFF16151A) else Color(0xFFF5F5F7)
    val Text2: Color get() = if (light) Color(0xFF3C3B42) else Color(0xFFC4C4CC)
    val Text3: Color get() = if (light) Color(0xFF5E5D66) else Color(0xFFA1A1AA)
    /** Texte très atténué (compteurs, entrées désactivées). */
    val Muted: Color get() = if (light) Color(0xFF8A8992) else Color(0xFF71717A)
    val Muted2: Color get() = if (light) Color(0xFFB0AEB5) else Color(0xFF52525B)
    val Err: Color get() = if (light) Color(0xFFB3261E) else Color(0xFFFF8A8A)

    val Line: Color get() = if (light) Color(0xFFD9D6CF) else Color(0xFF3F3F46)
    val LineKey: Color get() = if (light) Color(0xFFB0AEB5) else Color(0xFF52525B)
    val Scrim: Color get() = if (light) Color(0xCCF4F3EF) else Color(0xB80A0A0C)

    // Focus : sombre = fond blanc / texte noir ; clair = fond encre / texte blanc ; l'anneau reste accent.
    val Cta: Color get() = if (light) Color(0xFF16151A) else Color(0xFFFFFFFF)
    val TextOnLight: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF0A0A0C)
    /** Texte secondaire sur une ligne focalisée. */
    val OnFocus2: Color get() = if (light) Color(0xFFC9C7CF) else Color(0xFF3F3F46)

    val Accent = Color(0xFFD91E2B)
    val White = Color(0xFFFFFFFF)
}

/** Sora (titres) et Manrope (texte) : polices variables sous licence OFL, embarquées dans res/font. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Sora = FontFamily(
    Font(R.font.sora, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.sora, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Taille de texte de la maquette (px) en sp de l'interface normalisée. */
val Int.spx: TextUnit get() = (this / 2f).sp

/**
 * Surface focalisable : fond [bg] au repos ; au focus fond blanc, texte noir, échelle 1,06 et
 * anneau accent de 6 px (maquette). Pas d'ombre floue ni d'animation : rendu statique, peu
 * coûteux sur une box d'entrée de gamme.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(28.design),
    bg: Color = Ux.Surface,
    focusedScale: Float = 1.06f,
    ringWidth: Dp = 6.design,
    focusedBg: Color = Ux.Cta,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .graphicsLayer {
                val s = if (focused) focusedScale else 1f
                scaleX = s; scaleY = s
            }
            .then(if (focused) Modifier.border(ringWidth, Ux.Accent, shape) else Modifier)
            .clip(shape)
            .background(if (focused) focusedBg else bg)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) { content(focused) }
}

/** Icône dessinée depuis un chemin SVG 24×24 (trait arrondi, comme dans la maquette). */
@Composable
fun DIcon(d: String, size: Dp, color: Color, strokeWidth: Float = 2f, fill: Boolean = false, modifier: Modifier = Modifier) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (fill) drawPath(path, color, style = Fill)
            else drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

object Icons {
    const val Monitor = "M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM8 21h8M12 17v4"
    const val List = "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"
    const val Globe = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"
    const val Link = "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"
    const val File = "M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zM14 3v6h6M9 14h6M9 17h4"
    const val Arrow = "M5 12h14M13 6l6 6-6 6"
    const val Check = "M5 12l5 5 9-10"
    const val Play = "M7 4v16l13-8z"
    const val Chevron = "M15 6l-6 6 6 6"
    // Rail (Sidebar.dc.html)
    const val Home = "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"
    const val Live = "M2 8h20v12H2zM7 3l5 5 5-5"
    const val Guide = "M3 5h18v14H3zM3 10h18M9 10v9"
    const val Movies = "M4 4h16v16H4zM8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4"
    const val Series = "M3 8h18v12H3zM8 4h8"
    const val Search = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4"
    const val Heart = "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"
    const val Record = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
    const val Settings = "M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4"
}

/** Logo carré accent avec triangle de lecture (maquette : 56 px, rayon 14). */
@Composable
fun LogoMark(sizePx: Int = 56) {
    Box(
        Modifier.size(sizePx.design).clip(RoundedCornerShape((sizePx / 4).design)).background(Ux.Accent),
        contentAlignment = Alignment.Center,
    ) { DIcon(Icons.Play, (sizePx * 0.43f).toInt().design, Ux.White, fill = true) }
}

/** Pastille d'aide télécommande : « OK Valider », « ‹ Retour »… */
@Composable
fun KeyHint(key: String?, label: String, chevron: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .heightIn(min = 36.design).widthIn(min = 36.design)
                .border(2.design, Ux.LineKey, RoundedCornerShape(18.design))
                .padding(horizontal = 10.design),
            contentAlignment = Alignment.Center,
        ) {
            if (chevron) DIcon(Icons.Chevron, 18.design, Color(0xFFE4E4E7), strokeWidth = 2.5f)
            else Text(key.orEmpty(), color = Color(0xFFE4E4E7), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx)
        }
        Spacer(Modifier.width(12.design))
        Text(label, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx)
    }
}

/** Pastille « EN DIRECT » : fond accent, rayon 8, 20 px 700, interlettrage 0,12 em (maquettes Accueil/Direct/Lecteur). */
@Composable
fun LiveBadge(text: String, bg: Color = Ux.Accent) {
    Box(Modifier.clip(RoundedCornerShape(8.design)).background(bg).padding(horizontal = 14.design, vertical = 6.design)) {
        Text(text, color = Ux.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, letterSpacing = 2.4.sp, maxLines = 1)
    }
}

/**
 * Bouton pilule des maquettes : repos = fond [bg], focus = blanc / texte noir / ×1,06 / anneau accent.
 * Hauteur, marge et corps du texte sont ceux de la maquette (Regarder : 76 / 40 / 28 ; secondaire : 76 / 36 / 26).
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightPx: Int = 76,
    hPadPx: Int = 36,
    fontPx: Int = 26,
    weight: FontWeight = FontWeight.SemiBold,
    bg: Color = Ux.Surface2,
    iconPath: String? = null,
    iconFill: Boolean = false,
    ringWidth: Dp = 6.design,
) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape((heightPx / 2).design),
        bg = bg,
        ringWidth = ringWidth,
        modifier = modifier.height(heightPx.design),
    ) { f ->
        Row(Modifier.padding(horizontal = hPadPx.design).height(heightPx.design), verticalAlignment = Alignment.CenterVertically) {
            val ink = if (f || bg == Ux.Cta) Ux.TextOnLight else Ux.Text
            if (iconPath != null) {
                DIcon(iconPath, (fontPx - 4).design, ink, fill = iconFill, strokeWidth = 2.2f)
                Spacer(Modifier.width(14.design))
            }
            Text(label, color = ink, fontFamily = Manrope, fontWeight = weight, fontSize = fontPx.spx, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionTitle(text: String, sizePx: Int = 32) =
    Text(text, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = sizePx.spx, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

/** Barre de progression fine (hauteur [heightPx], fond [track], remplissage accent). */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, heightPx: Int = 6, track: Color = Color.Transparent) {
    Box(modifier.height(heightPx.design).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Ux.Accent))
    }
}

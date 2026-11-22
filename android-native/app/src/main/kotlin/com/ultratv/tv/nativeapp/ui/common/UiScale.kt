package com.ultratv.tv.nativeapp.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset

/**
 * Mise à l'échelle de l'interface selon la HAUTEUR réelle de l'écran.
 *
 * Les écrans sont dessinés pour 540 dp de haut (= 1080p à 320 dpi). Or une TV peut
 * rapporter n'importe quelle densité : 720p en xhdpi donne 360 dp de haut (tout
 * est trop grand et déborde), 4K en 640 dpi donne 540 dp mais 3840 px. On force donc
 * la densité à `hauteur_px / 540` sur TV : la mise en page est identique en 720p,
 * 1080p et 4K, seule la netteté change. Hors TV (téléphone/tablette) on garde la
 * densité système.
 */
const val REFERENCE_HEIGHT_DP = 540f

/** Marge de sécurité overscan TV : 5 % de chaque côté. */
const val SAFE_AREA_FRACTION = 0.05f

fun uiDensityFor(heightPx: Int, systemDensity: Float): Float =
    if (heightPx <= 0) systemDensity else heightPx / REFERENCE_HEIGHT_DP

/**
 * Densité de l'interface : TV = hauteur normalisée à 540 dp × [ui], police au facteur de texte ; téléphone / tablette =
 * densité système × [ui], police système × facteur de texte. [ui] = 1 et [textScale] = 1 : rendu d'avant, inchangé.
 */
fun scaledDensity(tv: Boolean, heightPx: Int, base: Density, textScale: Float, ui: Float): Density =
    if (tv) Density(uiDensityFor(heightPx, base.density) * ui, textScale)
    else if (textScale == 1f && ui == 1f) base else Density(base.density * ui, base.fontScale * textScale)

fun isTelevision(ctx: Context): Boolean {
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Marges de sécurité (horizontale et verticale) en dp pour la densité courante. */
val LocalSafeArea = compositionLocalOf { PaddingValues(0.dp) }

/** Largeur de la fenêtre en dp de l'interface (après normalisation de densité). */
val LocalUiWidthDp = compositionLocalOf { 0f }

@Composable
fun ProvideUiScale(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val tv = remember { isTelevision(ctx) }
    val base = LocalDensity.current
    // BoxWithConstraints : taille réelle de la fenêtre, fiable dès la première
    // composition (LocalWindowInfo.containerSize renvoie 0 ici).
    val edges = if (tv) com.ultratv.tv.nativeapp.ui.design.Ux.screenEdges.fraction else 0f
    // Marge des bords : couleur de fond du thème (pas de cadre sombre autour d'un thème clair).
    val frame = if (edges > 0f) com.ultratv.tv.nativeapp.ui.design.Ux.Bg else androidx.compose.ui.graphics.Color(0xFF0A0A0C)
    androidx.compose.foundation.layout.BoxWithConstraints(
        androidx.compose.ui.Modifier.fillMaxSize().background(frame),
    ) {
        // Bords de l'écran (overscan, TV seulement) : l'interface est dessinée dans la zone intérieure, densité calculée sur
        // SA hauteur — même maquette de 540 dp, simplement plus petite : aucun écran (rail, Guide, Réglages, dialogues,
        // pavé de code) ne se tasse ni ne déborde. La vidéo plein écran ressort de la marge (voir [bleedScreenEdges]).
        val inset = screenInsetPx(constraints.maxWidth, constraints.maxHeight, edges)
        val wPx = constraints.maxWidth - 2 * inset.x
        val hPx = constraints.maxHeight - 2 * inset.y
        // TV : maquette en pixels fixes, police comprise. La taille de police système (« Grand » sur certaines box)
        // agrandissait les textes dans des cadres de taille fixe : titres, boutons et libellés tronqués partout.
        // Taille du texte choisie dans Réglages › Affichage (Normal / Grand / Très grand) : facteur de police explicite,
        // multiplié à celui du système hors TV. Les cadres qui contiennent du texte grandissent avec (voir `designT`).
        val textScale = com.ultratv.tv.nativeapp.ui.design.Ux.textScale
        // Taille de l'interface (Petite / Normale / Grande / Très grande) : toute la densité, texte compris.
        val ui = com.ultratv.tv.nativeapp.ui.design.Ux.uiSize.scale(tv)
        val scaled = scaledDensity(tv, hPx, base, textScale, ui)
        val wDp = if (wPx > 0) wPx / scaled.density else 0f
        val hDp = if (hPx > 0) hPx / scaled.density else 0f
        val safe = if (tv) PaddingValues(
            horizontal = (wDp * SAFE_AREA_FRACTION).dp,
            vertical = (hDp * SAFE_AREA_FRACTION).dp,
        ) else PaddingValues(0.dp)
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().insetPx(inset)) {
            CompositionLocalProvider(
                LocalDensity provides scaled,
                LocalSafeArea provides safe,
                LocalUiWidthDp provides wDp,
                LocalScreenInsetPx provides inset,
                // Mode tactile (téléphone / tablette) : tout ce qui n'est pas une TV. Jamais recalculé ailleurs.
                com.ultratv.tv.nativeapp.ui.mobile.LocalTouch provides !tv,
                com.ultratv.tv.nativeapp.ui.mobile.LocalUiHeightDp provides hDp,
            ) { content() }
        }
    }
}

/** Marge appliquée de chaque côté par [ProvideUiScale], en pixels (x = gauche/droite, y = haut/bas). */
val LocalScreenInsetPx = compositionLocalOf { androidx.compose.ui.unit.IntOffset.Zero }

/** Marge des bords de l'écran en pixels : [fraction] de la largeur à gauche/droite, de la hauteur en haut/bas. Pur, testé. */
fun screenInsetPx(widthPx: Int, heightPx: Int, fraction: Float): androidx.compose.ui.unit.IntOffset =
    if (fraction <= 0f || widthPx <= 0 || heightPx <= 0) androidx.compose.ui.unit.IntOffset.Zero
    else androidx.compose.ui.unit.IntOffset(kotlin.math.round(widthPx * fraction).toInt(), kotlin.math.round(heightPx * fraction).toInt())

/** Retrait de [inset] pixels de chaque côté (en pixels : indépendant de la densité de l'interface). */
private fun androidx.compose.ui.Modifier.insetPx(inset: androidx.compose.ui.unit.IntOffset): androidx.compose.ui.Modifier =
    if (inset == androidx.compose.ui.unit.IntOffset.Zero) this
    else layout { m, c ->
        val p = m.measure(c.offset(-2 * inset.x, -2 * inset.y))
        layout(c.maxWidth, c.maxHeight) { p.place(inset.x, inset.y) }
    }

/**
 * Vidéo plein écran bord à bord malgré la marge des bords de l'écran : l'élément ressort de [LocalScreenInsetPx] de chaque
 * côté. Les bandeaux et commandes du lecteur, eux, restent dans la zone intérieure.
 */
@Composable
fun androidx.compose.ui.Modifier.bleedScreenEdges(): androidx.compose.ui.Modifier {
    val inset = LocalScreenInsetPx.current
    return if (inset == androidx.compose.ui.unit.IntOffset.Zero) this
    else layout { m, c ->
        val w = if (c.hasBoundedWidth) c.maxWidth + 2 * inset.x else c.maxWidth
        val h = if (c.hasBoundedHeight) c.maxHeight + 2 * inset.y else c.maxHeight
        val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
        layout(c.maxWidth, c.maxHeight) { p.place(-inset.x, -inset.y) }
    }
}

/** Convertit une cote de la maquette 1920×1080 en dp de l'interface normalisée (540 dp de haut). */
val Int.design: Dp get() = (this / 2f).dp

/**
 * Largeur « de maquette » TV (px de 1920) qui, au toucher, devient « toute la largeur disponible plafonnée à cette cote » :
 * un panneau de 820 px (410 dp) ne doit pas déborder d'un téléphone de 360 dp. Sur TV : largeur fixe, comme avant.
 */
@androidx.compose.runtime.Composable
fun androidx.compose.ui.Modifier.responsiveWidth(px: Int): androidx.compose.ui.Modifier =
    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) this.fillMaxWidth().widthIn(max = px.design) else this.width(px.design)

package com.ultratv.tv.nativeapp.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
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
    androidx.compose.foundation.layout.BoxWithConstraints(
        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0A0A0C)),
    ) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val scaled = if (tv) Density(uiDensityFor(hPx, base.density), base.fontScale) else base
        val wDp = if (wPx > 0) wPx / scaled.density else 0f
        val hDp = if (hPx > 0) hPx / scaled.density else 0f
        val safe = if (tv) PaddingValues(
            horizontal = (wDp * SAFE_AREA_FRACTION).dp,
            vertical = (hDp * SAFE_AREA_FRACTION).dp,
        ) else PaddingValues(0.dp)
        CompositionLocalProvider(
            LocalDensity provides scaled,
            LocalSafeArea provides safe,
            LocalUiWidthDp provides wDp,
            // Mode tactile (téléphone / tablette) : tout ce qui n'est pas une TV. Jamais recalculé ailleurs.
            com.ultratv.tv.nativeapp.ui.mobile.LocalTouch provides !tv,
            com.ultratv.tv.nativeapp.ui.mobile.LocalUiHeightDp provides hDp,
        ) { content() }
    }
}

/** Convertit une cote de la maquette 1920×1080 en dp de l'interface normalisée (540 dp de haut). */
val Int.design: Dp get() = (this / 2f).dp

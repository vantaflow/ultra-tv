package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp

/**
 * Mode d'entrée : TACTILE (téléphone, tablette) ou D-PAD (TV). Fourni une fois, à la racine, depuis
 * [com.ultratv.tv.nativeapp.ui.common.isTelevision] ; aucun écran ne le recalcule. Faux par défaut :
 * tout code qui s'affiche sans ce fournisseur (tests, aperçus) garde le comportement TV historique.
 *
 * STATIQUE : la valeur ne change jamais pendant la vie de l'écran. Un `compositionLocalOf` ordinaire fait
 * suivre chaque lecture (une par cellule d'affiche, par ligne de chaîne, par FocusSurface…) par le moteur de
 * composition ; un local statique se lit sans aucun suivi.
 */
val LocalTouch = staticCompositionLocalOf { false }

/** Hauteur de la fenêtre en dp (téléphone en paysage < 480 dp). */
val LocalUiHeightDp = androidx.compose.runtime.compositionLocalOf { 0f }

/** Cible tactile minimale (Material / WCAG) : 48 dp. */
const val MIN_TOUCH_DP = 48

/** Classe de taille de fenêtre courante. */
@Composable
fun rememberWindowClass(): WindowClass = windowClassFor(LocalUiWidthDp.current)

/**
 * Impose au moins 48 dp en tactile. À placer AVANT les cotes de l'appelant : une contrainte
 * minimale posée en amont l'emporte sur un `height()` plus petit placé après.
 */
fun Modifier.minTouchTarget(): Modifier = heightIn(min = MIN_TOUCH_DP.dp).widthIn(min = MIN_TOUCH_DP.dp)

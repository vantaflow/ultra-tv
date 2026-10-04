package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.runtime.compositionLocalOf

/**
 * Vrai sur les machines entrée de gamme (niveau LOW du profil adaptatif) : pas d'animations décoratives.
 * Fourni par MainActivity depuis [com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile] — aucun écran ne lit la RAM lui-même.
 */
val LocalLowRam = compositionLocalOf { false }

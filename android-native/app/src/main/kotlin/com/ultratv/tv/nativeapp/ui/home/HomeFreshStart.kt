package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Retour à l'accueil CHOISI dans le menu latéral (ou par Retour menu ouvert) : l'accueil s'ouvre comme à neuf, liste en haut,
 * « Regarder » focalisé, mémoire des rangées remise à zéro. Le retour d'une fiche ou du lecteur ne passe pas par là : il
 * garde la restauration de la carte quittée ([HomeFocusRestore]). Posé par le menu juste avant la navigation, lu une fois
 * par l'accueil qui se compose ensuite.
 */
internal object HomeFreshStart {
    @Volatile private var pending = false
    fun mark() { pending = true }
    fun consume(): Boolean { val v = pending; pending = false; return v }
}

/** Vrai pour CETTE visite de l'accueil si elle suit un choix dans le menu ; lu une seule fois à la création de l'écran. */
@Composable
internal fun rememberHomeFreshStart(): Boolean = remember { HomeFreshStart.consume() }

/** « Regarder » ne prend le focus qu'une fois par visite (état sauvegardé) ; une visite neuve repart de zéro. */
@Composable
internal fun rememberHeroFocusDone(freshStart: Boolean): MutableState<Boolean> {
    val state = rememberSaveable { mutableStateOf(false) }
    remember(state, freshStart) { if (freshStart) state.value = false; freshStart }
    return state
}

/** Visite neuve de l'accueil, fournie aux rangées (leur défilement horizontal sauvegardé est remis au début). */
internal val LocalHomeFresh = staticCompositionLocalOf { false }

/** État de défilement d'une rangée de l'accueil : sauvegardé avec l'entrée de navigation, sauf visite neuve (remis au début). */
@Composable
internal fun rememberRowListState(): LazyListState {
    val state = rememberLazyListState()
    val fresh = LocalHomeFresh.current
    remember(state, fresh) { if (fresh) state.requestScrollToItem(0); fresh }
    return state
}

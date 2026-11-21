package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * Numéro de visite neuve de l'accueil (0 = aucune), sauvegardé avec l'entrée de navigation : incrémenté une fois quand
 * l'accueil s'ouvre par le menu. Chaque rangée ne se remet au début qu'une fois par numéro ([rememberRowListState]).
 */
@Composable
internal fun rememberHomeVisit(freshStart: Boolean): Int {
    val visit = rememberSaveable { mutableIntStateOf(0) }
    remember(visit, freshStart) { if (freshStart) visit.intValue++; freshStart }
    return visit.intValue
}

/** Numéro de la visite neuve en cours, fourni aux rangées. */
internal val LocalHomeVisit = staticCompositionLocalOf { 0 }

/**
 * État de défilement d'une rangée de l'accueil : sauvegardé avec l'entrée de navigation. Une visite neuve le remet au début
 * UNE seule fois (le numéro déjà traité est sauvegardé avec la rangée) : sortie puis revenue dans la composition, la rangée
 * garde sa place.
 */
@Composable
internal fun rememberRowListState(): LazyListState {
    val state = rememberLazyListState()
    val visit = LocalHomeVisit.current
    val done = rememberSaveable { mutableIntStateOf(0) }
    remember(state, visit) { if (visit != 0 && done.intValue != visit) { state.requestScrollToItem(0); done.intValue = visit }; visit }
    return state
}

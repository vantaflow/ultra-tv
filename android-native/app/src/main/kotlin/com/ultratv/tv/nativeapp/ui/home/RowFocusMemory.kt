package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged

/**
 * Mémoire d'une rangée de l'accueil : la recherche directionnelle de Compose choisit la carte la plus proche
 * (focusRestorer ne sert pas aux touches ▲ ▼), donc revenir sur une rangée tombait sur une carte voisine de celle quittée.
 * Quand le focus est hors de la rangée, seule la dernière carte focalisée reste candidate ; si elle n'est plus composée
 * (données remplacées, carte sortie de l'écran), toutes les cartes redeviennent candidates : jamais d'impasse.
 * Objet simple lu au moment de la recherche : aucun état Compose, donc aucune recomposition quand le focus bouge.
 */
internal class RowFocusMemory {
    var hasFocus = false
    var last: Any? = null
    val composed = HashSet<Any>()
    fun canFocus(key: Any) = hasFocus || last == null || last !in composed || key == last

    /** À poser sur la rangée (après son groupe de focus) : sait si le focus est dedans. Créé une fois avec la mémoire. */
    val tracker: Modifier = Modifier.onFocusChanged { hasFocus = it.hasFocus }
}

@Composable
internal fun rememberRowFocusMemory(): RowFocusMemory = remember { RowFocusMemory() }

/**
 * Modifieur d'une carte de la rangée [memory] (clé stable [key]) : inscrit la carte tant qu'elle est composée, la rend
 * non candidate quand ce n'est pas la dernière visée, et retient la dernière focalisée. Le modifieur est mémorisé :
 * rien n'est alloué à la recomposition de la carte.
 */
@Composable
internal fun rowMemory(memory: RowFocusMemory, key: Any): Modifier {
    DisposableEffect(memory, key) { memory.composed += key; onDispose { memory.composed -= key } }
    return remember(memory, key) {
        Modifier.focusProperties { canFocus = memory.canFocus(key) }.onFocusChanged { if (it.hasFocus) memory.last = key }
    }
}

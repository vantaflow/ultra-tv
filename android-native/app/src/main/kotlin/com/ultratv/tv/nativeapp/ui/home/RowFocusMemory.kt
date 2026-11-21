package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Mémoire d'une rangée de l'accueil : la recherche directionnelle de Compose choisit la carte la plus proche
 * (focusRestorer ne sert pas aux touches ▲ ▼), donc revenir sur une rangée tombait sur une carte voisine de celle quittée.
 * Quand le focus est hors de la rangée, seule la dernière carte focalisée reste candidate ; si elle n'est plus composée
 * (données remplacées, carte sortie de l'écran), toutes les cartes redeviennent candidates : jamais d'impasse.
 * Objet simple lu au moment de la recherche : aucun état Compose, donc aucune recomposition quand le focus bouge.
 */
internal class RowFocusMemory(val id: String = "") {
    var hasFocus = false
    var last: Any? = null
    val composed = HashSet<Any>()
    fun canFocus(key: Any) = !HomeEdgeGate.closed && (hasFocus || last == null || last !in composed || key == last)

    /** À poser sur la rangée (après son groupe de focus) : sait si le focus est dedans. Créé une fois avec la mémoire. */
    val tracker: Modifier = Modifier.onFocusChanged { hasFocus = it.hasFocus }
}

/** Identité de la rangée (clé de son élément dans la liste de l'accueil), fournie par l'écran : distingue deux rangées qui ont une même clé de carte. */
internal val LocalHomeRowId = staticCompositionLocalOf { "" }

@Composable
internal fun rememberRowFocusMemory(): RowFocusMemory {
    val id = LocalHomeRowId.current
    return remember(id) { RowFocusMemory(id) }
}

/**
 * Sortie horizontale d'une rangée (◀ au début, ▶ à la fin). La recherche de voisin de Compose met à plat TOUTES les cartes
 * de l'écran : depuis la première affiche du Top 10, décalée par son grand chiffre, les premières cartes des rangées
 * voisines passaient pour des voisines de gauche, et ◀ sautait sur l'une d'elles (souvent hors écran) au lieu d'ouvrir
 * le menu latéral. La rangée annule donc ce premier déplacement et le refait aussitôt, [closed] fermant les cartes de
 * l'accueil le temps de cette seule recherche (appel synchrone, rouvert dans la foulée : rien ne reste fermé).
 */
internal object HomeEdgeGate { @JvmField var closed = false }

/**
 * Dernière carte focalisée de l'accueil, SAUVEGARDÉE avec l'entrée de navigation : l'accueil quitte la composition quand une
 * fiche s'ouvre et en revient recomposé de zéro (mémoire de rangée et focusRestorer perdus), le focus tombait alors sur la
 * première carte composée. Objet simple, aucun état Compose : rien ne se recompose quand le focus bouge.
 */
internal class HomeFocusRestore(var saved: String? = null) {
    val requester = FocusRequester()
    /** Retour en cours (une carte était sauvegardée) : la carte sauvegardée porte [requester] et [saved] n'est pas écrasée. */
    @JvmField var pending = saved != null
    @JvmField var targetComposed = false
    fun idOf(row: String, key: Any) = "$row\u0000$key"

    companion object {
        val Saver = listSaver<HomeFocusRestore, String>(save = { listOf(it.saved ?: "") }, restore = { HomeFocusRestore(it[0].ifEmpty { null }) })
    }
}

internal val LocalHomeFocusRestore = staticCompositionLocalOf<HomeFocusRestore?> { null }

/**
 * Modifieur d'une carte de la rangée [memory] (clé stable [key]) : inscrit la carte tant qu'elle est composée, la rend
 * non candidate quand ce n'est pas la dernière visée, et retient la dernière focalisée. Le modifieur est mémorisé :
 * rien n'est alloué à la recomposition de la carte.
 */
@Composable
internal fun rowMemory(memory: RowFocusMemory, key: Any): Modifier {
    val restore = LocalHomeFocusRestore.current
    val id = restore?.idOf(memory.id, key)
    // Carte quittée avant la sortie de l'accueil, retrouvée à la recomposition : elle seule porte la cible de restauration.
    val isTarget = remember(memory, key) { restore != null && restore.pending && restore.saved == id }
    DisposableEffect(memory, key) {
        memory.composed += key
        if (isTarget) restore!!.targetComposed = true
        onDispose { memory.composed -= key; if (isTarget) restore!!.targetComposed = false }
    }
    return remember(memory, key) {
        Modifier.focusProperties { canFocus = memory.canFocus(key) }
            .then(if (isTarget) Modifier.focusRequester(restore!!.requester) else Modifier)
            .onFocusChanged { if (it.hasFocus) { memory.last = key; restore?.let { r -> if (!r.pending) r.saved = id } } }
    }
}

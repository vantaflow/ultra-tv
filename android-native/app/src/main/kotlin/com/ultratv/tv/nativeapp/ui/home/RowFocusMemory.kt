package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.flow.first

/**
 * Mémoire d'une rangée de l'accueil : la recherche directionnelle de Compose choisit la carte la plus proche
 * (focusRestorer ne sert pas aux touches ▲ ▼), donc revenir sur une rangée tombait sur une carte voisine de celle quittée.
 * Quand le focus est hors de la rangée, seule la dernière carte focalisée reste candidate ; si elle n'est plus composée
 * (données remplacées, carte sortie de l'écran), toutes les cartes redeviennent candidates : jamais d'impasse.
 * Objet simple lu au moment de la recherche : aucun état Compose, donc aucune recomposition quand le focus bouge.
 */
internal class RowFocusMemory(val id: String = "", private val gate: HomeEdgeGate? = null, private val restore: HomeFocusRestore? = null) {
    var hasFocus = false
    var last: Any? = null
    val composed = HashSet<Any>()

    /** [restoreId] : identité de la carte pour la restauration au retour ([HomeFocusRestore.idOf]), calculée une fois par carte. */
    fun canFocus(key: Any, restoreId: String?): Boolean {
        if (gate?.closed == true) return false
        // Retour d'une fiche en attente : seule la carte quittée est candidate, la demande de focus initiale tombe donc dessus.
        if (restore != null && restore.isPending()) return restoreId == restore.saved
        return hasFocus || last == null || last !in composed || key == last
    }

    /** À poser sur la rangée (après son groupe de focus) : sait si le focus est dedans. Créé une fois avec la mémoire. */
    val tracker: Modifier = Modifier.onFocusChanged { hasFocus = it.hasFocus }
}

/** Identité de la rangée (clé de son élément dans la liste de l'accueil), fournie par l'écran : distingue deux rangées qui ont une même clé de carte. */
internal val LocalHomeRowId = staticCompositionLocalOf { "" }

@Composable
internal fun rememberRowFocusMemory(): RowFocusMemory {
    val id = LocalHomeRowId.current
    val gate = LocalHomeEdgeGate.current
    val restore = LocalHomeFocusRestore.current
    return remember(id, gate, restore) { RowFocusMemory(id, gate, restore) }
}

/**
 * Porte de sortie horizontale d'une rangée (◀ au début, ▶ à la fin), PROPRE à l'écran d'accueil (mémorisée par lui).
 * La recherche de voisin de Compose met à plat TOUTES les cartes de l'écran : depuis la première affiche du Top 10,
 * décalée par son grand chiffre, les premières cartes des rangées voisines passaient pour des voisines de gauche, et ◀
 * sautait sur l'une d'elles (souvent hors écran) au lieu d'ouvrir le menu latéral. La rangée annule donc ce déplacement
 * et le refait aussitôt, [closed] fermant les cartes de l'accueil le temps de cette seule recherche (synchrone, rouvert
 * dans un `finally`). Fermée, [exit] renvoie toujours `Default` : la seconde recherche ne se relance jamais elle-même.
 * [keys] compte les touches reçues par l'accueil : une relance dont la touche est périmée est abandonnée.
 */
internal class HomeEdgeGate {
    @JvmField var closed = false
    @JvmField var keys = 0
    /** Relances effectuées (au plus une par touche ; lu par les tests). */
    @JvmField var relaunches = 0
}

internal val LocalHomeEdgeGate = staticCompositionLocalOf<HomeEdgeGate?> { null }

/**
 * Dernier élément focalisé de l'accueil (carte ou bouton du bandeau), SAUVEGARDÉ avec l'entrée de navigation : l'accueil
 * quitte la composition quand une fiche s'ouvre et en revient recomposé de zéro (le `focusRestorer` de l'écran et la
 * mémoire de rangée sont perdus). Tant que [isPending], seul cet élément est candidat au focus : la demande de focus
 * initiale de l'écran (qui réessaie) tombe directement dessus, sans passage par un autre. Abandon, tout redevient
 * candidat : à la première touche, ou après [RESTORE_MS] si l'élément n'existe plus ou n'est pas composé.
 * Objet simple, aucun état Compose : rien ne se recompose quand le focus bouge.
 */
internal class HomeFocusRestore(var saved: String? = null) {
    @JvmField var pending = saved != null
    private val startMs = android.os.SystemClock.uptimeMillis()

    /** Rangée de l'élément sauvegardé (« HERO », « SPORT » ou l'identité d'une rangée de la liste), calculée une fois. */
    private val savedRow: String? = saved?.substringBefore('\u0000')

    /**
     * Clés (en texte) des cartes de chaque rangée, d'après ses DONNÉES et non d'après ce que la liste paresseuse a composé :
     * une carte qui existe mais est hors de la fenêtre composée reste attendue. Posé par les rangées pendant l'attente seulement.
     * Seule une rangée déclarée, composée et non vide peut décider : une rangée absente (données qui arrivent par morceaux)
     * ou vide ne décide jamais, le délai de dernier recours reste la borne.
     */
    private val rowKeys = HashMap<String, Set<String>>()

    /** [keys] vide : rien n'est décidé (les données peuvent encore arriver) ; la déclaration précédente est alors retirée. */
    fun registerRow(row: String, keys: Set<String>) {
        if (!pending) return
        if (keys.isEmpty()) rowKeys.remove(row) else rowKeys[row] = keys
    }

    /** La rangée quitte la composition : ses clés ne sont plus garanties à jour. */
    fun unregisterRow(row: String) { rowKeys.remove(row) }

    /** Les données d'une rangée déclarée sont là et l'élément sauvegardé n'y figure plus : inutile d'attendre. */
    private fun savedAbsentFromData(): Boolean {
        val s = saved ?: return false
        val row = savedRow ?: return false
        if (row == "HERO" || row == "SPORT") return false
        val keys = rowKeys[row] ?: return false
        return s.substring(row.length + 1) !in keys
    }

    /**
     * L'utilisateur a pressé une touche depuis que CET accueil est affiché (créé après la touche d'un autre écran : celle-ci ne compte pas).
     * Distingue le lancement (le bandeau qui arrive après les rangées prend le focus) d'une navigation déjà commencée (il ne le déplace pas).
     */
    fun userActed(): Boolean = com.ultratv.tv.nativeapp.ui.common.InputClock.lastKeyMs > startMs

    fun isPending(): Boolean {
        if (!pending) return false
        if (com.ultratv.tv.nativeapp.ui.common.InputClock.lastKeyMs > startMs || savedAbsentFromData()) pending = false
        return pending
    }

    /** Index, dans [keys] (clés de la rangée [row], dans l'ordre), de l'élément sauvegardé ; -1 s'il n'est pas de cette rangée ou n'y est plus. */
    fun indexOfSaved(row: String, keys: List<String>): Int {
        val s = saved ?: return -1
        if (savedRow != row) return -1
        return keys.indexOf(s.substring(row.length + 1))
    }

    /** Visite neuve (retour par le menu) : plus aucun élément à restaurer, tout est candidat. */
    fun reset() { saved = null; pending = false }

    fun idOf(row: String, key: Any) = "$row\u0000$key"

    /** Le focus est arrivé sur [id] : fin de l'attente si c'est la cible, sinon mémorisation pour une prochaine sortie. */
    fun focused(id: String) { if (pending) { if (id == saved) pending = false } else saved = id }

    companion object {
        const val RESTORE_MS = 1_000L
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
    val id = remember(restore, memory, key) { restore?.idOf(memory.id, key) }
    DisposableEffect(memory, key) { memory.composed += key; onDispose { memory.composed -= key } }
    return remember(memory, key, restore, id) {
        Modifier.focusProperties { canFocus = memory.canFocus(key, id) }
            .onFocusChanged { if (it.hasFocus) { memory.last = key; if (id != null) restore?.focused(id) } }
    }
}

/**
 * Déclare à la restauration les clés de la rangée [memory], tirées de ses données ([keys], dans l'ordre de la rangée,
 * appelée seulement tant que la restauration est en attente : rien n'est alloué ensuite). À poser sous [rememberRowFocusMemory].
 *
 * [listState] : l'historique est trié par date décroissante, donc la carte quittée (lue, remontée en tête) existe dans les
 * données sans être dans la fenêtre composée de la rangée, qui garde son ancien défilement. Pendant l'attente, la rangée
 * défile jusqu'à elle (un défilement, aucune demande de focus) : elle est alors composée, devient la seule candidate et la
 * demande de focus initiale de l'écran tombe dessus sans attendre le dernier recours.
 */
@Composable
internal fun RegisterRowKeys(memory: RowFocusMemory, data: Any?, listState: LazyListState? = null, keys: () -> List<Any>) {
    val restore = LocalHomeFocusRestore.current ?: return
    if (!restore.pending) return
    val list = remember(restore, data) { keys().map { it.toString() } }
    val set = remember(restore, list) { list.toHashSet() }
    SideEffect { restore.registerRow(memory.id, set) }
    DisposableEffect(restore, memory) { onDispose { restore.unregisterRow(memory.id) } }
    if (listState != null) LaunchedEffect(restore, list) {
        val index = restore.indexOfSaved(memory.id, list)
        if (index < 0) return@LaunchedEffect
        // Attend la première mesure : avant elle, rien n'est « visible ». Une carte déjà visible ne déplace rien.
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        val key = list[index]
        if (listState.layoutInfo.visibleItemsInfo.none { it.key.toString() == key }) listState.scrollToItem(index)
    }
}

/** Bouton du bandeau « À la une » ([key] : watch, info, list) : mêmes règles de restauration que les cartes des rangées. */
@Composable
internal fun heroFocus(key: String): Modifier {
    val restore = LocalHomeFocusRestore.current
    val gate = LocalHomeEdgeGate.current
    val id = remember(restore, key) { restore?.idOf("HERO", key) }
    return remember(restore, gate, id) {
        Modifier.focusProperties {
            if (gate?.closed == true) canFocus = false
            else if (restore != null && restore.isPending()) canFocus = id == restore.saved
        }.onFocusChanged { if (it.hasFocus && id != null) restore?.focused(id) }
    }
}

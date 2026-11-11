package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.data.repo.CategoryRow
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Logique pure de la liste des chaînes du lecteur (LiveDrawer) : ordre des entrées de la colonne de gauche, compteur,
 * décision de charger la catégorie qui prend le focus. Sans dépendance Android : testée sur la JVM (LiveDrawerLogicTest).
 */

/** Une entrée de la colonne des catégories : [count] = nombre réel de chaînes (null = inconnu). */
data class DrawerCategory(val id: String, val label: String, val count: Int?)

object LiveDrawerLogic {
    /** Délai avant de charger la catégorie qui reçoit le focus : évite de tout charger quand on défile vite. */
    const val BROWSE_DEBOUNCE_MS = 250L

    /**
     * Colonne des catégories : « Toutes les chaînes » en tête (avec le total), puis Favoris s'il y en a, puis les catégories
     * dans leur ordre. Si une entrée « Tout » / « Favoris » existe déjà dans [cats] (même identifiant), elle est déplacée
     * en tête au lieu d'être dupliquée.
     */
    fun categories(allLabel: String, favoritesLabel: String, allCount: Int?, favoritesCount: Int, cats: List<CategoryRow>): List<DrawerCategory> {
        val existingAll = cats.firstOrNull { it.remoteId == CATEGORY_ALL }
        val existingFav = cats.firstOrNull { it.remoteId == CATEGORY_FAVORITES }
        val rows = cats.filter { it.remoteId != CATEGORY_ALL && it.remoteId != CATEGORY_FAVORITES }
        return buildList {
            add(DrawerCategory(CATEGORY_ALL, existingAll?.label ?: allLabel, allCount ?: existingAll?.count))
            val favCount = if (favoritesCount > 0) favoritesCount else existingFav?.count ?: 0
            if (favCount > 0) add(DrawerCategory(CATEGORY_FAVORITES, existingFav?.label ?: favoritesLabel, favCount))
            rows.mapTo(this) { DrawerCategory(it.remoteId, it.label, it.count) }
        }
    }

    /** Faut-il charger la catégorie [focused] ? Non si c'est déjà celle dont la liste est affichée. */
    fun shouldBrowse(focused: String, displayed: String?): Boolean = focused != displayed

    /**
     * Nombre affiché à côté du titre. Jamais 0 tant que la catégorie a des chaînes : pendant le chargement on montre le
     * compte connu de la catégorie (pas la taille de la liste précédente, ni celle, vide, d'avant le chargement) ; la
     * liste chargée est plafonnée, le compte réel de la catégorie fait foi dès qu'il est plus grand.
     * @param listIsCategory la liste affichée est celle d'une catégorie (sinon : la file de zapping, on compte ses lignes)
     * @param loading une catégorie est demandée mais sa liste n'est pas encore arrivée
     */
    fun counter(entriesSize: Int, categoryCount: Int?, listIsCategory: Boolean, loading: Boolean): Int = when {
        !listIsCategory -> entriesSize
        loading -> categoryCount ?: entriesSize
        else -> maxOf(entriesSize, categoryCount ?: 0)
    }

    /** Entrée de la liste des chaînes où poser le focus en y entrant : la chaîne en cours, sinon la première ; null si vide. */
    fun entryFocusIndex(currentIndex: Int, size: Int): Int? = when {
        size <= 0 -> null
        currentIndex in 0 until size -> currentIndex
        else -> 0
    }
}

/**
 * Chargeur anti-rebond d'une catégorie parcourue : [request] note la catégorie voulue ([pending] la porte jusqu'à l'arrivée de
 * sa liste), seule la dernière demande restée [debounceMs] sans changement est chargée, et un chargement dépassé est annulé
 * (il ne peut pas écraser une liste plus récente).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DebouncedBrowser(
    scope: CoroutineScope,
    debounceMs: Long = LiveDrawerLogic.BROWSE_DEBOUNCE_MS,
    private val load: suspend (String) -> Unit,
) {
    private val requests = MutableStateFlow<String?>(null)
    private val _pending = MutableStateFlow<String?>(null)
    /** Catégorie demandée dont la liste n'est pas encore chargée ; null quand tout est à jour. */
    val pending: StateFlow<String?> = _pending

    init {
        scope.launch {
            requests.filterNotNull().debounce(debounceMs).collectLatest { id ->
                load(id)
                if (_pending.value == id) _pending.value = null
            }
        }
    }

    fun request(id: String) {
        if (requests.value == id && _pending.value == null) return   // déjà chargée
        _pending.value = id
        requests.value = id
    }

    fun reset() { requests.value = null; _pending.value = null }
}

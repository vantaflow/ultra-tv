package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Clés de liste paresseuse (LazyColumn / LazyRow / grilles) qui ne plantent jamais, pour toute liste dont la clé vient
 * des données d'un fournisseur (titre, libellé, nom, identifiant distant, URL…) : deux éléments de même clé font planter
 * Compose (« Key "— · M6" was already used », « HD · HBO », « 4K · RELAX TURKEY »).
 *
 * Règle commune :
 *  - la première occurrence d'une clé la garde telle quelle (même type, même valeur) : focus, défilement et état
 *    restent attachés au même élément d'une recomposition à l'autre ;
 *  - la n-ième occurrence (n ≥ 2) reçoit une clé texte dérivée de la clé et de son RANG PARMI SES DOUBLONS (pas de
 *    l'index dans la liste) : elle reste stable quand d'autres éléments s'insèrent ou se déplacent ;
 *  - une clé dérivée qui heurterait une clé déjà vue est encore départagée (garantie d'unicité absolue).
 *
 * Les clés restent des types qu'Android sait ranger dans un Bundle (la clé d'origine, ou un String).
 */
fun <T> uniqueKeys(items: List<T>, id: (T) -> Any): List<Any> {
    if (items.isEmpty()) return emptyList()
    val used = HashSet<Any>(items.size * 2)
    var counts: HashMap<Any, Int>? = null
    return items.map { item ->
        val base = id(item)
        if (used.add(base)) base
        else {
            val c = counts ?: HashMap<Any, Int>().also { counts = it }
            var n = (c[base] ?: 1) + 1
            var key: String = dupKey(base, n)
            while (!used.add(key)) key = dupKey(base, ++n)
            c[base] = n
            key
        }
    }
}

private fun dupKey(base: Any, n: Int): String = "\u0000dup\u0000$base\u0000$n"

/** [uniqueKeys] calculées une fois par liste (aucun calcul ajouté à chaque recomposition). */
@Composable
fun <T> rememberUniqueKeys(items: List<T>, id: (T) -> Any): List<Any> = remember(items) { uniqueKeys(items, id) }

/**
 * `items(list, key = …)` dont les clés passent par [uniqueKeys] : même signature qu'`items`, à utiliser pour toute liste
 * dont la clé vient des données (identifiant distant, épisode, catégorie, ligue…). Plantage évité :
 * « Key "2" was already used » (1.3.76, liste non identifiée faute de table de correspondance R8).
 */
inline fun <T> androidx.compose.foundation.lazy.LazyListScope.uniqueItems(
    items: List<T>,
    noinline key: (T) -> Any,
    noinline contentType: (T) -> Any? = { null },
    crossinline itemContent: @Composable androidx.compose.foundation.lazy.LazyItemScope.(T) -> Unit,
) {
    val keys = uniqueKeys(items, key)
    items(items.size, key = { keys[it] }, contentType = { contentType(items[it]) }) { itemContent(items[it]) }
}

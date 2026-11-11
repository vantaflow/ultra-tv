package com.ultratv.tv.nativeapp.data.repo

/**
 * Logique PURE de l'ordre personnel des chaînes (sans Android ni base) : fusion des places choisies avec l'ordre naturel,
 * déplacement d'une chaîne, calcul des lignes à réécrire. Les requêtes SQL du ChannelDao (`…Ordered`) appliquent la même
 * règle : place choisie d'abord, puis ordre naturel ; [NO_POSITION] doit rester égal à la constante écrite dans ces requêtes.
 */
object ChannelOrder {
    /** Place des chaînes qui n'en ont pas : elles suivent toutes celles qui en ont une (ordre naturel conservé entre elles). */
    const val NO_POSITION = Int.MAX_VALUE

    /** Liste « favoris » : valeur de `channel_order.scope` (ne peut pas être l'identifiant d'une catégorie). */
    const val FAVORITES_SCOPE = "__fav__"

    /**
     * Fusionne l'ordre naturel et les places choisies ([positions] : clé → place). Tri STABLE : à place égale (ou sans
     * place), l'ordre naturel est conservé.
     */
    fun <T> merge(natural: List<T>, positions: Map<String, Int>, key: (T) -> String): List<T> =
        if (positions.isEmpty()) natural else natural.sortedBy { positions[key(it)] ?: NO_POSITION }

    /**
     * Déplace [key] de [delta] rangs (négatif = vers le haut) dans [keys], borné aux extrémités. Liste inchangée si la clé
     * est absente ou si le déplacement est nul.
     */
    fun move(keys: List<String>, key: String, delta: Int): List<String> {
        val from = keys.indexOf(key)
        if (from < 0 || delta == 0) return keys
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        if (to == from) return keys
        return keys.toMutableList().also { it.removeAt(from); it.add(to, key) }
    }

    /** Places 1, 2, 3… dans l'ordre de [keys]. */
    fun positionsOf(keys: List<String>): Map<String, Int> =
        LinkedHashMap<String, Int>(keys.size * 2).also { m -> keys.forEachIndexed { i, k -> m[k] = i + 1 } }

    /** Places à réécrire pour passer de [old] à [new] (seulement celles qui changent) : un déplacement d'un rang = 2 lignes. */
    fun changed(old: Map<String, Int>, new: Map<String, Int>): Map<String, Int> =
        new.filter { (k, v) -> old[k] != v }

    /** Remise à zéro d'une liste : aucune place choisie, donc l'ordre naturel. */
    fun reset(): Map<String, Int> = emptyMap()
}

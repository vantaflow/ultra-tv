package com.ultratv.tv.nativeapp.data.repo

/**
 * Petit cache LRU borné et sûr entre fils, pour mémoriser le résultat d'un nettoyage de nom (fonction pure). Les noms
 * de chaînes reviennent sans cesse (composition des cases du guide, zapping, clics) : sans lui, chaque passage refaisait
 * toutes les expressions régulières, sur le fil principal (ANR MatcherNative.findImpl).
 */
class NameMemo<K : Any, V : Any>(private val capacity: Int) {
    private val map = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
    }

    /** Valeur mémorisée pour [key], ou calculée par [compute] (hors verrou) puis mémorisée. */
    fun getOrPut(key: K, compute: () -> V): V {
        synchronized(map) { map[key] }?.let { return it }
        val v = compute()
        synchronized(map) { map[key]?.let { return it }; map[key] = v }
        return v
    }
}

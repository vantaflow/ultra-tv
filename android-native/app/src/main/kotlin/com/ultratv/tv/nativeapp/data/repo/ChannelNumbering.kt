package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Numérotation d'affichage PAR PROFIL (logique pure). `channel.seq` reste GLOBAL (une chaîne masquée par un seul profil
 * garde son numéro en base) ; pour le profil courant, le numéro affiché d'une chaîne est
 * `seq − (nombre de chaînes masquées par ce profil, dans la même catégorie, dont le seq est plus petit)`.
 * Masquer 1 et 2 fait donc de la 3 la chaîne 1, sans trou, comme quand tous les profils les masquent.
 *
 * [hidden] : (source, catégorie) → seq masqués par le profil, triés et sans doublon. Minuscule (seulement les chaînes
 * masquées) ; vide pour un profil qui ne masque rien.
 */
class ChannelNumbering(private val hidden: Map<Key, IntArray> = emptyMap()) {
    data class Key(val providerId: Long, val categoryId: String?)

    /** Vrai si le profil ne masque aucune chaîne numérotée (numéros = seq). */
    val isIdentity: Boolean get() = hidden.isEmpty()

    /** Numéro affiché d'un [seq] (> 0) de la catégorie ; seq <= 0 inchangé. */
    fun shown(providerId: Long, categoryId: String?, seq: Int): Int {
        if (seq <= 0 || hidden.isEmpty()) return seq
        val arr = hidden[Key(providerId, categoryId)] ?: return seq
        return seq - countBelow(arr, seq)
    }

    /**
     * Numéro d'affichage d'une chaîne : séquentiel du profil, sinon celui de la source (`num`, jamais décalé : il vient
     * du fournisseur) ; 0 si inconnu.
     */
    fun numberOf(c: ChannelEntity): Int = if (c.seq > 0) shown(c.providerId, c.categoryId, c.seq) else c.num.coerceAtLeast(0)

    /**
     * Inverse : le seq (global) de la chaîne qui affiche [number] dans la catégorie, ou null si [number] <= 0.
     * Point fixe `seq = number + masqués avant seq` : on avance sur les seq masqués triés. Le résultat n'est jamais un seq masqué.
     */
    fun seqOf(providerId: Long, categoryId: String?, number: Int): Int? {
        if (number <= 0) return null
        val arr = hidden[Key(providerId, categoryId)] ?: return number
        var s = number
        for (h in arr) { if (h <= s) s++ else break }
        return s
    }

    private fun countBelow(arr: IntArray, seq: Int): Int {   // nombre d'éléments < seq (recherche dichotomique)
        var lo = 0
        var hi = arr.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (arr[mid] < seq) lo = mid + 1 else hi = mid }
        return lo
    }

    companion object {
        val NONE = ChannelNumbering()

        /** Construit depuis les lignes (source, catégorie, seq) des chaînes masquées ; seq <= 0 ignorés. */
        fun of(rows: List<Triple<Long, String?, Int>>): ChannelNumbering {
            if (rows.isEmpty()) return NONE
            val m = rows.filter { it.third > 0 }.groupBy({ Key(it.first, it.second) }, { it.third })
                .mapValues { (_, v) -> v.distinct().sorted().toIntArray() }
            return if (m.isEmpty()) NONE else ChannelNumbering(m)
        }
    }
}

/**
 * Numérotation du profil COURANT, tenue à jour d'après `channel_hidden_now` (la projection du profil) : le Flow Room se
 * relance au masquage, à l'affichage et au changement de profil (la projection est réécrite). Une seule requête, limitée aux
 * chaînes masquées : rien par ligne, quelques octets en mémoire.
 */
@Singleton
class ProfileNumbering @Inject constructor(private val db: UltraDb) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<ChannelNumbering> by lazy {
        db.profileViewDao().observeHiddenSeqs()
            .map { rows -> ChannelNumbering.of(rows.map { Triple(it.providerId, it.categoryId, it.seq) }) }
            .stateIn(scope, SharingStarted.Eagerly, ChannelNumbering.NONE)
    }

    /** Numérotation actuelle (lecture ponctuelle hors Compose). */
    val current: ChannelNumbering get() = state.value
}

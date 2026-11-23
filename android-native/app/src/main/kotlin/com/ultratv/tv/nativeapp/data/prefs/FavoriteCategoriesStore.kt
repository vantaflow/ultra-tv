package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catégories FAVORITES par source et par type (issue #249), partagées entre appareils par le champ `favCats` des réglages
 * d'affichage (voir DisplayPrefs). Stockées hors de Room (pas de migration), comme [NewCategoriesStore] : clé
 * `kind:providerId:remoteId` avec `kind` = LIVE / MOVIE / SERIES, identifiants = ceux du FOURNISSEUR.
 *
 * `known` : sources dont l'état des favorites est établi (reçu du cloud, ou modifié ici). Tant qu'une source n'y est pas,
 * l'appli ne publie PAS de `favCats` pour elle : un appareil qui n'a rien lu ne doit pas effacer les favorites du compte.
 */
@Singleton
class FavoriteCategoriesStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("favorite_categories", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _keys = MutableStateFlow(sp.getStringSet(SET, emptySet()).orEmpty().toSet())
    /** Clés `kind:providerId:remoteId` de toutes les favorites. */
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    /** Identifiants favoris d'une source pour un type (LIVE / MOVIE / SERIES). */
    fun ids(pid: Long, kind: String): Set<String> = idsOf(_keys.value, pid, kind)

    /** L'état des favorites de cette source est-il établi (donc publiable) ? */
    fun isKnown(pid: Long): Boolean = sp.getStringSet(KNOWN, emptySet()).orEmpty().contains(pid.toString())

    /** Marque ou démarque une catégorie (action de l'utilisateur) : la source devient « établie ». */
    fun setFavorite(pid: Long, kind: String, id: String, fav: Boolean) = edit(pid) { s -> if (fav) s += key(pid, kind, id) else s -= key(pid, kind, id) }

    /** Réglages reçus du cloud : [byKind] (clés du protocole live / movie / series → identifiants) remplace l'état de la source. */
    fun replaceFromCloud(pid: Long, byKind: Map<String, List<String>>, kinds: Map<String, String>) = edit(pid) { s ->
        s.removeAll { it.split(':', limit = 3).getOrNull(1) == pid.toString() }
        for ((kind, wire) in kinds) byKind[wire].orEmpty().forEach { s += key(pid, kind, it) }
    }

    fun forgetProvider(pid: Long) = edit(null) { s -> s.removeAll { k -> k.split(':', limit = 3).getOrNull(1) == pid.toString() } }

    private inline fun edit(markKnown: Long?, block: (MutableSet<String>) -> Unit) = synchronized(lock) {
        val s = HashSet(sp.getStringSet(SET, emptySet()).orEmpty())
        block(s)
        val e = sp.edit().putStringSet(SET, s)
        if (markKnown != null) e.putStringSet(KNOWN, HashSet(sp.getStringSet(KNOWN, emptySet()).orEmpty()).apply { add(markKnown.toString()) })
        e.commit()
        _keys.value = s.toSet()
    }

    companion object {
        private const val SET = "keys"
        private const val KNOWN = "known"
        fun key(pid: Long, kind: String, id: String) = "$kind:$pid:$id"

        /** Identifiants favoris d'un type dans un ensemble de clés. Pur. */
        fun idsOf(keys: Set<String>, pid: Long, kind: String): Set<String> {
            val prefix = "$kind:$pid:"
            return keys.filter { it.startsWith(prefix) }.mapTo(LinkedHashSet()) { it.substring(prefix.length) }
        }

        /** Les favorites en tête (ordre relatif conservé de part et d'autre : l'ordre personnalisé des autres ne bouge pas). Pur. */
        fun <T> favoritesFirst(list: List<T>, favs: Set<String>, idOf: (T) -> String): List<T> {
            if (favs.isEmpty() || list.isEmpty()) return list
            val (f, rest) = list.partition { idOf(it) in favs }
            return if (f.isEmpty()) list else f + rest
        }
    }
}

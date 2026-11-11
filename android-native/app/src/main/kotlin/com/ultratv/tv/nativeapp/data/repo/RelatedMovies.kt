package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.tmdb.PersonCredit
import com.ultratv.tv.nativeapp.data.tmdb.PersonLogic

/**
 * « Parce que vous avez regardé… » : rapprochement des films recommandés par TMDB avec le catalogue local (règles pures,
 * testées sans Android). Un titre du catalogue correspond quand son titre normalisé est celui de l'œuvre (titre ou titre
 * original) et que l'année, si les deux sont connues, est à ±1 an ([PersonLogic.matches]).
 */
object RelatedMovies {
    /** Nombre maximal de titres TMDB examinés (chaque titre coûte une recherche plein texte locale). */
    const val MAX_CREDITS = 40

    /**
     * Pour chaque titre TMDB, dans l'ordre : le premier film du catalogue qui correspond ([lookup] renvoie les candidats
     * d'un titre cherché). Sans doublon, sans le film de départ ([seedId] / [seedTitle], y compris une autre version de
     * ce titre), au plus [limit]. Une erreur de [lookup] est ignorée pour ce titre.
     */
    suspend fun <T> match(
        credits: List<PersonCredit>, seedId: Long, seedTitle: String, limit: Int,
        idOf: (T) -> Long, titleOf: (T) -> String, yearOf: (T) -> Int?, accept: (T) -> Boolean = { true },
        lookup: suspend (String) -> List<T>,
    ): List<T> {
        val seedKey = PersonLogic.key(seedTitle)
        val out = LinkedHashMap<Long, T>()
        for (c in credits.take(MAX_CREDITS)) {
            if (out.size >= limit) break
            var found: T? = null
            for (q in listOf(c.title) + listOfNotNull(c.originalTitle)) {
                val candidates = try { lookup(q) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
                found = candidates.firstOrNull { accept(it) && idOf(it) != seedId && PersonLogic.key(titleOf(it)) != seedKey && PersonLogic.matches(c, titleOf(it), yearOf(it)) }
                if (found != null) break
            }
            if (found == null) continue
            out.putIfAbsent(idOf(found), found)
        }
        return out.values.toList()
    }

    /** [primary] (ordre TMDB) puis le classement local, sans doublon, au plus [limit]. */
    fun <T> complete(primary: List<T>, local: List<T>, limit: Int, idOf: (T) -> Long): List<T> {
        val seen = HashSet<Long>()
        return (primary + local).filter { seen.add(idOf(it)) }.take(limit)
    }
}

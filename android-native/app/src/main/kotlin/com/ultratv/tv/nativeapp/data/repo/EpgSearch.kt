package com.ultratv.tv.nativeapp.data.repo

import androidx.room.Embedded
import com.ultratv.tv.nativeapp.data.db.ChannelEntity

/** Ligne brute de la requête EPG : la chaîne + le programme qui correspond. */
data class ProgramHitRow(
    @Embedded val channel: ChannelEntity,
    val pTitle: String,
    val pStart: Long,
    val pEnd: Long,
)

/** Un programme trouvé par la recherche (une ligne par chaîne). */
data class ProgramHit(val channel: ChannelEntity, val title: String, val startMs: Long, val endMs: Long, val live: Boolean)

/** Recherche de programmes EPG : motif SQL large + filtre exact (sans accents ni casse) + un programme par chaîne. */
object EpgSearch {
    const val WINDOW_MS = 7L * 24 * 3_600_000
    const val MAX_HITS = 24
    const val SQL_LIMIT = 600
    private const val ESC = '\\'
    private val accentable = setOf('a', 'e', 'i', 'o', 'u', 'y', 'c', 'n')

    /**
     * Motif LIKE (ESCAPE '\') : les lettres qui existent avec accents deviennent `_` (SQLite ne plie que le
     * ASCII) ; le filtre exact [select] retire ensuite les faux positifs. Null si la requête est vide.
     */
    fun likePattern(query: String): String? {
        val q = SearchDedup.fold(query)
        if (q.isEmpty()) return null
        val sb = StringBuilder("%")
        for (ch in q) when {
            ch in accentable -> sb.append('_')
            ch == '%' || ch == '_' || ch == ESC -> sb.append(ESC).append(ch)
            else -> sb.append(ch)
        }
        return sb.append('%').toString()
    }

    /** Filtre exact, un programme par chaîne (le plus proche : en cours sinon prochain), en cours d'abord, 24 max. */
    fun select(rows: List<ProgramHitRow>, query: String, nowMs: Long, limit: Int = MAX_HITS): List<ProgramHit> {
        val q = SearchDedup.fold(query)
        if (q.isEmpty()) return emptyList()
        return rows.asSequence()
            .filter { it.pEnd > nowMs && it.pStart <= nowMs + WINDOW_MS && SearchDedup.fold(it.pTitle).contains(q) }
            .groupBy { it.channel.id }
            .values
            .map { g -> g.minWith(compareBy<ProgramHitRow> { it.pStart > nowMs }.thenBy { it.pStart }) }
            .sortedWith(compareBy<ProgramHitRow> { it.pStart > nowMs }.thenBy { it.pStart }.thenBy { it.channel.title })
            .take(limit)
            .map { ProgramHit(it.channel, it.pTitle, it.pStart, it.pEnd, live = it.pStart <= nowMs) }
    }
}

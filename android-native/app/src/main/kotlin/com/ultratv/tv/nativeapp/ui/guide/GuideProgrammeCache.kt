package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import java.util.Calendar

/**
 * Cache des programmes du Guide, PAR CHAÎNE et PAR PLAGE de temps (pur, testé en JVM) : un décalage de la fenêtre ne le vide
 * plus, seules les plages manquantes sont demandées, et une chaîne dont rien n'a changé garde la MÊME liste (les lignes qui
 * ne changent pas ne sont pas recomposées).
 *
 * Une entrée couvre [from, to] pour sa chaîne. Une plage qui touche celle déjà chargée l'étend ; une plage disjointe (changement
 * de jour) la remplace (jamais de trou déclaré chargé). [invalidate] (fin d'une synchro du guide) marque tout comme à
 * recharger SANS rien effacer : les lignes gardent leurs listes jusqu'au remplacement. Au plus [maxChannels] chaînes
 * (les moins récemment demandées sortent d'abord).
 */
internal class GuideProgrammeCache(private val maxChannels: Int = 200) {
    private class Entry(val from: Long, val to: Long, val generation: Int, val raw: List<EpgEntity>, val rows: List<EpgEntity>)

    private val entries = object : LinkedHashMap<Long, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?) = size > maxChannels
    }
    private var generation = 0

    /** Chaînes de [ids] dont la plage [from, to] n'est pas déjà chargée (ou dont le chargement date d'avant la dernière synchro). */
    fun missing(ids: Collection<Long>, from: Long, to: Long): List<Long> =
        ids.filter { id -> val e = entries[id]; e == null || e.generation != generation || e.from > from || e.to < to }

    /** Tout est à recharger (fin de synchro) ; les listes affichées restent en place jusqu'à leur remplacement. */
    fun invalidate() { generation++ }

    /**
     * Range [fetched] (programmes lus pour [ids] sur [from, to]). Rend vrai si la liste affichée d'une chaîne a changé
     * (nouvelle chaîne non vide, contenu différent) : alors, et alors seulement, il faut republier [snapshot].
     */
    fun put(ids: Collection<Long>, from: Long, to: Long, fetched: List<EpgEntity>): Boolean {
        val byChannel = fetched.groupBy { it.channelId }
        var changed = false
        for (id in ids) {
            val old = entries[id]
            val incoming = byChannel[id].orEmpty()
            val extend = old != null && old.generation == generation && old.from <= to && old.to >= from
            val raw = if (extend) mergeById(old!!.raw, incoming) else incoming
            val rows = normalizeRow(raw)
            val keep = old != null && old.rows == rows
            entries[id] = Entry(
                if (extend) minOf(old!!.from, from) else from, if (extend) maxOf(old!!.to, to) else to, generation, raw,
                if (keep) old!!.rows else rows,
            )
            if (!keep && (rows.isNotEmpty() || old?.rows?.isNotEmpty() == true)) changed = true
        }
        return changed
    }

    /** Listes à afficher, par chaîne (les chaînes sans programme n'y figurent pas). */
    fun snapshot(): Map<Long, List<EpgEntity>> {
        val out = HashMap<Long, List<EpgEntity>>(entries.size * 2)
        for ((id, e) in entries) if (e.rows.isNotEmpty()) out[id] = e.rows
        return out
    }

    fun clear() { entries.clear() }

    private fun mergeById(old: List<EpgEntity>, incoming: List<EpgEntity>): List<EpgEntity> {
        if (incoming.isEmpty()) return old
        val byId = LinkedHashMap<Long, EpgEntity>(old.size + incoming.size)
        for (e in old) byId[e.id] = e
        for (e in incoming) byId[e.id] = e
        return byId.values.toList()
    }
}

/**
 * Plage de chaînes du Guide pour la fenêtre commençant à [windowStart] : TOUTE la journée locale de la fenêtre, plus la
 * fenêtre qui la déborde après minuit. Elle ne change qu'au changement de jour : ◀ / ▶ au bord de la fenêtre (90 min) ne
 * reconstruit plus la liste paginée des chaînes. Toute fenêtre [w, w + 3 h] commençant ce jour-là y tient.
 */
internal fun guideChannelRange(windowStart: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = windowStart
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val dayStart = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return dayStart to cal.timeInMillis + GUIDE_WINDOW_MS
}

/** Plage de programmes chargée pour les lignes visibles quand la fenêtre commence à [windowStart] : la fenêtre et sa voisine de chaque côté. */
internal fun guideNeededRange(windowStart: Long): Pair<Long, Long> = (windowStart - GUIDE_WINDOW_MS) to (windowStart + 2 * GUIDE_WINDOW_MS)

/** Plage réellement demandée à la base quand il en manque : la plage utile élargie d'une fenêtre (3 h) de chaque côté, pour que plusieurs décalages de suite n'aient plus rien à lire. */
internal fun guideFetchRange(windowStart: Long): Pair<Long, Long> = (windowStart - 2 * GUIDE_WINDOW_MS) to (windowStart + 3 * GUIDE_WINDOW_MS)

package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.db.EpgEntity

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
    companion object { const val KEEP_MARGIN_MS = 6 * 3_600_000L }
    private class Entry(val from: Long, val to: Long, val generation: Int, val raw: List<EpgEntity>, val rows: List<EpgEntity>)

    private val entries = object : LinkedHashMap<Long, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?) = size > maxChannels
    }
    /** Génération courante (incrémentée par [invalidate]) : à lire AVANT une requête et à rendre à [put]. */
    var generation = 0
        private set

    /** Chaînes de [ids] dont la plage [from, to] n'est pas déjà chargée (ou dont le chargement date d'avant la dernière synchro). */
    fun missing(ids: Collection<Long>, from: Long, to: Long): List<Long> =
        ids.filter { id -> val e = entries[id]; e == null || e.generation != generation || e.from > from || e.to < to }

    /** Tout est à recharger (fin de synchro) ; les listes affichées restent en place jusqu'à leur remplacement. */
    fun invalidate() { generation++ }

    /**
     * Range [fetched] (programmes lus pour [ids] sur [from, to]). Rend vrai si la liste affichée d'une chaîne a changé
     * (nouvelle chaîne non vide, contenu différent) : alors, et alors seulement, il faut republier [snapshot].
     */
    fun put(ids: Collection<Long>, from: Long, to: Long, fetched: List<EpgEntity>, readGeneration: Int = generation): Boolean {
        val byChannel = fetched.groupBy { it.channelId }
        var changed = false
        // Lu avant une fin de synchro survenue pendant la requête : rangé comme périmé (relu au prochain passage), jamais « frais ».
        val gen = readGeneration
        // Mémoire bornée : chaque entrée garde au plus la plage lue élargie de [KEEP_MARGIN_MS] de chaque côté.
        val keepFrom = from - KEEP_MARGIN_MS; val keepTo = to + KEEP_MARGIN_MS
        for (id in ids) {
            val old = entries[id]
            val incoming = byChannel[id].orEmpty()
            val extend = old != null && old.generation == gen && old.from <= to && old.to >= from
            var raw = if (extend) mergeByStart(old!!.raw, incoming) else incoming
            var eFrom = if (extend) minOf(old!!.from, from) else from
            var eTo = if (extend) maxOf(old!!.to, to) else to
            if (eFrom < keepFrom || eTo > keepTo) {
                eFrom = maxOf(eFrom, keepFrom); eTo = minOf(eTo, keepTo)
                raw = raw.filter { it.endMs >= eFrom && it.startMs <= eTo }
            }
            val rows = normalizeRow(raw)
            val keep = old != null && old.rows == rows
            entries[id] = Entry(eFrom, eTo, gen, raw, if (keep) old!!.rows else rows)
            // Chaîne lue pour la première fois, même sans programme : publiée (« lue, rien à cette heure » ≠ « pas encore lue »).
            if (old == null || (!keep && (rows.isNotEmpty() || old.rows.isNotEmpty()))) changed = true
        }
        return changed
    }

    /** Listes à afficher, par chaîne lue (liste vide : chaîne lue, sans programme sur la plage ; absente : pas encore lue). */
    fun snapshot(): Map<Long, List<EpgEntity>> {
        val out = HashMap<Long, List<EpgEntity>>(entries.size * 2)
        for ((id, e) in entries) out[id] = e.rows
        return out
    }

    fun clear() { entries.clear() }

    /**
     * Fusion par DÉBUT (unique par chaîne en base) : un programme réinséré par une synchro en cours (nouvel identifiant, même
     * horaire) remplace l'ancien au lieu de s'y ajouter ; la version lue en dernier gagne.
     */
    private fun mergeByStart(old: List<EpgEntity>, incoming: List<EpgEntity>): List<EpgEntity> {
        if (incoming.isEmpty()) return old
        val byStart = LinkedHashMap<Long, EpgEntity>(old.size + incoming.size)
        for (e in old) byStart[e.startMs] = e
        for (e in incoming) byStart[e.startMs] = e
        return byStart.values.toList()
    }
}

/**
 * Plage de la liste des chaînes du Guide : TOUTE chaîne qui a un guide, quelle que soit la fenêtre affichée. La liste
 * paginée ne dépend donc jamais du temps : ◀ / ▶ au bord (même en passant minuit) et ⏪ ⏩ ne la reconstruisent pas (un
 * nouveau Pager repartait du premier lot de 30 : au-delà, la ligne focalisée disparaissait avec son focus). Une chaîne sans
 * programme dans la fenêtre courante reste une ligne vide, traversée par ▼ / ▲.
 */
internal val GUIDE_CHANNEL_RANGE: Pair<Long, Long> = 0L to Long.MAX_VALUE

/** Plage de programmes chargée pour les lignes visibles quand la fenêtre commence à [windowStart] : la fenêtre et sa voisine de chaque côté. */
internal fun guideNeededRange(windowStart: Long): Pair<Long, Long> = (windowStart - GUIDE_WINDOW_MS) to (windowStart + 2 * GUIDE_WINDOW_MS)

/** Plage réellement demandée à la base quand il en manque : la plage utile élargie d'une fenêtre (3 h) de chaque côté, pour que plusieurs décalages de suite n'aient plus rien à lire. */
internal fun guideFetchRange(windowStart: Long): Pair<Long, Long> = (windowStart - 2 * GUIDE_WINDOW_MS) to (windowStart + 3 * GUIDE_WINDOW_MS)

package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity

/** Attribue un numéro séquentiel stable par catégorie (séparateurs et entrées masquées exclus), dans l'ordre d'arrivée. */
class ChannelSeq {
    private val counters = HashMap<String?, Int>()
    fun assign(batch: List<ChannelEntity>): List<ChannelEntity> = batch.map { c ->
        if (c.isSeparator || c.junk) c
        else { val n = (counters[c.categoryId] ?: 0) + 1; counters[c.categoryId] = n; c.copy(seq = n) }
    }
}

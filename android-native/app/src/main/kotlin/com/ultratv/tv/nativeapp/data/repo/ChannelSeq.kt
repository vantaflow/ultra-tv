package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity

/**
 * Attribue un numéro séquentiel stable par catégorie (séparateurs et entrées masquées exclus), dans l'ordre d'arrivée.
 * [skip] : identifiants fournisseur des chaînes masquées par l'utilisateur. Elles ne prennent AUCUN numéro : masquer les
 * chaînes 1 et 2 fait de la 3 la chaîne 1, à l'écran comme à la télécommande.
 */
class ChannelSeq(private val skip: Set<String> = emptySet()) {
    private val counters = HashMap<String?, Int>()
    fun assign(batch: List<ChannelEntity>): List<ChannelEntity> = batch.map { c ->
        if (c.isSeparator || c.junk || c.remoteId in skip) c
        else { val n = (counters[c.categoryId] ?: 0) + 1; counters[c.categoryId] = n; c.copy(seq = n) }
    }

    companion object {
        /**
         * Resserre les numéros d'une catégorie après le retrait de chaînes : [rows] (id, numéro) dans l'ordre actuel des
         * numéros reçoivent 1, 2, 3… Rend seulement les lignes dont le numéro change. Pur.
         */
        fun compact(rows: List<Pair<Long, Int>>): List<Pair<Long, Int>> =
            rows.withIndex().mapNotNull { (i, r) -> if (r.second == i + 1) null else r.first to i + 1 }
    }
}

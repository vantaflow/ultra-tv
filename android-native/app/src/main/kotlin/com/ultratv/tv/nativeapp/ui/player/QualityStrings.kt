package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n

/**
 * Libellés de l'entrée « Qualité » du panneau des pistes, à part de [PlayerExtraStrings] (relu par le golden) : même
 * principe que ChannelOrderStrings.
 */
class QualityStrings(val lang: AppLang) {
    private fun tx(id: Int): String = I18n.string(lang, id)

    val title get() = tx(R.string.pq_title)
    val single get() = tx(R.string.pq_single)
    val aboveCap get() = tx(R.string.pq_above_cap)
}

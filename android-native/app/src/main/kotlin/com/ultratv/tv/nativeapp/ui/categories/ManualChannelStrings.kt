package com.ultratv.tv.nativeapp.ui.categories

import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n

/**
 * Libellés de l'ajout manuel de chaînes, à part de [com.ultratv.tv.nativeapp.i18n.DesignStrings] : ni le golden ni les
 * porteurs existants n'ont à bouger (même principe que ChannelOrderStrings).
 */
class ManualChannelStrings(val lang: AppLang) {
    private fun tx(id: Int): String = I18n.string(lang, id)

    val add get() = tx(R.string.mc_add)
    val editTitle get() = tx(R.string.mc_edit_title)
    val sub get() = tx(R.string.mc_sub)
    val name get() = tx(R.string.mc_name)
    val url get() = tx(R.string.mc_url)
    val logo get() = tx(R.string.mc_logo)
    val epg get() = tx(R.string.mc_epg)
    val category get() = tx(R.string.mc_category)
    val catCustom get() = tx(R.string.mc_cat_custom)
    val catHint get() = tx(R.string.mc_cat_hint)
    val save get() = tx(R.string.mc_save)
    val delete get() = tx(R.string.mc_delete)
    val edit get() = tx(R.string.mc_edit)
    val badge get() = tx(R.string.mc_badge)
    val badUrl get() = tx(R.string.mc_bad_url)
}

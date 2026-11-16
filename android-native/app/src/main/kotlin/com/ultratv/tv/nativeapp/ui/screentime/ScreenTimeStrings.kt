package com.ultratv.tv.nativeapp.ui.screentime

import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n

/** Textes des horaires de visionnage et du temps d'écran (toutes les langues, voir strings.xml « st_* »). */
class ScreenTimeStrings(private val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    val title get() = tx(R.string.st_title)
    val sub get() = tx(R.string.st_sub)
    val limit get() = tx(R.string.st_limit)
    val limitNone get() = tx(R.string.st_limit_none)
    fun minutes(n: Int) = tx(R.string.st_minutes, n)
    val free get() = tx(R.string.st_free)
    val blockedDay get() = tx(R.string.st_blocked_day)
    val pickHours get() = tx(R.string.st_pick_hours)
    val start get() = tx(R.string.st_start)
    val end get() = tx(R.string.st_end)
    val clear get() = tx(R.string.st_clear)
    val off get() = tx(R.string.st_off)
    fun summaryLimit(n: Int) = tx(R.string.st_summary_limit, n)
    val summaryHours get() = tx(R.string.st_summary_hours)
    val needCode get() = tx(R.string.st_need_code)
    val pinTitle get() = tx(R.string.st_pin_title)
    val blockOutside get() = tx(R.string.st_block_outside)
    val blockLimit get() = tx(R.string.st_block_limit)
    fun blockNext(whenText: String) = tx(R.string.st_block_next, whenText)
    val blockNone get() = tx(R.string.st_block_none)
    val unlock get() = tx(R.string.st_unlock)
    val unlockExtra get() = tx(R.string.st_unlock_extra)
    val unlockToday get() = tx(R.string.st_unlock_today)
    fun warn(min: Int) = tx(R.string.st_warn, min)
}

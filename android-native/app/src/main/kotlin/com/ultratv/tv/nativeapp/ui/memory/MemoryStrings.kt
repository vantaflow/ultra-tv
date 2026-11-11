package com.ultratv.tv.nativeapp.ui.memory

import androidx.compose.runtime.Composable
import com.ultratv.tv.nativeapp.data.memory.SizeFormat
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/**
 * Libellés de Réglages › Mémoire et stockage (en / fr / es / ar), à part des autres tables pour ne pas se marcher
 * dessus. Vérifiés par TranslationsCompleteTest.
 */
class MemoryStrings(val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)

    fun size(bytes: Long) = SizeFormat.format(bytes, lang)

    val title get() = tx(R.string.mem_title)
    val subtitle get() = tx(R.string.mem_subtitle)

    // Espace utilisé
    val groupUsed get() = tx(R.string.mem_group_used)
    val calculating get() = tx(R.string.mem_calculating)
    val database get() = tx(R.string.mem_database)
    val databaseHint get() = tx(R.string.mem_database_hint)
    val images get() = tx(R.string.mem_images)
    val downloads get() = tx(R.string.mem_downloads)
    val timeshift get() = tx(R.string.mem_timeshift)
    val temp get() = tx(R.string.mem_temp)
    val tempHint get() = tx(R.string.mem_temp_hint)
    val logs get() = tx(R.string.mem_logs)
    val totalUsed get() = tx(R.string.mem_total_used)
    val freeSpace get() = tx(R.string.mem_free_space)

    // Mémoire vive
    val groupRam get() = tx(R.string.mem_group_ram)
    val ramAvailable get() = tx(R.string.mem_ram_available)
    fun ramValue(avail: Long, total: Long) = tx(R.string.mem_ram_value, size(avail), size(total))
    val device get() = tx(R.string.mem_device)
    val deviceLimited get() = tx(R.string.mem_device_limited)
    val deviceComfortable get() = tx(R.string.mem_device_comfortable)
    val limitedNote get() = tx(R.string.mem_limited_note)

    // Actions
    val groupActions get() = tx(R.string.mem_group_actions)
    val clearImages get() = tx(R.string.mem_clear_images)
    val clearImagesHint get() = tx(R.string.mem_clear_images_hint)
    val clearTemp get() = tx(R.string.mem_clear_temp)
    val clearTempHint get() = tx(R.string.mem_clear_temp_hint)
    val compact get() = tx(R.string.mem_compact)
    val compactHint get() = tx(R.string.mem_compact_hint)
    val clearHistory get() = tx(R.string.mem_clear_history)
    val clearHistoryHint get() = tx(R.string.mem_clear_history_hint)
    val clearResume get() = tx(R.string.mem_clear_resume)
    val clearResumeHint get() = tx(R.string.mem_clear_resume_hint)
    val clearTimeshift get() = tx(R.string.mem_clear_timeshift)
    val clearTimeshiftHint get() = tx(R.string.mem_clear_timeshift_hint)
    val working get() = tx(R.string.mem_working)

    val scopeTitle get() = tx(R.string.mem_scope_title)
    val scopeCurrent get() = tx(R.string.mem_scope_current)
    val scopeAll get() = tx(R.string.mem_scope_all)

    val confirm get() = tx(R.string.mem_confirm)
    val cancel get() = tx(R.string.mem_cancel)
    val confirmImagesTitle get() = tx(R.string.mem_confirm_images_title)
    val confirmImagesBody get() = tx(R.string.mem_confirm_images_body)
    val confirmTempTitle get() = tx(R.string.mem_confirm_temp_title)
    val confirmTempBody get() = tx(R.string.mem_confirm_temp_body)
    val confirmCompactTitle get() = tx(R.string.mem_confirm_compact_title)
    val confirmCompactBody get() = tx(R.string.mem_confirm_compact_body)
    fun confirmHistoryTitle(all: Boolean) = if (all)
        tx(R.string.mem_confirm_history_title_1)
    else tx(R.string.mem_confirm_history_title_2)
    val confirmHistoryBody get() = tx(R.string.mem_confirm_history_body)
    fun confirmResumeTitle(all: Boolean) = if (all)
        tx(R.string.mem_confirm_resume_title_1)
    else tx(R.string.mem_confirm_resume_title_2)
    val confirmResumeBody get() = tx(R.string.mem_confirm_resume_body)
    val confirmTimeshiftTitle get() = tx(R.string.mem_confirm_timeshift_title)
    val confirmTimeshiftBody get() = tx(R.string.mem_confirm_timeshift_body)

    // Résultats
    fun freed(bytes: Long) = tx(R.string.mem_freed, size(bytes))
    val nothingToFree get() = tx(R.string.mem_nothing_to_free)
    fun cleared(n: Int) = tx(R.string.mem_cleared, n)
    val blockedSync get() = tx(R.string.mem_blocked_sync)
    val timeshiftBusy get() = tx(R.string.mem_timeshift_busy)
    val noSpace get() = tx(R.string.mem_no_space)
    val failed get() = tx(R.string.mem_failed)

    // Réglages
    val groupSettings get() = tx(R.string.mem_group_settings)
    val imageLimit get() = tx(R.string.mem_image_limit)
    val imageLimitHint get() = tx(R.string.mem_image_limit_hint)
    fun autoLimit(bytes: Long) = tx(R.string.mem_auto_limit, size(bytes))
    val auto get() = tx(R.string.mem_auto)
    val on get() = tx(R.string.mem_on)
    val off get() = tx(R.string.mem_off)
    val autoClean get() = tx(R.string.mem_auto_clean)
    val autoCleanHint get() = tx(R.string.mem_auto_clean_hint)
    val economy get() = tx(R.string.mem_economy)
    val economyHint get() = tx(R.string.mem_economy_hint)
    fun economyAuto(limited: Boolean) = "$auto · " + if (limited) on else off
}

@Composable
fun rememberMemoryStrings(): MemoryStrings {
    val lang = LocalDs.current.lang
    return androidx.compose.runtime.remember(lang) { MemoryStrings(lang) }
}

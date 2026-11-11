package com.ultratv.tv.nativeapp.ui.profile

import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/** Textes de l'écran « Qui regarde ? » et de Réglages › Profils (EN / FR / ES / AR). */
class ProfileStrings(private val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)
    val whoWatching get() = tx(R.string.prof_who_watching)
    val add get() = tx(R.string.prof_add)
    val manage get() = tx(R.string.prof_manage)
    val done get() = tx(R.string.prof_done)
    val protectedHint get() = tx(R.string.prof_protected_hint)
    val kidsHint get() = tx(R.string.prof_kids_hint)
    val lastHint get() = tx(R.string.prof_last_hint)
    val pinTitle get() = tx(R.string.prof_pin_title)
    fun pinSubtitle(name: String) = tx(R.string.prof_pin_subtitle, name)
    val wrongPin get() = tx(R.string.prof_wrong_pin)
    val settingsTitle get() = tx(R.string.prof_settings_title)
    val settingsSub get() = tx(R.string.prof_settings_sub)
    val newProfile get() = tx(R.string.prof_new_profile)
    val rename get() = tx(R.string.prof_rename)
    val color get() = tx(R.string.prof_color)
    val kidsProfile get() = tx(R.string.prof_kids_profile)
    val kidsHelp get() = tx(R.string.prof_kids_help)
    val pin get() = tx(R.string.prof_pin)
    val pinNone get() = tx(R.string.prof_pin_none)
    val pinSet get() = tx(R.string.prof_pin_set)
    val pinRemove get() = tx(R.string.prof_pin_remove)
    val delete get() = tx(R.string.prof_delete)
    val deleteLast get() = tx(R.string.prof_delete_last)
    fun deleteConfirm(name: String) = tx(R.string.prof_delete_confirm, name)
    val confirm get() = tx(R.string.prof_confirm)
    val cancel get() = tx(R.string.prof_cancel)
    val startup get() = tx(R.string.prof_startup)
    val alwaysAsk get() = tx(R.string.prof_always_ask)
    val lastProfile get() = tx(R.string.prof_last_profile)
    val switchProfile get() = tx(R.string.prof_switch_profile)
    val nameLabel get() = tx(R.string.prof_name_label)
    val save get() = tx(R.string.prof_save)
    val kidsLocked get() = tx(R.string.prof_kids_locked)
    val kidsNeedPin get() = tx(R.string.prof_kids_need_pin)
    val colorNames get() = I18n.stringArray(lang, R.array.prof_color_names)
}

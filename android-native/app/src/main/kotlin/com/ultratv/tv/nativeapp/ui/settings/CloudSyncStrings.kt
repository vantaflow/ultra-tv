package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/** Libellés de la synchro cloud multi-appareils (FR · EN · ES · AR), dans un fichier dédié. */
class CloudSyncStrings(private val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)

    val group get() = tx(R.string.cloud_group)
    val syncedWithCloud get() = tx(R.string.cloud_synced_with_cloud)
    val notSynced get() = tx(R.string.cloud_not_synced)
    val syncFailed get() = tx(R.string.cloud_sync_failed)
    fun devicesCount(n: Int) = tx(R.string.cloud_devices_count, n)
    fun ago(minutes: Long): String = when {
        minutes < 1 -> tx(R.string.cloud_ago_1)
        minutes < 60 -> tx(R.string.cloud_ago_2, minutes)
        minutes < 1440 -> tx(R.string.cloud_ago_3, minutes / 60)
        else -> tx(R.string.cloud_ago_else, minutes / 1440)
    }
    val syncNow get() = tx(R.string.cloud_sync_now)
    val thisDevice get() = tx(R.string.cloud_this_device)
    val deviceNameHint get() = tx(R.string.cloud_device_name_hint)
    val rename get() = tx(R.string.cloud_rename)
    val accountDevices get() = tx(R.string.cloud_account_devices)
    val manageOnDashboard get() = tx(R.string.cloud_manage_on_dashboard)

    val localPrivate get() = tx(R.string.cloud_local_private)
    fun cloudShared(n: Int) = tx(R.string.cloud_cloud_shared, n)
    val shareSource get() = tx(R.string.cloud_share_source)
    val editSharing get() = tx(R.string.cloud_edit_sharing)
    val stopSharing get() = tx(R.string.cloud_stop_sharing)
    val shareTitle get() = tx(R.string.cloud_share_title)
    val allDevices get() = tx(R.string.cloud_all_devices)
    val thisOne get() = tx(R.string.cloud_this_one)
    val syncDisplayPrefs get() = tx(R.string.cloud_sync_display_prefs)
    val syncDisplayPrefsHint get() = tx(R.string.cloud_sync_display_prefs_hint)
    val confirm get() = tx(R.string.cloud_confirm)
    val offerTitle get() = tx(R.string.cloud_offer_title)
    val offerBody get() = tx(R.string.cloud_offer_body)
    val notNow get() = tx(R.string.cloud_not_now)
    val pairFirst get() = tx(R.string.cloud_pair_first)

    val connectionWarning get() = tx(R.string.cloud_connection_warning)

    val removalTitle get() = tx(R.string.cloud_removal_title)
    fun removalBody(name: String, n: Int) = tx(R.string.cloud_removal_body, name, n)
    val removeAnyway get() = tx(R.string.cloud_remove_anyway)
    val keepLocal get() = tx(R.string.cloud_keep_local)
}

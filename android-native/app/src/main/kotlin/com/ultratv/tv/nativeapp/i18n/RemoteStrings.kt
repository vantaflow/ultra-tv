package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/**
 * Libellés de « Télécommande sur le téléphone » : recherche et télécommande servies sur le réseau local, sans compte
 * (FR · EN · ES · AR). Fichier à part pour ne pas toucher à [DesignStrings].
 */

val DesignStrings.remoteTitle get() = tx(R.string.remote_remote_title)
val DesignStrings.remoteHint get() = tx(R.string.remote_remote_hint)
val DesignStrings.remoteEyebrow get() = tx(R.string.remote_remote_eyebrow)
val DesignStrings.remoteHeadline get() = tx(R.string.remote_remote_headline)
val DesignStrings.remoteStep1 get() = tx(R.string.remote_remote_step1)
fun DesignStrings.remoteStep2(address: String) = tx(R.string.remote_remote_step2, address).trim()
val DesignStrings.remoteStep3 get() = tx(R.string.remote_remote_step3)
val DesignStrings.remoteOnlyForeground get() = tx(R.string.remote_remote_only_foreground)
val DesignStrings.remoteEnable get() = tx(R.string.remote_remote_enable)
val DesignStrings.remoteDisable get() = tx(R.string.remote_remote_disable)
val DesignStrings.remoteNewKey get() = tx(R.string.remote_remote_new_key)
val DesignStrings.remoteNewKeyHint get() = tx(R.string.remote_remote_new_key_hint)
val DesignStrings.remoteOff get() = tx(R.string.remote_remote_off)
val DesignStrings.remoteNoNetwork get() = tx(R.string.remote_remote_no_network)
val DesignStrings.remoteFailed get() = tx(R.string.remote_remote_failed)
val DesignStrings.remoteScanHint get() = tx(R.string.remote_remote_scan_hint)
val DesignStrings.remoteStatusOn get() = tx(R.string.remote_remote_status_on)

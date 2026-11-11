package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/**
 * Libellés de « Depuis le téléphone » : envoyer une playlist depuis un téléphone ou un ordinateur, sans compte
 * (FR · EN · ES · AR). Fichier à part pour ne pas toucher à [DesignStrings].
 */

val DesignStrings.cardPhone get() = tx(R.string.phone_card_phone)
val DesignStrings.cardPhoneDesc get() = tx(R.string.phone_card_phone_desc)
val DesignStrings.phoneEyebrow get() = tx(R.string.phone_phone_eyebrow)
val DesignStrings.phoneTitle get() = tx(R.string.phone_phone_title)
fun DesignStrings.phoneStep1(address: String) = tx(R.string.phone_phone_step1, address).trim()
val DesignStrings.phoneStep2 get() = tx(R.string.phone_phone_step2)
val DesignStrings.phoneStep3 get() = tx(R.string.phone_phone_step3)
val DesignStrings.phoneScanHint get() = tx(R.string.phone_phone_scan_hint)
val DesignStrings.phoneAddressLabel get() = tx(R.string.phone_phone_address_label)
val DesignStrings.phoneCodeLabel get() = tx(R.string.phone_phone_code_label)
val DesignStrings.phoneRequesting get() = tx(R.string.phone_phone_requesting)
fun DesignStrings.phoneWaiting(remaining: String) = tx(R.string.phone_phone_waiting, remaining)
val DesignStrings.phoneFailed get() = tx(R.string.phone_phone_failed)
val DesignStrings.phoneReceived get() = tx(R.string.phone_phone_received)

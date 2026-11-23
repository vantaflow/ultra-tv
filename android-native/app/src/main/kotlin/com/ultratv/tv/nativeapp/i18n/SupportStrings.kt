package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de « Soutenir Ultra TV » (Réglages › À propos). Fichier à part : [DesignStrings] reste intact. */

/** Don direct sur PayPal (versé tout de suite, sans plateforme intermédiaire), ouvert sur téléphone ; le QR code de la TV y mène aussi. */
const val SUPPORT_URL = "https://paypal.me/kbenazzouz"

val DesignStrings.supportTitle get() = tx(R.string.support_title)
val DesignStrings.supportSub get() = tx(R.string.support_sub)
val DesignStrings.supportScan get() = tx(R.string.support_scan)

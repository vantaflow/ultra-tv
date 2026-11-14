package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de « Soutenir Ultra TV » (Réglages › À propos). Fichier à part : [DesignStrings] reste intact. */

/** Page de dons (Ko-fi : carte ou PayPal, tous pays), ouverte sur téléphone ; le QR code de la TV y mène aussi. */
const val SUPPORT_URL = "https://ko-fi.com/ultratv"

val DesignStrings.supportTitle get() = tx(R.string.support_title)
val DesignStrings.supportSub get() = tx(R.string.support_sub)
val DesignStrings.supportScan get() = tx(R.string.support_scan)

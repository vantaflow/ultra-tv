package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de « Soutenir Ultra TV » (Réglages › À propos). Fichier à part : [DesignStrings] reste intact. */

/** Lien de paiement PayPal Business (montant libre, PayPal ou carte bancaire), ouvert sur téléphone ; le QR code de la TV y mène aussi. */
const val SUPPORT_URL = "https://www.paypal.com/ncp/payment/JHPS8RCN35UWA"

val DesignStrings.supportTitle get() = tx(R.string.support_title)
val DesignStrings.supportSub get() = tx(R.string.support_sub)
val DesignStrings.supportScan get() = tx(R.string.support_scan)

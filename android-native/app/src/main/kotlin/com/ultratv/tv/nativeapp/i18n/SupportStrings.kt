package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de « Soutenir Ultra TV » (Réglages › À propos). Fichier à part : [DesignStrings] reste intact. */

/**
 * Page de dons du site (montant au choix, boutons PayPal / carte bancaire intégrés, lien paypal.me en secours), ouverte sur
 * téléphone ; le QR code de la TV y mène aussi. Une page côté serveur : les moyens de paiement changent sans mise à jour de l'appli.
 */
const val SUPPORT_URL = "https://ultratv.dpdns.org/donate"

val DesignStrings.supportTitle get() = tx(R.string.support_title)
val DesignStrings.supportSub get() = tx(R.string.support_sub)
val DesignStrings.supportScan get() = tx(R.string.support_scan)

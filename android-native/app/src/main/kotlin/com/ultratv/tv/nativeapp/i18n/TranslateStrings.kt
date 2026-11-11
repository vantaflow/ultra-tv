package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés du lien « Aider à traduire » (Réglages › Langue). Fichier à part : [DesignStrings] reste intact. */

/** Adresse de la page d'engagement Weblate, affichée en clair et encodée dans le QR code. */
const val TRANSLATE_URL = "https://hosted.weblate.org/engage/ultra-tv/"

val DesignStrings.translateHelp get() = tx(R.string.translate_help)
val DesignStrings.translateHelpSub get() = tx(R.string.translate_help_sub)
val DesignStrings.translateHelpScan get() = tx(R.string.translate_help_scan)
val DesignStrings.translateHelpClose get() = tx(R.string.translate_help_close)

package com.ultratv.tv.nativeapp.ui.common

/**
 * Nom de catégorie affiché : celui du FOURNISSEUR, tel quel (espaces superflus retirés seulement).
 * Tout nettoyage (préfixe pays, décorations, exposants) finissait par rendre des catégories indiscernables
 * (« FR| SPORT » et « AR| SPORT » → « SPORT »).
 */
fun prettyCategoryName(raw: String): String = raw.trim().replace(Regex("\\s{2,}"), " ").ifEmpty { raw }

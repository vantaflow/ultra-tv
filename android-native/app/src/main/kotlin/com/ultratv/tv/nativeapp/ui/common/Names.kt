package com.ultratv.tv.nativeapp.ui.common

/**
 * Nom de catégorie affiché : celui du FOURNISSEUR, tel quel (espaces superflus retirés seulement).
 * Tout nettoyage (préfixe pays, décorations, exposants) finissait par rendre des catégories indiscernables
 * (« FR| SPORT » et « AR| SPORT » → « SPORT »).
 */
private val MULTI_SPACE = Regex("\\s{2,}")

fun prettyCategoryName(raw: String): String = raw.trim().replace(MULTI_SPACE, " ").ifEmpty { raw }

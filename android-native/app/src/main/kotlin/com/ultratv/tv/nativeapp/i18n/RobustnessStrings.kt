package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés ajoutés par les retours utilisateurs du 22/11 (verrou par catégorie, sélecteur de source). Fichier à part : [DesignStrings] reste intact. */

/** En-tête de la colonne « verrou par code » de Gérer les catégories. */
val DesignStrings.catLockCol get() = tx(R.string.cat_lock_col)
/** Description du cadenas d'une catégorie libre (lecteur d'écran). */
val DesignStrings.catLock get() = tx(R.string.cat_lock)
/** Description du cadenas d'une catégorie verrouillée (lecteur d'écran). */
val DesignStrings.catUnlock get() = tx(R.string.cat_unlock)
/** Verrouiller sans code parental défini ne protège rien : on le dit. */
val DesignStrings.catLockNeedPin get() = tx(R.string.cat_lock_need_pin)

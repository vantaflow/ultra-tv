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
/** Entrée du menu latéral : changer de source (deux sources ou plus). */
val DesignStrings.railSwitchSource get() = tx(R.string.rail_switch_source)
/** Titre de la fenêtre de choix de la source. */
val DesignStrings.sourcePickerTitle get() = tx(R.string.source_picker_title)

// ---- « Modifier » une source (Réglages › Sources) ----
val DesignStrings.sourceEdit get() = tx(R.string.source_edit)
val DesignStrings.sourceEditTitle get() = tx(R.string.source_edit_title)
val DesignStrings.sourceEditSave get() = tx(R.string.source_edit_save)
val DesignStrings.sourceEditReplaceTitle get() = tx(R.string.source_edit_replace_title)
val DesignStrings.sourceEditReplaceBody get() = tx(R.string.source_edit_replace_body)
val DesignStrings.sourceEditReplaceConfirm get() = tx(R.string.source_edit_replace_confirm)
val DesignStrings.sourceEditSaved get() = tx(R.string.source_edit_saved)
val DesignStrings.sourceEditFailed get() = tx(R.string.source_edit_failed)
val DesignStrings.sourceEditExists get() = tx(R.string.source_edit_exists)
val DesignStrings.sourceEditKeepLogin get() = tx(R.string.source_edit_keep_login)
val DesignStrings.sourceEditPickFile get() = tx(R.string.source_edit_pick_file)

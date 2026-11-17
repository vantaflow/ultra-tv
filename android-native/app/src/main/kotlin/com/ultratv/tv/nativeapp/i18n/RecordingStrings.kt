package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Emplacement des enregistrements (mémoire interne, appareil, clé USB, partage NAS) et limite de connexions. */
val DesignStrings.recLocation get() = tx(R.string.rec_location)
val DesignStrings.recLocationHint get() = tx(R.string.rec_location_hint)
val DesignStrings.recLocInternal get() = tx(R.string.rec_loc_internal)
val DesignStrings.recLocDevice get() = tx(R.string.rec_loc_device)
fun DesignStrings.recLocUsb(name: String) = tx(R.string.rec_loc_usb).replace("%1\$s", name)
fun DesignStrings.recLocNas(name: String) = tx(R.string.rec_loc_nas).replace("%1\$s", name)

/** Déjà [maxConnections] enregistrement(s) à ce moment sur la source. */
fun recConflict(D: DesignStrings, maxConnections: Int) = D.tp(R.plurals.rec_conflict, maxConnections, maxConnections)

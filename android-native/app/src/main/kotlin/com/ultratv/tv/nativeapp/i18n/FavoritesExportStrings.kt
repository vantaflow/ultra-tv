package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de l'export M3U des chaînes favorites (écran Favoris). Fichier à part : pas de conflit avec [DesignStrings]. */
val DesignStrings.favExportButton get() = tx(R.string.fav_export_button)
val DesignStrings.favExportTitle get() = tx(R.string.fav_export_title)
val DesignStrings.favExportWarning get() = tx(R.string.fav_export_warning)
val DesignStrings.favExportSave get() = tx(R.string.fav_export_save)
val DesignStrings.favExportShare get() = tx(R.string.fav_export_share)
val DesignStrings.favExportCancel get() = tx(R.string.fav_export_cancel)
val DesignStrings.favExportNone get() = tx(R.string.fav_export_none)
val DesignStrings.favExportFailed get() = tx(R.string.fav_export_failed)
val DesignStrings.favExportShareUnavailable get() = tx(R.string.fav_export_share_unavailable)
fun DesignStrings.favExportDone(n: Int): String = tx(R.string.fav_export_done, channelCount(n))

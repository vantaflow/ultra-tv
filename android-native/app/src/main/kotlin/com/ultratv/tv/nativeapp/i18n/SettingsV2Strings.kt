package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/**
 * Textes de l'écran Réglages v2 : description d'une ligne sous le titre de chaque rubrique et résumé d'état sous chaque
 * rubrique de la liste. Extensions de [DesignStrings] (comme HomeV2Strings) : le porteur lui-même ne grossit pas.
 */

val DesignStrings.setDescDisplay get() = tx(R.string.set2_desc_display)
val DesignStrings.setDescPlayback get() = tx(R.string.set2_desc_playback)
val DesignStrings.setDescParental get() = tx(R.string.set2_desc_parental)
val DesignStrings.setDescLanguages get() = tx(R.string.set2_desc_languages)
val DesignStrings.setDescMessages get() = tx(R.string.set2_desc_messages)
val DesignStrings.setDescAbout get() = tx(R.string.set2_desc_about)
val DesignStrings.setSubPlayback get() = tx(R.string.set2_sub_playback)
val DesignStrings.setSubParental get() = tx(R.string.set2_sub_parental)
val DesignStrings.setSourcesNone get() = tx(R.string.set2_sources_none)

fun DesignStrings.setSourcesCount(n: Int) = tp(R.plurals.set2_sources_count, n, n)

/** « Il y a 12 min » : [duration] est la durée déjà formatée. */
fun DesignStrings.setAgo(duration: String) = tx(R.string.set2_ago, duration)

fun DesignStrings.setSportSummary(state: String) = tx(R.string.set2_sport_summary, state)

fun DesignStrings.setVersionSummary(version: String) = tx(R.string.set2_version_summary, version)

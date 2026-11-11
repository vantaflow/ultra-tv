package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de l'en-tête des pages acteur / réalisateur. Fichier à part, comme les autres chantiers de libellés. */

/** Métier principal d'après le département TMDB ; null si inconnu (rien n'est affiché). */
fun DesignStrings.personRole(department: String?): String? = when (department) {
    "Acting" -> tx(R.string.person_person_role_acting)
    "Directing" -> tx(R.string.person_person_role_directing)
    "Writing" -> tx(R.string.person_person_role_writing)
    "Production" -> tx(R.string.person_person_role_production)
    else -> null
}

fun DesignStrings.personBorn(date: String) = tx(R.string.person_person_born, date)
val DesignStrings.personReadMore get() = tx(R.string.person_person_read_more)
val DesignStrings.personReadLess get() = tx(R.string.person_person_read_less)
fun DesignStrings.personInPlaylist(n: Int) = tx(R.string.person_person_in_playlist, n)
val DesignStrings.personBadgeMovie get() = tx(R.string.person_person_badge_movie)
val DesignStrings.personBadgeSeries get() = tx(R.string.person_person_badge_series)

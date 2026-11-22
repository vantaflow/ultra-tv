package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.repo.EpgRetention

/**
 * Jours proposés par le guide (#248) : du plus ancien jour d'archive des chaînes de la source (plafond 7 jours ; hier
 * si aucune archive, comme avant) jusqu'à après-demain. Puces de jours et touches ⏪ / ⏩ suivent les mêmes bornes.
 * Logique pure, testée à part.
 */
object GuideDays {
    const val LAST_DAY = 2

    /** Premier jour (décalage négatif) d'après la plus longue archive de la source, en jours. */
    fun firstDay(maxArchiveDays: Int): Int = -EpgRetention.archiveDays(maxArchiveDays).coerceAtLeast(1)

    fun days(firstDay: Int): IntRange = firstDay..LAST_DAY

    /** ⏪ / ⏩ : jour précédent / suivant, borné. */
    fun step(day: Int, forward: Boolean, firstDay: Int): Int = (day + if (forward) 1 else -1).coerceIn(firstDay, LAST_DAY)

    /** Jour affiché encore proposé (source changée, archive plus courte) ; sinon retour à aujourd'hui. */
    fun valid(day: Int, firstDay: Int): Int = if (day in days(firstDay)) day else 0
}

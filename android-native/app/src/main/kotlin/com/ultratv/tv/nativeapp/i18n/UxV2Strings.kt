package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de l'interface v2 (accueil, guide, sport) : fichier à part, [DesignStrings] ne grossit pas. */
val DesignStrings.greetMorning get() = tx(R.string.uxv2_greet_morning)
val DesignStrings.greetAfternoon get() = tx(R.string.uxv2_greet_afternoon)
val DesignStrings.greetEvening get() = tx(R.string.uxv2_greet_evening)
val DesignStrings.featuredBadge get() = tx(R.string.uxv2_featured)
val DesignStrings.categoryLabel get() = tx(R.string.uxv2_category)
val DesignStrings.reminderOn get() = tx(R.string.uxv2_reminder_on)
val DesignStrings.sportFromGuide get() = tx(R.string.uxv2_sport_from_guide)
val DesignStrings.sportKindFootball get() = tx(R.string.uxv2_sport_football)
val DesignStrings.sportKindBasket get() = tx(R.string.uxv2_sport_basket)
val DesignStrings.sportKindTennis get() = tx(R.string.uxv2_sport_tennis)
val DesignStrings.sportKindCombat get() = tx(R.string.uxv2_sport_combat)
val DesignStrings.sportKindMotor get() = tx(R.string.uxv2_sport_motor)
val DesignStrings.sportKindRugby get() = tx(R.string.uxv2_sport_rugby)

fun DesignStrings.sportLiveCount(count: String) = tx(R.string.uxv2_sport_live_count).format(count)

fun DesignStrings.sourcesCount(n: Int) = tp(R.plurals.uxv2_sources, n, n.toString())

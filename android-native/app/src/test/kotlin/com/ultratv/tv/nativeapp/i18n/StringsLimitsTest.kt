package com.ultratv.tv.nativeapp.i18n

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D8 produit un DEX invalide (« invalid arg count (0) in range invoke », crash au
 * premier accès à StringsKt) quand un constructeur dépasse 254 paramètres.
 * On garde une marge pour ne pas retomber dans le piège au prochain libellé.
 */
class StringsLimitsTest {
    @Test fun strings_constructeur_resteSousLaLimiteDex() {
        val max = Strings::class.java.constructors.maxOf { it.parameterCount }
        assertTrue("Strings a $max paramètres (limite DEX 254, marge visée 250)", max <= 250)
    }
}

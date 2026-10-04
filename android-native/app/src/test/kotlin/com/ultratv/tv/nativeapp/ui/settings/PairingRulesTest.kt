package com.ultratv.tv.nativeapp.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingRulesTest {
    @Test fun groupPairingCode_huitCaracteres_insereUnTiretAuMilieu() = assertEquals("K7Q2-M9XF", groupPairingCode("K7Q2M9XF"))
    @Test fun groupPairingCode_dejaGroupe_resteInchange() = assertEquals("K7Q2-M9XF", groupPairingCode("K7Q2-M9XF"))
    @Test fun groupPairingCode_codeCourt_resteInchange() = assertEquals("AB12", groupPairingCode("AB12"))
    @Test fun formatRemaining_neuvMinutesQuaranteDeux() = assertEquals("9:42", formatRemaining(9 * 60_000L + 42_000L))
    @Test fun formatRemaining_negatif_estBorneAZero() = assertEquals("0:00", formatRemaining(-5_000L))
}

package com.ultratv.tv.nativeapp.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRulesTest {
    @Test fun premierLancementSansPlaylist_affiche() =
        assertTrue(shouldShowOnboarding(hasSeenOnboarding = false, providerCount = 0))

    @Test fun dejaVu_masque() =
        assertFalse(shouldShowOnboarding(hasSeenOnboarding = true, providerCount = 0))

    @Test fun playlistDejaPresente_masque() =
        assertFalse(shouldShowOnboarding(hasSeenOnboarding = false, providerCount = 1))
}

class SourceOptionsTest {
    @Test fun tv_proposeStalkerEtTelephone() {
        val o = sourceOptions(touch = false)
        assertTrue(SourceOption.STALKER in o)
        assertTrue(SourceOption.PHONE in o)
        assertTrue(o.size == o.toSet().size)
    }

    @Test fun tactile_proposeStalker_maisPasTelephone() {
        val o = sourceOptions(touch = true)
        assertTrue(SourceOption.STALKER in o)
        assertFalse(SourceOption.PHONE in o)
    }

    @Test fun stalkerSuitXtream() =
        assertTrue(sourceOptions(false).take(2) == listOf(SourceOption.XTREAM, SourceOption.STALKER))
}

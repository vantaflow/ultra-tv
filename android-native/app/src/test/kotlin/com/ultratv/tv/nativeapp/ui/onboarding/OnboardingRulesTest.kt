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

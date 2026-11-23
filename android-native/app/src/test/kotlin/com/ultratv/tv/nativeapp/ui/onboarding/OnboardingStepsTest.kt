package com.ultratv.tv.nativeapp.ui.onboarding

import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.UiSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Étapes de l'assistant : « Apparence » s'insère entre Bienvenue et Source (4 étapes numérotées). Pur. */
class OnboardingStepsTest {
    @Test fun quatreEtapesNumerotees_apparenceEnDeuxieme() {
        assertEquals(4, WIZARD_STEP_COUNT)
        assertEquals(1, stepNumber(Step.Welcome))
        assertEquals(2, stepNumber(Step.Appearance))
        assertEquals(3, stepNumber(Step.Source))
        assertEquals(3, stepNumber(Step.Languages))   // choix des langues = suite de l'étape Source
        assertEquals(4, stepNumber(Step.Ready))
    }

    @Test fun retour_remonteDUneEtape() {
        assertNull(previousStep(Step.Welcome))
        assertEquals(Step.Welcome, previousStep(Step.Appearance))
        assertEquals(Step.Appearance, previousStep(Step.Source))
        assertEquals(Step.Source, previousStep(Step.Languages))
        assertEquals(Step.Source, previousStep(Step.Ready))
    }

    @Test fun passerLApparence_restaureSeulementCeQuiAChange() {
        val before = WizardAppearance(AppTheme.DARK, AccentChoice.RED, UiSize.NORMAL)
        assertEquals(AppearanceRestore(null, null, null), appearanceRestore(before, before))
        val after = WizardAppearance(AppTheme.SAND, AccentChoice.RED, UiSize.LARGE)
        val r = appearanceRestore(before, after)
        assertEquals(AppTheme.DARK, r.theme)
        assertNull(r.accent)
        assertEquals(UiSize.NORMAL, r.uiSize)
    }

    @Test fun utilisateurExistant_assistantNeSeRejouePas() {
        // Inchangé : l'étape Apparence ne fait pas revenir l'assistant pour qui l'a déjà vu.
        assertFalse(shouldShowOnboarding(hasSeenOnboarding = true, providerCount = 0))
        assertFalse(shouldShowOnboarding(hasSeenOnboarding = true, providerCount = 3, started = false))
        assertTrue(shouldShowOnboarding(hasSeenOnboarding = false, providerCount = 0))
    }
}

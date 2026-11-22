package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Réglages avancés : masqués pour une installation neuve, affichés pour un existant, toujours visibles s'ils sont modifiés. */
class AdvancedSettingsTest {
    @Test fun `installation neuve - masques, utilisateur existant - affiches`() {
        assertFalse(AdvancedSettings.initialShow(hasSeenOnboarding = false))
        assertTrue(AdvancedSettings.initialShow(hasSeenOnboarding = true))
        assertFalse(UserPrefs().showAdvanced)
        AdvancedRow.entries.forEach { assertFalse(it.name, AdvancedSettings.visible(UserPrefs(), it)) }
    }

    @Test fun `interrupteur - tous visibles`() {
        val p = UserPrefs(showAdvanced = true)
        AdvancedRow.entries.forEach { assertTrue(it.name, AdvancedSettings.visible(p, it)) }
    }

    @Test fun `valeur differente du defaut - toujours visible, les autres restent masques`() {
        val p = UserPrefs(showAdvanced = false, playerEngine = "vlc", tunneledPlayback = true, autoFrameRate = false)
        assertTrue(AdvancedSettings.visible(p, AdvancedRow.ENGINE))
        assertTrue(AdvancedSettings.visible(p, AdvancedRow.TUNNELED))
        assertTrue(AdvancedSettings.visible(p, AdvancedRow.AUTO_FPS))
        assertFalse(AdvancedSettings.visible(p, AdvancedRow.DECODER))
        assertFalse(AdvancedSettings.visible(p, AdvancedRow.BUFFER))
    }
}

package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Niveau global des réglages (Simple / Expert) : Simple pour une installation neuve, Expert pour un existant, reprise de
 * l'ancien interrupteur « Afficher les réglages avancés », réglage modifié toujours visible.
 */
class AdvancedSettingsTest {
    private val simple = SettingsLevel.SIMPLE.key
    private val expert = SettingsLevel.EXPERT.key

    @Test fun `niveau de depart - neuf Simple, existant Expert, S1 repris, valeur enregistree prioritaire`() {
        assertEquals(SettingsLevel.SIMPLE, SettingsLevels.resolve(null, null, hasSeenOnboarding = false))
        assertEquals(SettingsLevel.EXPERT, SettingsLevels.resolve(null, null, hasSeenOnboarding = true))
        // Ancien interrupteur S1 enregistré : « afficher » → Expert, même sur une installation récente.
        assertEquals(SettingsLevel.EXPERT, SettingsLevels.resolve(null, true, hasSeenOnboarding = false))
        assertEquals(SettingsLevel.SIMPLE, SettingsLevels.resolve(null, false, hasSeenOnboarding = true))
        // Choix enregistré : toujours prioritaire.
        assertEquals(SettingsLevel.SIMPLE, SettingsLevels.resolve("simple", true, hasSeenOnboarding = true))
        assertEquals(SettingsLevel.EXPERT, SettingsLevels.resolve("expert", false, hasSeenOnboarding = false))
        // Valeur inconnue (autre version) : règle de départ.
        assertEquals(SettingsLevel.EXPERT, SettingsLevels.resolve("???", null, hasSeenOnboarding = true))
        assertEquals("simple", UserPrefs().settingsLevel)
    }

    @Test fun `Simple masque tous les reglages techniques par defaut`() {
        AdvancedRow.entries.forEach { assertFalse(it.name, AdvancedSettings.visible(UserPrefs(settingsLevel = simple), it)) }
    }

    @Test fun `Expert montre tout`() {
        AdvancedRow.entries.forEach { assertTrue(it.name, AdvancedSettings.visible(UserPrefs(settingsLevel = expert), it)) }
    }

    @Test fun `valeur differente du defaut - toujours visible en Simple, les autres restent masques`() {
        val p = UserPrefs(
            settingsLevel = simple, playerEngine = "vlc", tunneledPlayback = true, autoFrameRate = false,
            syncVod = false, timeZone = "GMT", includeUnknownLang = false, workerBaseUrl = "https://mon-worker.example",
            extraEpg = "https://guide.example/epg.xml", localLogosFolderUri = "content://logos",
        )
        val shown = setOf(
            AdvancedRow.ENGINE, AdvancedRow.TUNNELED, AdvancedRow.AUTO_FPS, AdvancedRow.SYNC_VOD, AdvancedRow.TIME_ZONE,
            AdvancedRow.INCLUDE_UNKNOWN, AdvancedRow.WORKER_URL, AdvancedRow.EXTRA_GUIDE, AdvancedRow.LOCAL_LOGOS,
        )
        AdvancedRow.entries.forEach { assertEquals(it.name, it in shown, AdvancedSettings.visible(p, it)) }
    }

    @Test fun `regle generale et rubriques entierement techniques`() {
        assertTrue(SettingsLevels.show(SettingsLevel.EXPERT))
        assertFalse(SettingsLevels.show(SettingsLevel.SIMPLE))
        assertTrue(SettingsLevels.show(SettingsLevel.SIMPLE, customized = true))
        // Rubrique avec au moins un réglage simple : toujours là.
        assertTrue(SettingsLevels.rubVisible(SettingsLevel.SIMPLE, technicalOnly = false, anyCustomized = false))
        // Rubrique entièrement technique : masquée en Simple, sauf réglage modifié ; visible en Expert.
        assertFalse(SettingsLevels.rubVisible(SettingsLevel.SIMPLE, technicalOnly = true, anyCustomized = false))
        assertTrue(SettingsLevels.rubVisible(SettingsLevel.SIMPLE, technicalOnly = true, anyCustomized = true))
        assertTrue(SettingsLevels.rubVisible(SettingsLevel.EXPERT, technicalOnly = true, anyCustomized = false))
    }
}

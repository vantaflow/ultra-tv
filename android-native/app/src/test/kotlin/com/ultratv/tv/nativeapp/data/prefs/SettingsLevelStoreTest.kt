package com.ultratv.tv.nativeapp.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ultratv.tv.nativeapp.ui.settings.SettingsLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Niveau des réglages lu dans les VRAIES clés de l'appareil (DataStore « user_prefs ») : neuf → Simple, existant (assistant
 * déjà passé) → Expert, ancien interrupteur show_advanced repris, choix enregistré prioritaire.
 */
class SettingsLevelStoreTest {
    private val level = stringPreferencesKey("settings_level")
    private val legacy = booleanPreferencesKey("show_advanced")
    private val seen = booleanPreferencesKey("has_seen_onboarding")

    @Test fun `installation neuve - Simple`() =
        assertEquals(SettingsLevel.SIMPLE, UserPreferencesStore.settingsLevelFrom(mutablePreferencesOf()))

    @Test fun `utilisateur existant - Expert`() =
        assertEquals(SettingsLevel.EXPERT, UserPreferencesStore.settingsLevelFrom(mutablePreferencesOf(seen to true)))

    @Test fun `ancien interrupteur afficher enregistre - Expert, masquer - Simple`() {
        assertEquals(SettingsLevel.EXPERT, UserPreferencesStore.settingsLevelFrom(mutablePreferencesOf(legacy to true)))
        assertEquals(SettingsLevel.SIMPLE, UserPreferencesStore.settingsLevelFrom(mutablePreferencesOf(legacy to false, seen to true)))
    }

    @Test fun `choix enregistre prioritaire`() {
        assertEquals(SettingsLevel.SIMPLE, UserPreferencesStore.settingsLevelFrom(mutablePreferencesOf(level to "simple", legacy to true, seen to true)))
        assertEquals(SettingsLevel.EXPERT, UserPreferencesStore.settingsLevelFrom(mutablePreferencesOf(level to "expert")))
    }
}

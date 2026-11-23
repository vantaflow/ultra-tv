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

    private val chosen = booleanPreferencesKey("settings_level_chosen")
    private val migrated = booleanPreferencesKey("settings_level_migrated")

    /** Box mise à jour depuis 1.3.72 (rc8 → rc9 → 1.3.73) : sources présentes, assistant jamais proposé donc jamais « passé ». */
    private fun boxFrom1372() = mutablePreferencesOf(legacy to false, level to "simple")

    @Test fun `box existante mise a jour par rc8 rc9 - valeurs ecrites automatiquement ignorees, Expert`() {
        val p = boxFrom1372()
        UserPreferencesStore.fixSettingsLevel(p, hasProviders = true)
        assertEquals("expert", p[level])
        assertEquals(true, p[migrated])
        assertEquals(SettingsLevel.EXPERT, UserPreferencesStore.settingsLevelFrom(p))
    }

    @Test fun `sources presentes sans assistant ni ancienne valeur - Expert`() {
        val p = mutablePreferencesOf()
        UserPreferencesStore.fixSettingsLevel(p, hasProviders = true)
        assertEquals("expert", p[level])
    }

    @Test fun `installation neuve - Simple fige puis garde, meme apres ajout de sources`() {
        val p = mutablePreferencesOf()
        UserPreferencesStore.fixSettingsLevel(p, hasProviders = false)
        assertEquals("simple", p[level])
        p[seen] = true
        UserPreferencesStore.fixSettingsLevel(p, hasProviders = true)
        assertEquals("simple", p[level])
        assertEquals(SettingsLevel.SIMPLE, UserPreferencesStore.settingsLevelFrom(p, hasProviders = true))
    }

    @Test fun `niveau choisi dans le selecteur jamais touche`() {
        val p = boxFrom1372().apply { this[chosen] = true }
        UserPreferencesStore.fixSettingsLevel(p, hasProviders = true)
        assertEquals("simple", p[level])
        assertEquals(SettingsLevel.SIMPLE, UserPreferencesStore.settingsLevelFrom(p, hasProviders = true))
    }

    @Test fun `valeur differente de l ecriture automatique - choix garde`() {
        // Assistant passé : rc9 aurait écrit « expert » ; « simple » a donc été choisi.
        val p = mutablePreferencesOf(seen to true, level to "simple")
        UserPreferencesStore.fixSettingsLevel(p, hasProviders = true)
        assertEquals("simple", p[level])
        // S1 « masquer » alors que rc8 aurait écrit « afficher » (assistant passé) : choix à la main.
        val q = mutablePreferencesOf(seen to true, legacy to false)
        UserPreferencesStore.fixSettingsLevel(q, hasProviders = true)
        assertEquals("simple", q[level])
    }
}

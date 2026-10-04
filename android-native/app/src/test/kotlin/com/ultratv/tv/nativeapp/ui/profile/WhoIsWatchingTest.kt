package com.ultratv.tv.nativeapp.ui.profile

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-xhdpi")
class WhoIsWatchingTest {
    @get:Rule val rule = createComposeRule()

    private val profiles = listOf(
        ProfileEntity(1, "Karim", 0xFFD91E2B.toInt(), "K"),
        ProfileEntity(2, "Sara", 0xFF0EA5E9.toInt(), "S"),
        ProfileEntity(3, "Enfants", 0xFFF59E0B.toInt(), "E", isKids = true, pinHash = "x"),
    )

    @Test fun focusInitial_surLeDernierProfil_etAjouterPresent() {
        rule.setContent { WhoIsWatchingContent(profiles, focusId = 2, onPick = {}, onAdd = {}) }
        rule.onNodeWithText("Who's watching?").assertExists()
        rule.onNodeWithTag("profile-2").assertIsFocused()
        rule.onNodeWithTag("profile-add").assertExists()
        rule.onNodeWithText("PIN protected").assertExists()
    }

    @Test fun clic_surUnProfil_appelleOnPick() {
        var picked: Long? = null
        rule.setContent { WhoIsWatchingContent(profiles, focusId = 1, onPick = { picked = it.id }, onAdd = {}) }
        rule.onNodeWithTag("profile-3").performClick()
        assertEquals(3L, picked)
    }

    @Test fun ajouterAbsent_quandOnAddEstNul() {
        rule.setContent { WhoIsWatchingContent(profiles, focusId = 1, onPick = {}, onAdd = null) }
        rule.onNodeWithTag("profile-add").assertDoesNotExist()
    }
}

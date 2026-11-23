package com.ultratv.tv.nativeapp.ui.profile

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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

    /** Box rc9 : profil courant connu un instant après la liste (focus d'abord posé sur le premier profil). */
    @Test fun profilCourantConnuApres_focusSurLeDernierUtilise() {
        val id = androidx.compose.runtime.mutableStateOf<Long?>(null)
        rule.setContent { WhoIsWatchingContent(profiles, focusId = id.value, onPick = {}, onAdd = {}) }
        rule.onNodeWithTag("profile-1").assertIsFocused()
        rule.runOnIdle { id.value = 2 }
        rule.onNodeWithTag("profile-2").assertIsFocused()
    }

    /** Focus déplacé sans action de l'utilisateur (focus par défaut du système) : ramené sur le dernier utilisé. */
    @Test fun focusDeplaceSansTouche_rameneSurLeDernierUtilise() {
        lateinit var fm: androidx.compose.ui.focus.FocusManager
        rule.setContent {
            fm = androidx.compose.ui.platform.LocalFocusManager.current
            WhoIsWatchingContent(profiles, focusId = 2, onPick = {}, onAdd = {})
        }
        rule.onNodeWithTag("profile-2").assertIsFocused()
        rule.runOnIdle { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Left) }
        rule.onNodeWithTag("profile-2").assertIsFocused()
    }

    /** Après une flèche, le focus appartient à l'utilisateur : aucun vol. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun apresUneFleche_pasDeVolDeFocus() {
        rule.setContent { WhoIsWatchingContent(profiles, focusId = 2, onPick = {}, onAdd = {}) }
        rule.onNodeWithTag("profile-2").assertIsFocused()
        rule.onNodeWithTag("profile-2").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionLeft) }
        rule.onNodeWithTag("profile-1").assertIsFocused()
    }

    @Test fun regleDeRefocus() {
        assertEquals(true, ProfileFocus.shouldRefocus(2, 1, userMoved = false))
        assertEquals(true, ProfileFocus.shouldRefocus(2, null, userMoved = false))
        assertEquals(false, ProfileFocus.shouldRefocus(2, 2, userMoved = false))
        assertEquals(false, ProfileFocus.shouldRefocus(2, 1, userMoved = true))
        assertEquals(false, ProfileFocus.shouldRefocus(null, 1, userMoved = false))
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

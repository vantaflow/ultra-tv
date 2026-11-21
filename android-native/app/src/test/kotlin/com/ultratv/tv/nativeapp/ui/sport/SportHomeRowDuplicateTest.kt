package com.ultratv.tv.nativeapp.ui.sport

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rangée « Sport en direct » : deux matchs sur la même chaîne (deux programmes qui se chevauchent dans l'EPG) faisaient planter l'accueil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SportHomeRowDuplicateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `meme chaine pour deux matchs - aucune exception, deux cartes`() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val ch = ChannelEntity(id = 6, providerId = 1, remoteId = "m6", name = "M6", logo = null, categoryId = "sport", streamUrl = "u", num = 6)
        val live = listOf(
            SportEntry(ch, EpgEntity(id = 1, channelId = 6, title = "Match A", description = null, startMs = 0L, endMs = 7_200_000L), sportChannel = true, strict = true),
            SportEntry(ch, EpgEntity(id = 2, channelId = 6, title = "Match B", description = null, startMs = 0L, endMs = 7_200_000L), sportChannel = true, strict = true),
        )
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French)) {
                SportHomeRow(live, mutableLongStateOf(3_600_000L), Modifier, PaddingValues(), {}, {})
            }
        }
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("Match A", substring = true).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Match B", substring = true).fetchSemanticsNodes().size)
    }
}

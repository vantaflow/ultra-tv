package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.DirectCategory
import com.ultratv.tv.nativeapp.ui.live.LivePreviewStrings
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Réglage d'aperçu du Guide : présent avec les libellés du réglage du Direct, absent sans aperçu possible, et libellés manuscrits en en / fr / es / ar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GuidePreviewSettingTest {
    @get:Rule val rule = createComposeRule()

    private val chan = ChannelEntity(id = 1, providerId = 1, remoteId = "1", name = "Chaîne", logo = null, categoryId = "c", streamUrl = "u", num = 1)
    private var clicks = 0

    private fun mount(label: String?, withLabel: String? = null) {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French), LocalTouch provides false) {
                val clock = remember { mutableLongStateOf(0L) }
                val shownLabel = label ?: withLabel?.let { LivePreviewStrings(AppLang.French).guideChip + " " + it }
                Box(Modifier.fillMaxSize()) {
                    GuideTvContent(
                        channelCount = 1, channelAt = { chan }, peekAt = { chan }, itemKey = { chan.id },
                        progsFor = { emptyList() }, windowStart = 0L, day = 0, D = LocalDs.current, clock = clock,
                        groups = listOf(DirectCategory(CATEGORY_ALL, null, 1)), group = CATEGORY_ALL, guideLoad = GuideLoad.Idle, nameColPx = 260,
                        groupName = { "Toutes" }, onDay = {}, onWindowStart = {}, onGroup = {}, onVisible = {}, onRetry = {},
                        onSelect = { _, _ -> }, onWatch = {}, onRemind = { _, _ -> }, onRecord = { _, _ -> },
                        preview = { _, m -> Box(m.testTag("preview")) },
                        previewLabel = shownLabel, onPreviewSetting = { clicks++ },
                    )
                }
            }
        }
    }

    @Test fun reglage_present_avecLesLibellesDuDirect_etOuvreLeChoix() {
        val L = LivePreviewStrings(AppLang.French)
        mount(null, withLabel = L.on)
        rule.onNodeWithTag("guide-preview-setting").performClick()
        assertEquals(1, clicks)
    }

    @Test fun reglage_absentSansApercuPossible() {
        mount(null)
        assertTrue(rule.onAllNodes(hasTestTag("guide-preview-setting")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun libelleCourt_ecritDansLesQuatreLangues() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val labels = mutableListOf<String>()
        run {
            for (l in listOf(AppLang.English, AppLang.French, AppLang.Spanish, AppLang.Arabic)) labels += LivePreviewStrings(l).guideChip
        }
        assertEquals(4, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
    }
}

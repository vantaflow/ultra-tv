package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Guide tactile : une case de programme (64 dp) garde son horaire entier même si le titre est long. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TouchGuideCellTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() { Ux.textScale = 1f }

    private fun check(fontScale: Float) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val title = "Rugby : Championnat des nations | Angleterre / Nouvelle-Zélande"
        val start = System.currentTimeMillis() / 1_800_000L * 1_800_000L
        val chan = ChannelEntity(id = 1, providerId = 1, remoteId = "1", name = "Sport 1", logo = null, categoryId = "c", streamUrl = "u", num = 1)
        val p = EpgEntity(id = 5, channelId = 1, title = title, description = null, startMs = start, endMs = start + 90 * 60_000L)
        rule.setContent {
            CompositionLocalProvider(LocalTouch provides true, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                TouchGuideRow(chan, listOf(p), start, 0, 100.dp, onSelect = {}, onRemind = {})
            }
        }
        rule.waitForIdle()
        val t = rule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNode(SemanticsMatcher("horaire") { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.matches(Regex(".*\\d:\\d\\d.* – .*")) } == true }, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val dens = rule.density.density
        val cellBottom = t.top - 8 * dens + 64 * dens // case : 64 dp, marge intérieure haute de 8 dp
        val lh = 11f * fontScale * dens
        assertTrue("horaire rogné : hauteur ${time.height}px < ${lh}px", time.height >= lh)
        assertTrue("horaire sous la case : bas ${time.bottom} > ${cellBottom - 8 * dens}", time.bottom <= cellBottom + 0.5f)
    }

    @Test fun titreLong_horaireEntier() = check(1f)
    @Test fun titreLong_horaireEntier_tresGrand() = check(1.3f)

    @Test fun ligneSansProgrammeCeJourLa_annonceeEtEstompee() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val chan = ChannelEntity(id = 1, providerId = 1, remoteId = "1", name = "Sport 1", logo = null, categoryId = "c", streamUrl = "u", num = 1)
        rule.setContent {
            CompositionLocalProvider(LocalTouch provides true, com.ultratv.tv.nativeapp.i18n.LocalDs provides com.ultratv.tv.nativeapp.i18n.designStringsFor(com.ultratv.tv.nativeapp.i18n.AppLang.French)) {
                TouchNoProgrammeRow(chan, 0, 100.dp, com.ultratv.tv.nativeapp.i18n.LocalDs.current)
            }
        }
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Sport 1, Pas de programme ce jour-là")).assertExists()
    }
}

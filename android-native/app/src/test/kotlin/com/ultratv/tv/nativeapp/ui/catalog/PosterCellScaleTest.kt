package com.ultratv.tv.nativeapp.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.home.installFakeImageLoader
import com.ultratv.tv.nativeapp.ui.home.resetImageLoader
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Le nouveau paramètre `focusedScale` de [PosterCell] ne change pas la taille de la cellule (le catalogue garde son aspect). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PosterCellScaleTest {
    @get:Rule val rule = createComposeRule()

    @Before fun fakeImages() { installFakeImageLoader() }
    @After fun reset() { resetImageLoader() }

    private val item = PosterItem(id = 1, title = "Orbite basse", poster = null, year = 2024, rating = 7.8)

    @Test fun focusedScale_nul_ouRenseigne_memeTailleDeCellule() {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(LocalTouch provides false) {
                Box { PosterCell(item, Modifier.width(200.design).testTag("defaut"), imageWidth = 200.design) {} }
                Box { PosterCell(item, Modifier.width(200.design).testTag("accueil"), imageWidth = 200.design, focusedScale = 1.10f) {} }
            }
        }
        rule.waitForIdle()
        val a = rule.onNodeWithTag("defaut").getUnclippedBoundsInRoot()
        val b = rule.onNodeWithTag("accueil").getUnclippedBoundsInRoot()
        assertEquals(a.width.value, b.width.value, 0.01f)
        assertEquals(a.height.value, b.height.value, 0.01f)
        assertEquals(200.design.value, a.width.value, 0.5f)
    }
}

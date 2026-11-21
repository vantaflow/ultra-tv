package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Boutons du dialogue d'actions d'un programme : hauteur fixe de 92 unités, libellé et indication toujours entiers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ProgramActionButtonTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() { Ux.textScale = 1f }

    private val label = "Regarder la chaîne en direct maintenant et tout de suite"
    private val hint = "Ouvre le direct de la chaîne dans le lecteur plein écran"

    private fun check(scale: Float) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.textScale = scale
        rule.setContent {
            CompositionLocalProvider(LocalTouch provides false, LocalDensity provides Density(2f, scale)) {
                Box(Modifier.width(461.design)) { ActionButton(label, hint, com.ultratv.tv.nativeapp.ui.design.Icons.Live, true, Modifier, {}) }
            }
        }
        rule.waitForIdle()
        // Nœud fusionné (cliquable) : la zone des 92 unités ; puis les deux textes non fusionnés.
        val btn = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        assertEquals(92f, btn.height, 0.5f)
        val l = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val h = rule.onNodeWithText(hint, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("libellé sur plus d'une ligne : ${l.height}", l.height < 23f * 1.6f * scale)
        assertTrue("indication rognée : bas ${h.bottom} > bouton ${btn.bottom}", h.bottom <= btn.bottom + 0.5f)
        assertTrue("indication de hauteur réduite : ${h.height}", h.height >= 22f * 1.2f * scale)
    }

    @Test fun libelleLong_tailleNormale() = check(1f)
    @Test fun libelleLong_tresGrand() = check(1.3f)
}

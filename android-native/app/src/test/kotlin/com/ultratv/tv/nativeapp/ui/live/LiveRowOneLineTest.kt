package com.ultratv.tv.nativeapp.ui.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.ui.design.TextSize
import com.ultratv.tv.nativeapp.ui.design.Ux
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Liste du Direct (box, capture 22/11) : le titre long du programme en cours (« S.O.S. fantômes : la menace de glace »,
 * sans programme suivant) passait sur deux lignes très espacées, lignes hautes et irrégulières. Une seule ligne avec
 * points de suspension, hauteur de carte constante, à toutes les tailles de texte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class LiveRowOneLineTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @After fun reset() { Ux.textScale = 1f }

    @Test fun sautsDeLigneUnicode_rameneesAUnEspace() {
        assertEquals("S.O.S. fantômes : la menace de glace", oneLine("S.O.S. fantômes : la menace de\nglace "))
    }

    @Test fun titreLongSansSuivant_uneLigne_hauteurConstante_texteNormal() = check(TextSize.NORMAL.scale)
    @Test fun titreLongSansSuivant_uneLigne_hauteurConstante_texteTresGrand() = check(TextSize.XLARGE.scale)

    private fun check(scale: Float) {
        run {
            Ux.textScale = scale
            val now = System.currentTimeMillis()
            val ch = ChannelEntity(id = 1, providerId = 1, remoteId = "1", name = "FR| TF1 HD", logo = null, categoryId = "c", streamUrl = "u", num = 1)
            val long = EpgEntity(channelId = 1, title = "S.O.S. fantômes : la menace de glace (version longue, rediffusion du soir)", description = null, startMs = now - 3_600_000, endMs = now + 3_600_000)
            val next = EpgEntity(channelId = 1, title = "Programmes de nuit", description = null, startMs = now + 3_600_000, endMs = now + 7_200_000)
            val heights = IntArray(2)
            rule.setContent {
                val clock = remember { mutableLongStateOf(now) }
                Column(Modifier.width(320.dp)) {
                    ChannelRow(ch, 1, false, false, long, null, clock, Modifier.onGloballyPositioned { heights[0] = it.size.height }, {}, {}, {})
                    ChannelRow(ch, 2, false, false, long, next, clock, Modifier.onGloballyPositioned { heights[1] = it.size.height }, {}, {}, {})
                }
            }
            rule.waitForIdle()
            val nodes = rule.onAllNodesWithTag("live-now-title", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(2, nodes.size)
            for (n in nodes) {
                val res = mutableListOf<TextLayoutResult>()
                n.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(res)
                assertEquals("titre sur une ligne (texte ×$scale)", 1, res.first().lineCount)
            }
            assertTrue(heights[0] > 0)
            assertEquals("hauteur de ligne constante (texte ×$scale)", heights[1], heights[0])
        }
    }
}

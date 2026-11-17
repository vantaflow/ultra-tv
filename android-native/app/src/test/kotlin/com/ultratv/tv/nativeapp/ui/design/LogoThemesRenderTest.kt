package com.ultratv.tv.nativeapp.ui.design

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.ui.theme.isLightTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Le logo reste détouré (liseré ≥ 3:1 sur le fond) dans TOUS les thèmes ; une capture par thème sous `build/outputs/ui-v2/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h270dp-xhdpi")
class LogoThemesRenderTest {
    @get:Rule val rule = createComposeRule()
    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    @After fun reset() { Ux.themeLight = false; Ux.themeOled = false; Ux.themeKind = AppTheme.DARK }

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }
    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test fun logo_visibleSurTousLesThemes() {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                Box(Modifier.padding(24.dp).testTag("logo")) { LogoMark(160) }
            }
        }
        for (t in AppTheme.entries) {
            Ux.themeKind = t
            Ux.themeLight = isLightTheme(t, systemDark = true, isTv = true)
            Ux.themeOled = t == AppTheme.OLED
            rule.waitForIdle()
            File(outDir, "logo-${t.name.lowercase()}.png").outputStream().use {
                rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val bmp = rule.onNodeWithTag("logo").captureToImage().asAndroidBitmap()
            val bg = bmp.getPixel(0, 0) // coin hors de la tuile arrondie : le fond du thème
            val edge = bmp.getPixel(2, bmp.height / 2) // milieu du bord gauche : le liseré
            val r = ratio(bg, edge)
            assertTrue("logo invisible sur le thème $t (contraste du liseré $r)", r >= 3.0)
        }
    }
}

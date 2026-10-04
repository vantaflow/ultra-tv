package com.ultratv.tv.nativeapp.ui.design

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RÈGLE : une image ne dicte JAMAIS la taille de son emplacement. Quelles que soient les dimensions de l'image reçue
 * (bandeau 4000×10, colonne 10×4000, énorme 8000×8000, minuscule 1×1), l'emplacement garde EXACTEMENT sa taille imposée.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-xhdpi")
class SlotSizeTest {
    @get:Rule val rule = createComposeRule()

    private val extremes = listOf(4000 to 10, 10 to 4000, 3000 to 3000, 1 to 1)

    private fun install() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Coil renvoie une image de la taille codée dans l'URL (« …/4000x10 »), sans réseau.
        val loader = ImageLoader.Builder(ctx).components {
            add(Interceptor { chain ->
                val (w, h) = chain.request.data.toString().substringAfterLast('/').split('x').map { it.toInt() }
                val bmp = Bitmap.createBitmap(w.coerceAtMost(512), h.coerceAtMost(512), Bitmap.Config.ARGB_8888)
                SuccessResult(drawable = BitmapDrawable(ctx.resources, bmp), request = chain.request, dataSource = coil.decode.DataSource.MEMORY) as ImageResult
            })
        }.build()
        Coil.setImageLoader(loader)
    }

    @Before fun reset() { Coil.reset() }
    @After fun cleanup() { Coil.reset() }

    @Test fun lesEmplacementsGardentExactementLeurTaille_quelleQueSoitL_image() {
        install()
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((w, h) in extremes) {
                    val u = "https://exemple.invalid/${w}x$h"
                    PosterImage(u, "Titre", Modifier.testTag("poster-${w}x$h").width(200.dp).aspectRatio(2f / 3f))
                    ThumbImage(u, "Titre", Modifier.testTag("thumb-${w}x$h").width(320.dp).aspectRatio(16f / 9f))
                    LogoBox(u, "Chaîne", Modifier.testTag("logo-${w}x$h").width(72.dp).height(48.dp))
                    AvatarImage(u, "Nom", Modifier.testTag("avatar-${w}x$h").width(96.dp).height(96.dp))
                }
            }
        }
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        for ((w, h) in extremes) {
            for ((kind, size) in listOf("poster" to (200f to 300f), "thumb" to (320f to 180f), "logo" to (72f to 48f), "avatar" to (96f to 96f))) {
                val b = rule.onNodeWithTag("$kind-${w}x$h").getUnclippedBoundsInRoot()
                assertEquals("$kind ${w}x$h largeur", size.first, (b.right - b.left).value, 0.6f)
                assertEquals("$kind ${w}x$h hauteur", size.second, (b.bottom - b.top).value, 0.6f)
            }
        }
    }
}

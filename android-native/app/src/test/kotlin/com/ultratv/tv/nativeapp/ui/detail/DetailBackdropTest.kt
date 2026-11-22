package com.ultratv.tv.nativeapp.ui.detail

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Fond de fiche (`DetailBackdrop`) : un « fond paysage » qui est en fait une affiche (certaines sources y mettent l'affiche)
 * montre l'affiche NETTE à droite, dans sa zone 2:3, jamais agrandie en plein cadre. TV 1920 × 1080.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DetailBackdropTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    @After fun reset() { resetDetailHost(); coil3.SingletonImageLoader.reset() }

    private fun png(name: String, w: Int, h: Int): String {
        val f = File(tmp.root, name)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.rgb(40, 90, 160)) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.toURI().toString()
    }

    private fun show(backdrop: String?, poster: String? = null) {
        rule.setDetail(tv = true, width = 1920, height = 1080) { DetailBackdrop(backdrop, poster, "Orbite basse") }
    }

    private fun assertPosterAtRight(ctx: String) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val p = rule.onNodeWithTag("dt-backdrop-poster").fetchSemanticsNode().boundsInRoot
        assertEquals("$ctx : affiche 2:3", 2f / 3f, p.width / p.height, 0.02f)
        assertTrue("$ctx : affiche à droite (gauche = ${p.left}, largeur écran = ${root.width})", p.left > root.width * 0.5f)
        assertTrue("$ctx : jamais agrandie en plein cadre (largeur ${p.width} sur ${root.width})", p.width < root.width * 0.4f)
        assertTrue("$ctx : dans l'écran", p.right <= root.right + 1f && p.height <= root.height)
    }

    @Test fun fondPortrait_afficheDansSaZone() {
        // Le « fond » est une image portrait (400 × 600) : détecté au chargement, affiche à droite.
        show(png("portrait.png", 400, 600), poster = null)
        rule.waitUntil(10_000) { rule.onAllNodesWithTagExists("dt-backdrop-poster") }
        assertPosterAtRight("fond portrait")
    }

    @Test fun fondIllisible_afficheDansSaZone() {
        // Fond introuvable : même repli (affiche 2:3 à droite), jamais d'image étirée ni de plantage.
        show("file:///introuvable/fond.png", poster = null)
        rule.waitUntil(10_000) { rule.onAllNodesWithTagExists("dt-backdrop-poster") }
        assertPosterAtRight("fond illisible")
    }

    @Test fun sansImage_afficheDansSaZone() {
        show(null, null)
        assertPosterAtRight("sans image")
    }

    @Test fun fondPaysage_pasDAfficheADroite() {
        // Signal positif : la requête d'image est terminée (succès) avant de constater que l'affiche n'apparaît pas.
        val done = java.util.concurrent.CountDownLatch(1)
        var failed: String? = null
        coil3.SingletonImageLoader.setUnsafe(
            coil3.ImageLoader.Builder(androidx.test.core.app.ApplicationProvider.getApplicationContext())
                .eventListener(object : coil3.EventListener() {
                    override fun onSuccess(request: coil3.request.ImageRequest, result: coil3.request.SuccessResult) { done.countDown() }
                    override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) { failed = result.throwable.toString(); done.countDown() }
                }).build(),
        )
        show(png("paysage.png", 1280, 720))
        assertTrue("l'image paysage doit se charger dans le délai", done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("aucune erreur de chargement", null, failed)
        rule.waitForIdle()
        assertTrue("un vrai fond paysage reste plein cadre", !rule.onAllNodesWithTagExists("dt-backdrop-poster"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}

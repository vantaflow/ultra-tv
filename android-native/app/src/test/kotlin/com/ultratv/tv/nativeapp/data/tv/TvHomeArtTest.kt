package com.ultratv.tv.nativeapp.data.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Vignettes de l'écran d'accueil Android TV : logo de chaîne ENTIER centré dans une image 16:9 (le lanceur n'a plus rien à
 * agrandir ni à rogner), fond d'un film sans affiche entier dans une image 2:3, mises en cache par empreinte, servies par
 * une URI content:// qui ne porte aucune adresse ni identifiant de source.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class TvHomeArtTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    // ---- Règles pures ----

    @Test fun logo_large_tient_dans_60_pourcent_de_la_largeur_centre() {
        val area = TvArtPlan.area(TvArtRequest.ChannelLogo("x", "TF1"))
        val b = TvArtPlan.fit(1000, 200, area)
        assertEquals((640 * 0.6f).toInt(), b.width)
        assertEquals(5.0, b.width.toDouble() / b.height, 0.05)
        assertEquals(320.0, b.left + b.width / 2.0, 1.0)
        assertEquals(180.0, b.top + b.height / 2.0, 1.0)
    }

    @Test fun logo_haut_tient_dans_70_pourcent_de_la_hauteur_proportions_gardees() {
        val area = TvArtPlan.area(TvArtRequest.ChannelLogo("x", "M6"))
        val b = TvArtPlan.fit(300, 600, area)
        assertEquals((360 * 0.7f).toInt(), b.height)
        assertEquals(0.5, b.width.toDouble() / b.height, 0.02)
        assertTrue(b.left >= area.left && b.right <= area.right && b.top >= area.top && b.bottom <= area.bottom)
    }

    @Test fun petit_logo_pas_agrandi_au_dela_de_4_fois() {
        val b = TvArtPlan.fit(20, 20, TvArtPlan.area(TvArtRequest.ChannelLogo("x", "A")))
        assertEquals(80, b.width)
        assertEquals(80, b.height)
    }

    @Test fun fond_d_un_film_entier_dans_la_vignette_2_3() {
        val req = TvArtRequest.Backdrop("bg", "Dune")
        assertEquals(400 to 600, TvArtPlan.canvas(req))
        val b = TvArtPlan.fit(1920, 1080, TvArtPlan.area(req))
        assertEquals(16.0 / 9, b.width.toDouble() / b.height, 0.02)
        assertTrue(b.left >= 0 && b.right <= 400 && b.top >= 0 && b.bottom <= 600)
    }

    @Test fun fond_logo_sombre_transparent_clair_sinon_sombre_attenue() {
        val darkOnTransparent = IntArray(100) { if (it < 50) 0x00000000 else 0xFF101010.toInt() }
        assertEquals(TvArtPlan.LIGHT, TvArtPlan.background(darkOnTransparent))
        val red = IntArray(100) { 0xFFE00000.toInt() }
        val bg = TvArtPlan.background(red)
        assertTrue("teinte rouge atténuée", Color.red(bg) > Color.green(bg) && TvArtPlan.luminance(bg) < 0.2)
        assertEquals(TvArtPlan.DARK, TvArtPlan.background(IntArray(10)))
    }

    @Test fun empreinte_et_uri_sans_adresse_ni_identifiant() {
        val logo = "http://panel.example.com:8080/images/logo.png?username=jean&password=secret&stream=4242"
        val key = TvArtPlan.key(TvArtRequest.ChannelLogo(logo, "Canal 4242"))
        val uri = TvArtPlan.uri("com.ultratv.tv.nativeapp.tvart", key)
        assertTrue(uri, Regex("^content://com\\.ultratv\\.tv\\.nativeapp\\.tvart/art/[0-9a-f]{64}\\.jpg$").matches(uri))
        for (leak in listOf("panel", "example", "jean", "secret", "4242", "8080", "logo")) assertFalse("$leak dans $uri", uri.contains(leak))
        // Logo changé : autre image ; même logo : même image.
        assertNotEquals(key, TvArtPlan.key(TvArtRequest.ChannelLogo("$logo&v=2", "Canal 4242")))
        assertEquals(key, TvArtPlan.key(TvArtRequest.ChannelLogo(logo, "Autre nom")))
        // Sans logo : carte au nom.
        assertNotEquals(TvArtPlan.key(TvArtRequest.ChannelLogo(null, "A")), TvArtPlan.key(TvArtRequest.ChannelLogo(" ", "B")))
    }

    @Test fun seuls_les_noms_d_empreinte_sont_servis() {
        val key = "a".repeat(64)
        assertEquals(key, TvArtPlan.keyOf("$key.jpg"))
        assertNull(TvArtPlan.keyOf("../$key.jpg"))
        assertNull(TvArtPlan.keyOf("$key.fallback"))
        assertNull(TvArtPlan.keyOf("shared_prefs.xml"))
        assertNull(TvArtPlan.keyOf(null))
    }

    @Test fun description_aller_retour() {
        for (r in listOf(TvArtRequest.ChannelLogo("http://x/l.png", "TF1"), TvArtRequest.ChannelLogo(null, "Sans logo"), TvArtRequest.Backdrop("http://x/b.jpg", "Dune")))
            assertEquals(r, TvArtPlan.decode(TvArtPlan.encode(r)))
        assertNull(TvArtPlan.decode("n'importe quoi"))
    }

    @Test fun generation_plafonnee_par_fenetre() {
        assertEquals(0L, TvArtPlan.waitMs(listOf(1L, 2L), 1_000, max = 3, windowMs = 60_000))
        assertEquals(59_000L, TvArtPlan.waitMs(listOf(0L, 10L, 20L), 1_000, max = 3, windowMs = 60_000))
        assertEquals(0L, TvArtPlan.waitMs(listOf(0L, 10L, 20L), 61_000, max = 3, windowMs = 60_000))
    }

    @Test fun signatures_republient_avec_la_nouvelle_composition() {
        assertTrue(TvChannelPlan.signature(emptyList()).startsWith("art${TvArtPlan.VERSION}|"))
        assertTrue(FavoritesChannelPlan.signature(emptyList()).startsWith("art${TvArtPlan.VERSION}|"))
        assertTrue(NewsChannelPlan.signature(emptyList()).startsWith("art${TvArtPlan.VERSION}|"))
    }

    @Test fun programme_de_chaine_demande_une_vignette_composee() {
        val c = com.ultratv.tv.nativeapp.data.db.ChannelEntity(providerId = 1, remoteId = "7", name = "TF1", logo = "http://x/tf1.png", categoryId = null, streamUrl = "u")
        val p = TvChannelPlan.channelProgram("SPORT", c, "banner", title = "Match")
        assertEquals(TvArtRequest.ChannelLogo("http://x/tf1.png", "TF1"), p.compose)
        assertEquals(androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9, p.aspectRatio)
    }

    // ---- Composition (Canvas réel) ----

    private fun logo(w: Int, h: Int, color: Int = Color.rgb(230, 30, 30)) =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    /** Rectangle des pixels qui diffèrent du fond (coin haut gauche). */
    private fun content(b: Bitmap): TvArtPlan.Box {
        val bg = b.getPixel(0, 0)
        var l = b.width; var t = b.height; var r = -1; var bo = -1
        for (y in 0 until b.height) for (x in 0 until b.width) if (b.getPixel(x, y) != bg) { l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); bo = maxOf(bo, y) }
        return TvArtPlan.Box(l, t, r - l + 1, bo - t + 1)
    }

    @Test fun composition_logo_entier_centre_en_16_9() {
        val out = TvArtComposer.compose(TvArtRequest.ChannelLogo("x", "TF1"), logo(800, 400))
        assertEquals(640, out.width)
        assertEquals(360, out.height)
        val c = content(out)
        // Logo 2:1 entier : 384 × 192, centré, rien de coupé aux bords.
        assertEquals(2.0, c.width.toDouble() / c.height, 0.05)
        assertEquals(384.0, c.width.toDouble(), 3.0)
        assertEquals(320.0, c.left + c.width / 2.0, 2.0)
        assertEquals(180.0, c.top + c.height / 2.0, 2.0)
        assertTrue(c.left > 0 && c.top > 0 && c.right < 640 && c.bottom < 360)
        // Coins : fond (pas le logo agrandi en plein cadre).
        assertNotEquals(Color.rgb(230, 30, 30), out.getPixel(2, 2))
    }

    @Test fun composition_sans_logo_carte_au_nom() {
        val out = TvArtComposer.compose(TvArtRequest.ChannelLogo(null, "Canal Info"), null)
        assertEquals(640 to 360, out.width to out.height)
        val c = content(out)
        assertTrue("texte dessiné au centre", c.width > 40 && c.left < 320 && c.right > 320)
    }

    @Test fun composition_fond_entier_en_2_3() {
        val out = TvArtComposer.compose(TvArtRequest.Backdrop("x", ""), logo(1600, 900, Color.rgb(20, 120, 220)))
        assertEquals(400 to 600, out.width to out.height)
        val c = content(out)
        assertEquals(16.0 / 9, c.width.toDouble() / c.height, 0.05)
        assertTrue(c.left > 0 && c.right < 400)
    }

    // ---- Cache disque ----

    private fun store() = TvArtStore(ctx) { OkHttpClient() }.also { it.settleMs = 0 }

    private fun pngFile(name: String, w: Int, h: Int): String {
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { logo(w, h).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.toURI().toString()
    }

    @Test fun cache_par_empreinte_regenere_si_nettoye() = runBlocking {
        val s = store()
        val req = TvArtRequest.ChannelLogo(pngFile("l.png", 300, 100), "TF1")
        val uri = s.uriFor(req)
        val key = TvArtPlan.keyOf(uri.substringAfterLast('/'))!!
        val f = File(ctx.cacheDir, "${TvArtPlan.DIR}/$key.jpg")
        assertTrue(f.exists())
        val decoded = BitmapFactory.decodeFile(f.path)
        assertEquals(640 to 360, decoded.width to decoded.height)
        // Deuxième demande : même fichier, pas régénéré.
        val stamp = f.lastModified()
        f.setLastModified(stamp - 10_000)
        assertEquals(uri, s.uriFor(req))
        assertEquals(stamp - 10_000, f.lastModified())
        // « Tout nettoyer » : image partie, refaite à la demande du lanceur.
        assertTrue(s.clear() > 0)
        assertFalse(f.exists())
        assertNotNull(s.fileFor(key))
        assertTrue(f.exists())
        // Nom inconnu : rien.
        assertNull(s.fileFor("b".repeat(64)))
    }

    @Test fun logo_introuvable_carte_au_nom_sous_la_meme_uri() = runBlocking {
        val s = store()
        val req = TvArtRequest.ChannelLogo("file:///nulle/part.png", "Canal Perdu")
        val uri = s.uriFor(req)
        val key = TvArtPlan.keyOf(uri.substringAfterLast('/'))!!
        assertTrue(File(ctx.cacheDir, "${TvArtPlan.DIR}/$key.jpg").exists())
        assertTrue("repli à réessayer plus tard", File(ctx.cacheDir, "${TvArtPlan.DIR}/$key.fallback").exists())
    }
}

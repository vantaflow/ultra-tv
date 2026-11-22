package com.ultratv.tv.nativeapp.releasecheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Icône de l'écran de démarrage : sous Android 8 (minSdk 25 = Android 7.1), `@mipmap/ic_launcher` est un PNG de
 * 192 px agrandi par core-splashscreen → flou sur tablette. L'icône doit être un drawable vectoriel dédié, net à
 * toutes les densités et toutes les versions, dimensionné selon les règles « sans fond d'icône » (288dp, contenu
 * dans le cercle de 192dp).
 */
class SplashIconTest {
    private val res = File("src/main/res")

    private fun splashIconRef(themes: File): String? =
        Regex("""name="windowSplashScreenAnimatedIcon">([^<]+)<""").find(themes.readText())?.groupValues?.get(1)?.trim()

    @Test
    fun icone_demarrage_vectorielle_dediee() {
        val valuesDirs = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        val themes = valuesDirs.map { File(it, "themes.xml") }.filter { it.exists() && splashIconRef(it) != null }
        assertTrue("au moins un thème déclare windowSplashScreenAnimatedIcon", themes.isNotEmpty())
        themes.forEach { assertEquals("${it.path} : icône de démarrage", "@drawable/ic_splash", splashIconRef(it)) }
    }

    @Test
    fun ic_splash_vecteur_288dp_sans_variante_bitmap() {
        val xml = File(res, "drawable/ic_splash.xml")
        assertTrue("drawable/ic_splash.xml existe", xml.exists())
        val text = xml.readText()
        assertTrue("c'est un <vector>", text.contains("<vector"))
        assertTrue(text.contains("""android:width="288dp""""))
        assertTrue(text.contains("""android:height="288dp""""))
        // Aucune variante PNG ne doit masquer le vecteur dans une densité.
        val bitmaps = res.listFiles().orEmpty().flatMap { d -> d.listFiles().orEmpty().filter { it.nameWithoutExtension == "ic_splash" && it.extension != "xml" } }
        assertFalse("pas de ic_splash bitmap : $bitmaps", bitmaps.isNotEmpty())
    }
}

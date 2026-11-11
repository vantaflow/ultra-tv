package com.ultratv.tv.nativeapp.releasecheck

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bibliothèques natives sans règles R8 dans leur AAR : leur code JNI cherche des classes et membres Java par leur
 * nom. Si ces règles disparaissent, la release (minifiée) plante dès l'utilisation — invisible en debug.
 */
class ProguardRulesTest {
    private val rules = File("proguard-rules.pro").readText()

    @Test
    fun regles_libVlc_presentes() {
        assertTrue("org.videolan.libvlc doit être conservé tel quel", rules.contains("-keep class org.videolan.libvlc.** { *; }"))
    }

    @Test
    fun regles_clientRtmp_presentes() {
        assertTrue(rules.contains("-keep class io.antmedia.rtmp_client.** { *; }"))
    }

    /** La page du lecteur de bande-annonce appelle les méthodes du pont par leur nom : renommées par R8, plus aucun événement. */
    @Test
    fun regles_pontBandeAnnonce_presentes() {
        assertTrue(rules.contains("-keepclassmembers class com.ultratv.tv.nativeapp.ui.trailer.TrailerBridge"))
        assertTrue(rules.contains("@android.webkit.JavascriptInterface <methods>;"))
    }
}

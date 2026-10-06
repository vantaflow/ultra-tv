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
}

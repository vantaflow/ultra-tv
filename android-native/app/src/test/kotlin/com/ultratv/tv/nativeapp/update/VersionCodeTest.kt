package com.ultratv.tv.nativeapp.update

import com.ultratv.tv.nativeapp.update.UpdateVersions.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** versionCode : une pré-version -rcN est strictement inférieure à la stable du même numéro ; tout > 10375 (1.3.75, publiée). */
class VersionCodeTest {
    private fun v(t: String) = UpdateVersions.parse(t)!!

    @Test fun `exemples de codes`() {
        assertEquals(1_037_601, v("v1.3.76-rc1").androidCode)
        assertEquals(1_037_602, v("v1.3.76-rc2").androidCode)
        assertEquals(1_037_699, v("v1.3.76").androidCode)
        assertEquals(1_037_599, v("v1.3.75").androidCode)
        assertEquals(1_037_501, v("v1.3.75-rc1").androidCode)
    }

    @Test fun `une pre-version est strictement inferieure a la stable du meme numero`() {
        for (rc in 1..98) assertTrue(v("1.3.76-rc$rc").androidCode < v("1.3.76").androidCode)
        assertTrue(v("1.3.75").androidCode < v("1.3.76-rc1").androidCode)
        assertTrue(v("1.3.99").androidCode < v("1.4.0-rc1").androidCode)
        assertTrue(v("1.9.99").androidCode < v("2.0.0-rc1").androidCode)
    }

    @Test fun `tous les nouveaux codes depassent le code publie 10375`() {
        assertTrue(v("1.3.75-rc1").androidCode > 10_375)
        assertTrue(v("1.3.75").androidCode > 10_375)
        assertTrue(UpdateVersions.androidVersionCode(0, 0, 1, null) > 0)
    }

    @Test fun `le code suit le meme ordre que le rang de comparaison`() {
        val tags = listOf("1.3.75-rc1", "1.3.75-rc2", "1.3.75", "1.3.76-rc1", "1.3.76", "1.4.0-rc1", "1.4.0")
        assertEquals(tags.sortedBy { v(it).rank }, tags.sortedBy { v(it).androidCode })
    }

    @Test fun `la stable 1_3_76 est proposee a la 1_3_75 et a la 1_3_75-rc1`() {
        val latest = listOf(Release("v1.3.76", preRelease = false))
        assertEquals("v1.3.76", UpdateVersions.pick(latest, false, UpdateVersions.installed("1.3.75", "v1.3.75"))?.tag)
        assertEquals("v1.3.76", UpdateVersions.pick(latest, false, UpdateVersions.installed("1.3.75", "v1.3.75-rc1")!!)?.tag)
        assertEquals("v1.3.76", UpdateVersions.pick(latest, false, UpdateVersions.installed("1.3.75", "")!!)?.tag)
        // canal pré-version : la stable 1.3.76 passe aussi devant la 1.3.76-rc1
        val all = listOf(Release("v1.3.76", preRelease = false), Release("v1.3.76-rc1", preRelease = true))
        assertEquals("v1.3.76", UpdateVersions.pick(all, true, v("1.3.76-rc1"))?.tag)
    }

    /** build.gradle.kts garde la même formule que UpdateVersions.androidVersionCode (le Gradle n'est pas exécuté ici). */
    @Test fun `build_gradle_kts utilise la meme formule`() {
        val f = listOf("build.gradle.kts", "app/build.gradle.kts").map(::File).firstOrNull { it.isFile }
        assumeTrue(f != null)
        val text = f!!.readText()
        assertNotNull(Regex("""parts\[0\] \* 1_000_000 \+ parts\[1\] \* 10_000 \+ parts\[2\] \* 100 \+ \(rc \?: 99\)""").find(text))
        assertTrue(text.contains("RC_NUMBER") && text.contains("rcNumber"))
    }
}

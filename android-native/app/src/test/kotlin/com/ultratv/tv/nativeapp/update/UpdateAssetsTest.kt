package com.ultratv.tv.nativeapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateAssetsTest {
    private fun a(n: String) = UpdateAssets.Asset(n, "https://exemple.invalid/$n")
    private val full = listOf(
        a("UltraTV-debug.apk"), a("UltraTV-debug.apk.sha256"), a("SHA256SUMS.txt"),
        a("UltraTV-1.1.1-armeabi-v7a.apk"), a("UltraTV-1.1.1-arm64-v8a.apk"), a("UltraTV-1.1.1-x86_64.apk"),
    )

    @Test fun chromecastHd_armeabiV7a_prendLApkV7a_verifieParSums() {
        val c = UpdateAssets.choose(full, "1.1.1", listOf("armeabi-v7a", "armeabi"))!!
        assertEquals("UltraTV-1.1.1-armeabi-v7a.apk", c.apkName)
        assertNull(c.sha256Url)
        assertEquals("https://exemple.invalid/SHA256SUMS.txt", c.sumsUrl)
    }

    @Test fun boxArm64_prendLApkArm64_premierAbiDisponible() {
        assertEquals("UltraTV-1.1.1-arm64-v8a.apk", UpdateAssets.choose(full, "1.1.1", listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!.apkName)
    }

    @Test fun abiAbsentDeLaRelease_passeAuSuivant() {
        val sansArm64 = full.filterNot { it.name.contains("arm64") }
        assertEquals("UltraTV-1.1.1-armeabi-v7a.apk", UpdateAssets.choose(sansArm64, "1.1.1", listOf("arm64-v8a", "armeabi-v7a"))!!.apkName)
    }

    @Test fun rienNeCorrespond_repliSurLUniverselEtSonSha256() {
        val c = UpdateAssets.choose(full, "1.1.1", listOf("mips"))!!
        assertEquals("UltraTV-debug.apk", c.apkName)
        assertEquals("https://exemple.invalid/UltraTV-debug.apk.sha256", c.sha256Url)
        assertNull(c.sumsUrl)
    }

    @Test fun releaseAncienne_sansAssetsParAbi_universel() {
        val c = UpdateAssets.choose(listOf(a("UltraTV-debug.apk"), a("UltraTV-debug.apk.sha256")), "1.1.0", listOf("arm64-v8a"))!!
        assertEquals("UltraTV-debug.apk", c.apkName)
    }

    @Test fun sansSha256SumsLApkParAbiNEstPasChoisi() {
        val c = UpdateAssets.choose(full.filterNot { it.name == "SHA256SUMS.txt" }, "1.1.1", listOf("arm64-v8a"))!!
        assertEquals("UltraTV-debug.apk", c.apkName)
    }

    @Test fun aucunApk_null() = assertNull(UpdateAssets.choose(listOf(a("SHA256SUMS.txt")), "1.1.1", listOf("arm64-v8a")))

    @Test fun versionDuNomDAsset_doitCorrespondre() {
        assertEquals("UltraTV-debug.apk", UpdateAssets.choose(full, "1.2.0", listOf("arm64-v8a"))!!.apkName)
    }

    @Test fun digestFor_trouveLaBonneLigne() {
        val h1 = "a".repeat(64); val h2 = "b".repeat(64)
        val sums = "$h1  UltraTV-debug.apk\n$h2  UltraTV-1.1.1-arm64-v8a.apk\n"
        assertEquals(h2, UpdateAssets.digestFor(sums, "UltraTV-1.1.1-arm64-v8a.apk"))
        assertEquals(h1, UpdateAssets.digestFor(sums, "UltraTV-debug.apk"))
        assertEquals("", UpdateAssets.digestFor(sums, "inconnu.apk"))
        assertEquals(h2, UpdateAssets.digestFor("$h2 *UltraTV-x.apk", "UltraTV-x.apk"))
        assertEquals("", UpdateAssets.digestFor("zz  UltraTV-x.apk", "UltraTV-x.apk"))
    }
}

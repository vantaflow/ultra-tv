package com.ultratv.tv.nativeapp.data.memory

import com.ultratv.tv.nativeapp.i18n.AppLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Mémoire et stockage : tailles de dossiers, limite Auto du cache d'images, purge des temporaires, mise en forme. */
class MemoryLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, bytes: Int, ageMs: Long = 0L): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(ByteArray(bytes)); if (ageMs > 0) setLastModified(System.currentTimeMillis() - ageMs) }
    }

    // ── Calcul des tailles ──
    @Test fun taille_dossierRecursif_additionneTout() {
        val root = tmp.newFolder("a")
        file(root, "x", 100); file(File(root, "sub"), "y", 50); file(File(root, "sub/deep"), "z", 25)
        assertEquals(175L, StorageMath.sizeOf(root))
    }

    @Test fun taille_inexistantOuNull_zero() {
        assertEquals(0L, StorageMath.sizeOf(null))
        assertEquals(0L, StorageMath.sizeOf(File(tmp.root, "absent")))
    }

    @Test fun taille_fichierSimple() = assertEquals(42L, StorageMath.sizeOf(file(tmp.root, "f", 42)))

    @Test fun vider_dossier_garde_ledossier_et_compte_les_octets() {
        val root = tmp.newFolder("c")
        file(root, "x", 10); file(File(root, "s"), "y", 20)
        assertEquals(30L, StorageMath.clearDirectory(root))
        assertTrue(root.isDirectory)
        assertEquals(0, root.listFiles()!!.size)
    }

    // ── Fichiers temporaires et purge ──
    @Test fun nomsTemporaires_reconnus() {
        assertTrue(TempFiles.isSyncTemp("xtream-123456.json"))
        assertTrue(TempFiles.isSyncTemp("stalker-9.json"))
        assertTrue(TempFiles.isSyncTemp("xmltv-77.xml"))
        assertFalse(TempFiles.isSyncTemp("image_cache"))
        assertFalse(TempFiles.isSyncTemp("timeshift"))
        assertFalse(TempFiles.isSyncTemp("movie.mkv.part"))
    }

    @Test fun tailleTemporaires_syncPlusSousTitres_sansLeReste() {
        val cache = tmp.newFolder("cache")
        file(cache, "xtream-1.json", 100); file(cache, "xmltv-2.xml", 40)
        file(File(cache, "subtitles"), "fr.srt", 10)
        file(File(cache, "image_cache"), "img", 9_999); file(File(cache, "timeshift"), "buf.ts", 5_000)
        assertEquals(150L, TempFiles.size(cache))
    }

    @Test fun purge_garde_les_fichiers_recents_et_vide_les_sousTitres() {
        val cache = tmp.newFolder("cache2")
        val now = System.currentTimeMillis()
        file(cache, "xtream-old.json", 100, ageMs = 2 * TempFiles.MIN_AGE_MS)
        file(cache, "xtream-new.json", 70)
        file(File(cache, "subtitles"), "en.srt", 30)
        file(File(cache, "image_cache"), "img", 500)
        assertEquals(130L, TempFiles.purge(cache, now))
        assertFalse(File(cache, "xtream-old.json").exists())
        assertTrue(File(cache, "xtream-new.json").exists())
        assertTrue(File(cache, "image_cache/img").exists())          // jamais touché (c'est le journal de Coil)
        assertEquals(0, File(cache, "subtitles").listFiles()!!.size)
    }

    @Test fun purge_ageNul_supprimeTout_horsSynchro() {
        val cache = tmp.newFolder("cache3")
        file(cache, "stalker-1.json", 60)
        assertEquals(60L, TempFiles.purge(cache, System.currentTimeMillis(), minAgeMs = 0))
        assertEquals(0L, TempFiles.size(cache))
    }

    @Test fun purge_dossierInexistant_neCasseRien() = assertEquals(0L, TempFiles.purge(File(tmp.root, "nope"), 0L))

    // ── Limite Auto du cache d'images ──
    private val mb = ImageCacheLimit.MB
    private val gb = 1024 * mb

    @Test fun auto_peuDePlace_minimum64() {
        assertEquals(64 * mb, ImageCacheLimit.autoBytes(300 * mb, false))
        assertEquals(64 * mb, ImageCacheLimit.autoBytes(0, false))
        assertEquals(64 * mb, ImageCacheLimit.autoBytes(-5, true))
    }

    @Test fun auto_unDixieme_arrondiAu16Inferieur() {
        // 2 Go libres → 204 Mo → 192 Mo
        assertEquals(192 * mb, ImageCacheLimit.autoBytes(2 * gb, false))
    }

    @Test fun auto_plafond512_et128_surAppareilLimite() {
        assertEquals(512 * mb, ImageCacheLimit.autoBytes(20 * gb, false))
        assertEquals(128 * mb, ImageCacheLimit.autoBytes(20 * gb, true))
        assertEquals(96 * mb, ImageCacheLimit.autoBytes(1 * gb, true))
    }

    @Test fun limite_choixManuel_ouAutoSiInconnu() {
        assertEquals(250 * mb, ImageCacheLimit.bytes("250", 20 * gb, true))
        assertEquals(100 * mb, ImageCacheLimit.bytes("100", 0, false))
        assertEquals(ImageCacheLimit.autoBytes(2 * gb, false), ImageCacheLimit.bytes("auto", 2 * gb, false))
        assertEquals(ImageCacheLimit.autoBytes(2 * gb, false), ImageCacheLimit.bytes("999", 2 * gb, false))
    }

    @Test fun depassement_seulementAuDessusDeLaLimite() {
        assertTrue(ImageCacheLimit.overLimit(101, 100))
        assertFalse(ImageCacheLimit.overLimit(100, 100))
        assertFalse(ImageCacheLimit.overLimit(5, 0))
    }

    // ── Économie de mémoire et RAM ──
    @Test fun economie_selonLeMode() {
        assertTrue(EconomyMode.effective("on", false))
        assertFalse(EconomyMode.effective("off", true))
        assertTrue(EconomyMode.effective("auto", true))
        assertFalse(EconomyMode.effective("auto", false))
        assertTrue(EconomyMode.effective("n'importe quoi", true))   // valeur inconnue = Auto
    }

    @Test fun appareilLimite_drapeauOuMoinsDe2Go() {
        assertTrue(RamClass.isLimited(1_500L * mb, false))
        assertTrue(RamClass.isLimited(4 * gb, true))
        assertFalse(RamClass.isLimited(2 * gb, false))
        assertFalse(RamClass.isLimited(0, false))   // total inconnu : pas d'alerte
    }

    @Test fun totalUtilise_additionneSansLeLibre() {
        assertEquals(21L, StorageUsage(1, 2, 3, 4, 5, 6, free = 1_000).total)
    }

    // ── Mise en forme ──
    @Test fun format_francais_KoMoGo_virguleDecimale() {
        assertEquals("0 o", SizeFormat.format(0, AppLang.French))
        assertEquals("512 o", SizeFormat.format(512, AppLang.French))
        assertEquals("2 Ko", SizeFormat.format(2048, AppLang.French))
        assertEquals("312 Mo", SizeFormat.format(312 * mb, AppLang.French))
        assertEquals("1,5 Go", SizeFormat.format(gb + gb / 2, AppLang.French))
        assertEquals("1 Go", SizeFormat.format(gb, AppLang.French))
        assertEquals("12,3 Mo", SizeFormat.format((12.3 * mb).toLong(), AppLang.French))
    }

    @Test fun format_anglaisEspagnolArabe() {
        assertEquals("1.5 GB", SizeFormat.format(gb + gb / 2, AppLang.English))
        assertEquals("312 MB", SizeFormat.format(312 * mb, AppLang.English))
        assertEquals("1,5 GB", SizeFormat.format(gb + gb / 2, AppLang.Spanish))
        assertEquals("5 bytes", SizeFormat.format(5, AppLang.Spanish))
        assertEquals("312 م.ب", SizeFormat.format(312 * mb, AppLang.Arabic))
        assertEquals("1٫5 ج.ب", SizeFormat.format(gb + gb / 2, AppLang.Arabic))
        assertEquals("3 MB", SizeFormat.format(3 * mb, AppLang.System))
    }

    @Test fun format_negatifEtEnorme() {
        assertEquals("0 o", SizeFormat.format(-10, AppLang.French))
        assertEquals("2 To", SizeFormat.format(2L * 1024 * gb, AppLang.French))
    }
}

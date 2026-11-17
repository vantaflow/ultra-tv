package com.ultratv.tv.nativeapp.data.nas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure des partages NAS : adresse, liens internes, parcours borné, catalogue. */
class NasLogicTest {

    // ───── Adresse ─────

    @Test fun adresse_smbClassique() {
        val a = Nas.parseAddress("smb://192.168.1.20/video")!!
        assertEquals("192.168.1.20", a.host); assertEquals(445, a.port); assertEquals("video", a.share); assertEquals(emptyList<String>(), a.path)
        assertEquals("smb://192.168.1.20/video", a.url())
        assertEquals("video", a.rootName)
    }

    @Test fun adresse_dossierEtPort() {
        val a = Nas.parseAddress(" smb://nas.local:4450/media/Films/HD/ ")!!
        assertEquals(4450, a.port); assertEquals("media", a.share); assertEquals(listOf("Films", "HD"), a.path)
        assertEquals("smb://nas.local:4450/media/Films/HD", a.url())
        assertEquals("HD", a.rootName)
    }

    @Test fun adresse_formesWindowsEtSansSchema() {
        assertEquals("smb://nas/partage/dossier", Nas.parseAddress("\\\\nas\\partage\\dossier")!!.url())
        assertEquals("smb://nas/partage", Nas.parseAddress("nas/partage")!!.url())
        assertEquals("smb://nas/partage", Nas.parseAddress("SMB://nas/partage")!!.url())
    }

    @Test fun adresse_invalide() {
        assertNull(Nas.parseAddress(""))
        assertNull(Nas.parseAddress("smb://nas"))
        assertNull(Nas.parseAddress("smb:///partage"))
        assertNull(Nas.parseAddress("http://nas/partage"))
        assertNull(Nas.parseAddress("smb://nas:abc/partage"))
        assertNull(Nas.parseAddress("smb://nas:0/partage"))
        assertNull(Nas.parseAddress("smb://nas/partage/../autre"))
        assertNull(Nas.parseAddress("smb://h o/partage"))
    }

    @Test fun adresse_refuseLesIdentifiantsDansLAdresse() {
        // Ils se saisissent dans leurs champs : jamais stockés dans l'adresse.
        assertNull(Nas.parseAddress("smb://moi:secret@nas/partage"))
        assertNull(Nas.parseAddress("smb://moi@nas/partage"))
    }

    // ───── Liens internes ─────

    @Test fun lien_allerRetourAvecAccentsEtEspaces() {
        val path = listOf("Séries", "Ma Série (2019)", "Saison 1", "S01E02 – Épisode #1.mkv")
        val link = Nas.NasLink.build(42, path)
        assertTrue(Nas.NasLink.isNas(link))
        assertTrue(link.startsWith("nas://42/"))
        val p = Nas.NasLink.parse(link)!!
        assertEquals(42L, p.providerId); assertEquals(path, p.path)
    }

    @Test fun lien_neContientNiHoteNiCheminEnClair() {
        val link = Nas.NasLink.build(7, listOf("Films", "Secret.mkv"))
        assertFalse(link.contains("Secret"))
        assertFalse(link.contains("smb"))
    }

    @Test fun lien_invalidesRejetes() {
        assertNull(Nas.NasLink.parse("http://x/y"))
        assertNull(Nas.NasLink.parse("nas://abc/Zm9v"))
        assertNull(Nas.NasLink.parse("nas://1/"))
        assertNull(Nas.NasLink.parse("nas://1/@@@"))
        // « ../x » forgé à la main : jamais accepté.
        val evil = "nas://1/" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("a/../../etc/passwd".toByteArray())
        assertNull(Nas.NasLink.parse(evil))
        assertFalse(Nas.NasLink.isNas(null))
        assertFalse(Nas.NasLink.isNas("mediaserver://1/abc"))
    }

    // ───── Parcours borné ─────

    /** Faux partage : dossier → entrées. */
    private class FakeFs(val tree: Map<String, List<Nas.Entry>>) : Nas.Lister {
        val listed = ArrayList<String>()
        override fun list(path: List<String>): List<Nas.Entry> {
            val k = path.joinToString("/")
            listed += k
            return tree[k] ?: throw java.io.IOException("denied")
        }
    }

    private fun f(name: String, size: Long = 100, mtime: Long = 1_000) = Nas.Entry(name, false, size, mtime)
    private fun d(name: String) = Nas.Entry(name, true)

    @Test fun parcours_garde_les_videos_seulement_et_ignore_corbeilles_et_echantillons() {
        val fs = FakeFs(mapOf(
            "" to listOf(f("Titre (2019).mkv"), f("notes.txt"), f("cover.jpg"), d("Films"), d("@eaDir"), d("#recycle"), d("Extras"), f("sample.mkv")),
            "Films" to listOf(f("Autre.2020.mp4"), f("Autre.2020.srt"), d("Sous")),
            "Films/Sous" to listOf(f("Profond.avi")),
        ))
        val out = Nas.scan(fs)
        assertEquals(listOf("Titre (2019).mkv", "Films/Autre.2020.mp4", "Films/Sous/Profond.avi").sorted(), out.map { it.path.joinToString("/") }.sorted())
        assertFalse(fs.listed.any { it.startsWith("@eaDir") || it.startsWith("#recycle") || it.startsWith("Extras") })
    }

    @Test fun parcours_borneEnProfondeur() {
        val fs = FakeFs(mapOf(
            "" to listOf(d("a")), "a" to listOf(d("b"), f("1.mkv")), "a/b" to listOf(d("c"), f("2.mkv")), "a/b/c" to listOf(f("3.mkv")),
        ))
        val out = Nas.scan(fs, Nas.Limits(maxDepth = 2))
        assertEquals(setOf("a/1.mkv", "a/b/2.mkv"), out.map { it.path.joinToString("/") }.toSet())
    }

    @Test fun parcours_borneEnNombreDeFichiers() {
        val files = (1..50).map { f("v$it.mkv") }
        val out = Nas.scan(FakeFs(mapOf("" to files)), Nas.Limits(maxFiles = 10))
        assertEquals(10, out.size)
    }

    @Test fun parcours_borneEnNombreDeDossiers_etBoucleDeLiens() {
        // Un « dossier » qui se contient lui-même (lien symbolique) : borné par la profondeur ET par le nombre de dossiers.
        val fs = FakeFs(mapOf("" to listOf(d("x")), "x" to listOf(d("x"), f("a.mkv")), "x/x" to listOf(d("x"), f("a.mkv")), "x/x/x" to listOf(f("a.mkv"))))
        val out = Nas.scan(fs, Nas.Limits(maxDepth = 50, maxFolders = 3))
        assertTrue(out.size <= 3)
        assertTrue(fs.listed.size <= 3)
    }

    @Test fun parcours_dossierIllisibleSauteMaisRacineIllisibleRemonte() {
        val fs = FakeFs(mapOf("" to listOf(d("prive"), d("ok")), "ok" to listOf(f("a.mkv"))))   // « prive » absent : IOException
        assertEquals(listOf("ok/a.mkv"), Nas.scan(fs).map { it.path.joinToString("/") })
        var thrown = false
        try { Nas.scan(FakeFs(emptyMap())) } catch (e: java.io.IOException) { thrown = true }
        assertTrue(thrown)
    }

    @Test fun parcours_nomsSuspectsIgnores() {
        val fs = FakeFs(mapOf("" to listOf(f("../evil.mkv"), f("a/b.mkv"), f("ok.mkv"), f(".caché.mkv"))))
        assertEquals(listOf("ok.mkv"), Nas.scan(fs).map { it.name })
    }

    @Test fun parcours_annulation() {
        val fs = FakeFs(mapOf("" to listOf(d("a")), "a" to listOf(f("1.mkv"))))
        assertEquals(0, Nas.scan(fs, cancelled = { true }).size)
    }

    // ───── Catalogue ─────

    private fun vf(path: String, mtime: Long = 5) = Nas.VideoFile(path.split('/'), 1_000, mtime)

    @Test fun catalogue_filmsEtSeriesSepares_categoriesParDossierDePremierNiveau() {
        val files = listOf(
            vf("Films/Titre (2019).mkv"), vf("Films/Autre.2020.1080p.mp4"), vf("Racine (2001).avi"),
            vf("Séries/Ma Série/Saison 1/Ma Série S01E02.mkv"), vf("Séries/Ma Série/Saison 1/Ma Série S01E01.mkv"),
            vf("Séries/Ma Série/Saison 2/Ma Série S02E01.mkv"),
            vf("Séries/Autre Show/Autre.Show.1x03.avi"),
        )
        val c = Nas.buildCatalog(9, "video", files)
        assertEquals(setOf("Titre", "Autre", "Racine"), c.movies.map { it.name }.toSet())
        assertEquals(setOf("Ma Série", "Autre Show"), c.series.map { it.name }.toSet())
        assertEquals(setOf("Films", Nas.ROOT_CATEGORY), c.movieCategories.map { it.remoteId }.toSet())
        assertEquals("video", c.movieCategories.first { it.remoteId == Nas.ROOT_CATEGORY }.name)
        assertEquals(listOf("Séries"), c.seriesCategories.map { it.remoteId })
        assertTrue(c.movieCategories.all { it.kind == "MOVIE" && it.providerId == 9L })
        assertTrue(c.seriesCategories.all { it.kind == "SERIES" })
        val titre = c.movies.first { it.name == "Titre" }
        assertEquals("Films", titre.categoryId); assertEquals(2019, titre.year); assertEquals("mkv", titre.container)
        assertNull(titre.poster)   // affiche : repli TMDB de l'appli
        assertEquals(Nas.ROOT_CATEGORY, c.movies.first { it.name == "Racine" }.categoryId)
    }

    @Test fun catalogue_lienDeLecture_sansHoteNiIdentifiants() {
        val c = Nas.buildCatalog(3, "r", listOf(vf("Films/Titre (2019).mkv")))
        val m = c.movies.single()
        assertTrue(Nas.NasLink.isNas(m.streamUrl))
        assertEquals(listOf("Films", "Titre (2019).mkv"), Nas.NasLink.parse(m.streamUrl)!!.path)
        assertFalse(m.streamUrl.contains("smb"))
    }

    @Test fun catalogue_episodesRegroupesParSerie_etOrdonnes() {
        val files = listOf(
            vf("S/Show/Season 2/Show.S02E01.mkv"), vf("S/Show/Season 1/Show.S01E02.mkv"), vf("S/Show/Season 1/Show.S01E01.The.Start.mkv"),
        )
        val c = Nas.buildCatalog(1, "r", files)
        val s = c.series.single()
        assertEquals("Show", s.name)
        val eps = Nas.toEpisodes(1, 77, c.episodes[s.remoteId]!!)
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), eps.map { it.season to it.episode })
        assertEquals("The Start", eps[0].title)
        assertEquals("S01E02", eps[1].title)   // titre absent : générique, complété par TMDB
        assertTrue(eps.all { it.seriesId == 77L && Nas.NasLink.isNas(it.streamUrl) })
        assertEquals(eps.size, eps.map { it.remoteId }.toSet().size)
    }

    @Test fun catalogue_serieRegroupeeMalgreLesEcrituresDifferentes() {
        val files = listOf(vf("A/La Casa de Papel/La.Casa.de.Papel.S01E01.mkv"), vf("A/La Casa de Papel/la casa de papel S01E02.mkv"), vf("A/Élite/Elite.S01E01.mkv"), vf("A/Élite/Élite S01E02.mkv"))
        val c = Nas.buildCatalog(1, "r", files)
        assertEquals(2, c.series.size)
        assertEquals(setOf(2), c.episodes.values.map { it.size }.toSet())
    }

    @Test fun catalogue_remoteIdStableEtUniqueParFichier() {
        val files = listOf(vf("F/a (2000).mkv"), vf("F/b (2001).mkv"), vf("G/a (2000).mkv"))
        val c1 = Nas.buildCatalog(1, "r", files)
        val c2 = Nas.buildCatalog(1, "r", files.reversed())
        assertEquals(3, c1.movies.map { it.remoteId }.toSet().size)
        assertEquals(c1.movies.map { it.remoteId }.toSet(), c2.movies.map { it.remoteId }.toSet())
    }

    @Test fun catalogue_vide() {
        val c = Nas.buildCatalog(1, "r", emptyList())
        assertTrue(c.movies.isEmpty() && c.series.isEmpty() && c.movieCategories.isEmpty() && c.seriesCategories.isEmpty())
        assertNotNull(c.episodes)
    }
}

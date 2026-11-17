package com.ultratv.tv.nativeapp.data.nas

import com.ultratv.tv.nativeapp.data.nas.NasNames.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reconnaissance des films et épisodes par nom de fichier (fonction pure). */
class NasNamesTest {
    private fun movie(name: String, folders: List<String> = emptyList(), title: String, year: Int?) {
        val p = NasNames.parse(name, folders)
        assertEquals("type de $name", Kind.MOVIE, p.kind)
        assertEquals("titre de $name", title, p.title)
        assertEquals("année de $name", year, p.year)
    }

    private fun episode(name: String, folders: List<String> = emptyList(), series: String, season: Int, ep: Int, title: String = "", year: Int? = null) {
        val p = NasNames.parse(name, folders)
        assertEquals("type de $name", Kind.EPISODE, p.kind)
        assertEquals("série de $name", series, p.seriesTitle)
        assertEquals("saison de $name", season, p.season)
        assertEquals("épisode de $name", ep, p.episode)
        assertEquals("titre d'épisode de $name", title, p.title)
        assertEquals("année de $name", year, p.year)
    }

    // ───── Films ─────

    @Test fun film_titreEtAnneeEntreParentheses() = movie("Titre (2019).mkv", title = "Titre", year = 2019)

    @Test fun film_pointsAnneeEtMotsDeRelease() = movie("Titre.2019.1080p.x264.mkv", title = "Titre", year = 2019)

    @Test fun film_motsDeReleaseSansAnnee() = movie("Le.Grand.Film.1080p.BluRay.x265-GRP.mkv", title = "Le Grand Film", year = null)

    @Test fun film_webDlEtAudio() = movie("Mon.Film.2021.MULTi.1080p.WEB-DL.DDP5.1.H.264-FOO.mkv", title = "Mon Film", year = 2021)

    @Test fun film_tiretsInternesConserves() = movie("Spider-Man.No.Way.Home.2021.2160p.mkv", title = "Spider-Man No Way Home", year = 2021)

    @Test fun film_titreContenantUneAnnee() = movie("Blade Runner 2049 (2017).mp4", title = "Blade Runner 2049", year = 2017)

    @Test fun film_titreQuiEstUneAnnee() {
        movie("1917.2019.1080p.mkv", title = "1917", year = 2019)
        movie("2012 (2009).avi", title = "2012", year = 2009)
        movie("1917.mkv", title = "1917", year = null)
    }

    @Test fun film_crochetsRetires() = movie("[Groupe] Titre (2018) [1080p] [FR].mkv", title = "Titre", year = 2018)

    @Test fun film_tiretsBasEtEspaces() = movie("Mon_Film_2015_720p.mkv", title = "Mon Film", year = 2015)

    @Test fun film_motDuTitreIdentiqueAUnMotDeRelease() {
        // « French » et « Web » font partie de titres réels : ils ne coupent pas.
        movie("The.French.Dispatch.2021.1080p.mkv", title = "The French Dispatch", year = 2021)
        movie("Charlotte's Web (2006).mkv", title = "Charlotte's Web", year = 2006)
    }

    @Test fun film_accentsConserves() = movie("Amélie (2001) 1080p.mkv", title = "Amélie", year = 2001)

    @Test fun film_numeroDeDisqueRetire() = movie("Titre (2005) CD1.avi", title = "Titre", year = 2005)

    @Test fun film_titreDansLeDossierQuandLeNomEstGenerique() {
        movie("movie.mkv", folders = listOf("Films", "Titre (2019)"), title = "Titre", year = 2019)
        movie("video.mp4", folders = listOf("Mon.Film.2014.1080p"), title = "Mon Film", year = 2014)
    }

    @Test fun film_anneeDuDossierQuandLeNomEstLeMemeTitre() =
        movie("Titre.mkv", folders = listOf("Titre (2016)"), title = "Titre", year = 2016)

    @Test fun film_anneeDuDossierIgnoreeSiLeTitreDiffere() =
        movie("Autre chose.mkv", folders = listOf("Titre (2016)"), title = "Autre chose", year = null)

    @Test fun film_sansExtensionVideoReconnue() = movie("Titre (2019)", title = "Titre", year = 2019)

    @Test fun film_nomMajusculesEtChiffresFinaux() = movie("TITRE 2 (2012).mkv", title = "TITRE 2", year = 2012)

    // ───── Épisodes ─────

    @Test fun episode_S01E02() = episode("Série S01E02.mkv", series = "Série", season = 1, ep = 2)

    @Test fun episode_pointsMinusculesEtTitre() =
        episode("Show.Name.s02e10.The.Title.720p.HDTV.x264-GRP.mkv", series = "Show Name", season = 2, ep = 10, title = "The Title")

    @Test fun episode_forme1x02() = episode("Série 1x02.avi", series = "Série", season = 1, ep = 2)

    @Test fun episode_1x02AvecTitre() = episode("Série - 3x07 - Le titre.mkv", series = "Série", season = 3, ep = 7, title = "Le titre")

    @Test fun episode_seasonEpisodeEnToutesLettres() =
        episode("Série Season 2 Episode 5.mkv", series = "Série", season = 2, ep = 5)

    @Test fun episode_saisonFrancaise() = episode("Série Saison 1 Episode 12.mkv", series = "Série", season = 1, ep = 12)

    @Test fun episode_S01E02AvecAnneeDeLaSerie() =
        episode("Série (2019) S01E02.mkv", series = "Série", season = 1, ep = 2, year = 2019)

    @Test fun episode_pointsAnneeEtMarque() =
        episode("Show.Name.2019.S03E04.1080p.mkv", series = "Show Name", season = 3, ep = 4, year = 2019)

    @Test fun episode_doubleEpisodePrendLePremier() = episode("Série S01E01E02.mkv", series = "Série", season = 1, ep = 1)

    @Test fun episode_sansTitreDeSerieDansLeNomLeDossierLaDonne() =
        episode("S01E03.mkv", folders = listOf("Ma Série", "Saison 1"), series = "Ma Série", season = 1, ep = 3)

    @Test fun episode_dossierSaisonNumeroEnTete() =
        episode("02 - Le titre.mkv", folders = listOf("Série", "Saison 3"), series = "Série", season = 3, ep = 2, title = "Le titre")

    @Test fun episode_dossierSeasonAvecE02() =
        episode("E05.mkv", folders = listOf("Show (2012)", "Season 02"), series = "Show", season = 2, ep = 5, year = 2012)

    @Test fun episode_dossierSaisonAbregeS1() =
        episode("07.mp4", folders = listOf("Série", "S1"), series = "Série", season = 1, ep = 7)

    @Test fun episode_resolutionNEstPasUneMarque() {
        // « 1920x1080 » : 4 chiffres, pas un 1x02.
        movie("Titre 1920x1080 (2019).mkv", title = "Titre 1920x1080", year = 2019)
    }

    @Test fun episode_marqueCollee() = episode("serie.s01e02.mkv", series = "serie", season = 1, ep = 2)

    // ───── Fichiers et dossiers ignorés ─────

    @Test fun extensionsVideo() {
        assertTrue(NasNames.isVideo("a.MKV"))
        assertTrue(NasNames.isVideo("a.m2ts"))
        assertFalse(NasNames.isVideo("a.srt"))
        assertFalse(NasNames.isVideo("a.jpg"))
        assertFalse(NasNames.isVideo("mkv"))
    }

    @Test fun fichiersIgnores() {
        assertTrue(NasNames.isIgnored("sample.mkv"))
        assertTrue(NasNames.isIgnored("Titre-sample.mkv"))
        assertTrue(NasNames.isIgnored("Titre.Sample.mp4"))
        assertTrue(NasNames.isIgnored("Film-trailer.mp4"))
        assertTrue(NasNames.isIgnored("._Titre.mkv"))
        assertFalse(NasNames.isIgnored("Samplers (2015).mkv"))
        assertFalse(NasNames.isIgnored("Titre (2019).mkv"))
    }

    @Test fun dossiersIgnores() {
        assertTrue(NasNames.isIgnoredFolder("@eaDir"))
        assertTrue(NasNames.isIgnoredFolder("#recycle"))
        assertTrue(NasNames.isIgnoredFolder("\$RECYCLE.BIN"))
        assertTrue(NasNames.isIgnoredFolder(".Trash"))
        assertTrue(NasNames.isIgnoredFolder("Extras"))
        assertFalse(NasNames.isIgnoredFolder("Films"))
    }

    @Test fun cleSerie_ignoreAccentsPonctuationEtCasse() {
        assertEquals(NasNames.seriesKey("La Casa de Papel"), NasNames.seriesKey("la.casa.de.papel"))
        assertEquals(NasNames.seriesKey("Élite"), NasNames.seriesKey("elite"))
    }
}

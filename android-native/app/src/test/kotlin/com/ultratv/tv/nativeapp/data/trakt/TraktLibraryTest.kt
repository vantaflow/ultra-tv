package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.data.db.CatalogLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import java.io.File

class TraktLibraryTest {
    private fun movie(title: String, year: Int?, vararg keys: String) = TraktItem(false, null, year, title, keys.toList())
    private fun show(title: String, year: Int?, vararg keys: String) = TraktItem(true, null, year, title, keys.toList())

    private fun index(wanted: Set<String>, vararg entries: CatalogLite): Map<String, List<CatalogLite>> {
        val m = HashMap<String, MutableList<CatalogLite>>()
        TraktAvailability.indexInto(m, wanted, entries.toList())
        return m
    }

    @Test
    fun resolve_elementAbsentDuCatalogue_estExclu() {
        val items = listOf(movie("Alien", 1979, "alien"), movie("Dune", 2021, "dune"))
        val movies = index(setOf("alien", "dune"), CatalogLite(1, "FR - Alien (1979) 4K", "p", 1979, 8.0))
        val out = TraktAvailability.resolve(items, movies, emptyMap())
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun resolve_ordreDeTrakt_estConserve() {
        val items = listOf(movie("B", 2000, "b"), show("A", 2010, "a"), movie("C", 2005, "c"))
        val movies = index(setOf("b", "c"), CatalogLite(1, "C", null, 2005, null), CatalogLite(2, "B", null, 2000, null))
        val series = index(setOf("a"), CatalogLite(9, "A", null, 2010, null))
        val out = TraktAvailability.resolve(items, movies, series)
        assertEquals(listOf("B", "A", "C"), out.map { it.title })
        assertEquals(listOf(false, true, false), out.map { it.isShow })
    }

    @Test
    fun resolve_toleranceAnnee_unAnOkDeuxAnsRefuse() {
        val items = listOf(movie("Alien", 1979, "alien"))
        assertEquals(1, TraktAvailability.resolve(items, index(setOf("alien"), CatalogLite(1, "Alien", null, 1980, null)), emptyMap()).size)
        assertEquals(0, TraktAvailability.resolve(items, index(setOf("alien"), CatalogLite(1, "Alien", null, 1981, null)), emptyMap()).size)
        // Année inconnue côté catalogue : accepté.
        assertEquals(1, TraktAvailability.resolve(items, index(setOf("alien"), CatalogLite(1, "Alien", null, null, null)), emptyMap()).size)
    }

    @Test
    fun resolve_plusieursCandidats_preferAnneeExacte() {
        val items = listOf(movie("Dune", 1984, "dune"))
        val movies = index(setOf("dune"), CatalogLite(1, "Dune", null, 1985, null), CatalogLite(2, "Dune", null, 1984, null))
        assertEquals(2L, TraktAvailability.resolve(items, movies, emptyMap()).single().id)
    }

    @Test
    fun resolve_memeEntreeDeuxFois_estDedoublonnee() {
        // Deux éléments Trakt (clés communes) ne doivent pas produire deux cartes pour la même entrée du catalogue.
        val items = listOf(movie("Alien", 1979, "alien"), movie("Alien (remaster)", 1979, "alien"))
        val movies = index(setOf("alien"), CatalogLite(1, "Alien", null, 1979, null))
        assertEquals(1, TraktAvailability.resolve(items, movies, emptyMap()).size)
    }

    @Test
    fun resolve_filmEtSerieMemeTitre_nontPasLeMemeIndex() {
        val items = listOf(show("Fargo", 2014, "fargo"))
        val movies = index(setOf("fargo"), CatalogLite(1, "Fargo", null, 1996, null))
        assertTrue(TraktAvailability.resolve(items, movies, emptyMap()).isEmpty())
    }

    @Test
    fun indexInto_ignoreLesCleNonDemandees() {
        val m = index(setOf("alien"), CatalogLite(1, "Alien", null, 1979, null), CatalogLite(2, "Predator", null, 1987, null))
        assertEquals(setOf("alien"), m.keys)
    }

    @Test
    fun wantedKeys_separeFilmsEtSeries() {
        val lists = listOf(listOf(movie("A", 1, "a1", "a2")), listOf(show("B", 2, "b")))
        assertEquals(setOf("a1", "a2"), TraktAvailability.wantedKeys(lists, shows = false))
        assertEquals(setOf("b"), TraktAvailability.wantedKeys(lists, shows = true))
    }

    private val json = """
        {"linked":true,"updatedAt":123,
         "watchlist":[{"type":"movie","tmdb":348,"year":1979,"title":"Alien","keys":["alien"]}],
         "recommendations":[{"type":"show","tmdb":null,"year":null,"title":"Fargo","keys":["fargo"]}],
         "watched":{"movies":[{"type":"movie","tmdb":1,"year":2021,"title":"Dune","keys":["dune"]}],
                    "shows":[{"type":"show","tmdb":2,"year":2008,"title":"Breaking Bad","keys":["breaking bad"],"episodes":["1x1","1x2","2x1"]}]}}
    """.trimIndent()

    @Test
    fun parse_reponseLiee_litToutesLesListes() {
        val lib = TraktLibrary.parse(json)
        assertTrue(lib.linked)
        assertEquals(123L, lib.updatedAt)
        assertEquals("Alien", lib.watchlist.single().title)
        assertEquals(null, lib.recommendations.single().tmdb)
        assertTrue(lib.recommendations.single().isShow)
    }

    @Test
    fun parse_nonLie_donneBibliothequeVide() {
        val lib = TraktLibrary.parse("""{"linked":false}""")
        assertFalse(lib.linked)
        assertTrue(lib.watchlist.isEmpty() && lib.recommendations.isEmpty())
    }

    @Test
    fun watchedEpisodes_serieConnue_renvoieLesSxE() {
        val lib = TraktLibrary.parse(json)
        assertEquals(setOf("1x1", "1x2", "2x1"), lib.watchedEpisodes("Breaking Bad", 2008))
        assertTrue(TraktLibrary.episodeKey(1, 2) in lib.watchedEpisodes("Breaking Bad (2008) HD", null))
    }

    @Test
    fun watchedEpisodes_serieInconnue_ouAnneeIncompatible_estVide() {
        val lib = TraktLibrary.parse(json)
        assertTrue(lib.watchedEpisodes("Fargo", 2014).isEmpty())
        assertTrue(lib.watchedEpisodes("Breaking Bad", 1990).isEmpty())
    }

    @Test
    fun isMovieWatched_filmVu_ouNon() {
        val lib = TraktLibrary.parse(json)
        assertTrue(lib.isMovieWatched("FR - Dune 4K", 2021))
        assertFalse(lib.isMovieWatched("Dune", 1984))
        assertFalse(lib.isMovieWatched("Alien", 1979))
        assertFalse(TraktLibrary.EMPTY.isMovieWatched("Dune", 2021))
    }

    @Test
    fun parse_trendingEtPopular_sontLus() {
        val lib = TraktLibrary.parse(
            """{"linked":true,"watchlist":[],"recommendations":[],"watched":{"movies":[],"shows":[]},
            "trending":[{"type":"movie","tmdb":1,"year":2020,"title":"A","keys":["a"]},{"type":"show","tmdb":2,"year":2019,"title":"B","keys":["b"]}],
            "popular":[{"type":"show","title":"C","keys":["c","c2"]}]}""",
        )
        assertEquals(listOf("A", "B"), lib.trending.map { it.title })
        assertEquals(listOf(false, true), lib.trending.map { it.isShow })
        assertEquals(listOf("c", "c2"), lib.popular.single().keys)
    }

    @Test
    fun parse_trendingEtPopularAbsents_donnentDesListesVides() {
        val lib = TraktLibrary.parse("""{"linked":true,"watchlist":[],"recommendations":[]}""")
        assertTrue(lib.trending.isEmpty())
        assertTrue(lib.popular.isEmpty())
    }

    @Test
    fun rows_tendancesEtPopulaires_neGardentQueLeDisponible() {
        val lib = TraktLibrary(
            true, 1, emptyList(), emptyList(), emptyList(), emptyList(),
            trending = listOf(movie("Alien", 1979, "alien"), show("Absent", 2000, "absent"), show("Lost", 2004, "lost")),
            popular = listOf(movie("Dune", 2021, "dune"), movie("Nope", 1999, "nope")),
        )
        val movies = index(setOf("alien", "dune", "nope"), CatalogLite(1, "FR - Alien (1979)", "p", 1979, 8.0), CatalogLite(2, "Dune", null, 2021, null))
        val series = index(setOf("absent", "lost"), CatalogLite(7, "Lost", null, 2004, null))
        val rows = TraktAvailability.rows(lib, movies, series)
        assertEquals(listOf(1L, 7L), rows.trending.map { it.id })
        assertEquals(listOf(2L), rows.popular.map { it.id })
        assertTrue(rows.watchlist.isEmpty() && rows.recommendations.isEmpty())
        assertFalse(rows.isEmpty)
        assertTrue(TraktAvailability.rows(lib, emptyMap(), emptyMap()).isEmpty)
    }

    @Test
    fun rowsCodec_aller_retour_conserveTout() {
        val c = TraktCard(true, 42, "Titre é", "http://x/p.jpg", 2020, 7.5)
        val bare = TraktCard(false, 1, "Sans rien", null, null, null)
        val rows = TraktRows(listOf(c), listOf(bare), listOf(c, bare), emptyList())
        val back = TraktRowsCodec.decode(TraktRowsCodec.encode(mapOf(3L to rows, 9L to TraktRows.EMPTY)))
        assertEquals(rows, back[3L])
        assertEquals(TraktRows.EMPTY, back[9L])
        assertTrue(TraktRowsCodec.decode("pas du json").isEmpty())
        assertTrue(TraktRowsCodec.decode(null).isEmpty())
    }

    // Titres de test variés : préfixes, qualité, accents, article, année, « & », chiffres, non latin.
    private val sampleTitles = listOf(
        "FR - Alien (1979) 4K", "|FR| The Dune", "[VOD] Le Père Noël est une ordure", "L'Étrange Noël de Monsieur Jack", "Amélie 2001",
        "Fast & Furious", "WALL·E", "Œdipe roi", "Ça", "Se7en", "2012", "Les Misérables HD", "  ", "", "ÉCOLE VF", "Ünïcödé Ştrange",
        "الفيل الأزرق", "Αθήνα Σ", "Spider-Man: No Way Home (2021)", "UHD - the lord of the rings", "An American Tail", "El Camino 1080p",
    )

    @Test
    fun indexInto_ecremagePremierMot_donneLeMemeIndexQueLaRegleComplete() {
        val entries = sampleTitles.mapIndexed { i, t -> CatalogLite(i.toLong(), t, null, null, null) }
        val vectorKeys = File("../../cloudflare-config/test/fixtures/trakt-match-vectors.json").let { f ->
            val a = JSONObject(f.readText()).getJSONArray("keys")
            List(a.length()) { a.getJSONArray(it).getString(0) to a.getJSONArray(it).getString(1) }
        }
        val all = entries + vectorKeys.mapIndexed { i, (raw, _) -> CatalogLite(1000L + i, raw, null, null, null) }
        val wantedAll = all.map { TraktMatch.matchKey(it.title) }.filter { it.isNotEmpty() }.toSet() + setOf("zzz inconnu", "alien")
        // Plusieurs jeux de clés recherchées : tout, une moitié, un seul, aucun.
        val sets = listOf(wantedAll, wantedAll.filterIndexed { i, _ -> i % 2 == 0 }.toSet(), setOf("alien"), setOf("dune"), setOf("and furious"), emptySet())
        for (w in sets) {
            val full = HashMap<String, MutableList<CatalogLite>>().also { TraktAvailability.indexInto(it, w, all, null) }
            val fast = HashMap<String, MutableList<CatalogLite>>().also { TraktAvailability.indexInto(it, w, all) }
            assertEquals("wanted=$w", full, fast)
        }
        // Aucun faux négatif titre par titre : toute clé recherchée trouvée par la règle complète passe l'écrémage.
        val fw = TraktAvailability.firstWordsOf(wantedAll)
        for (e in all) {
            val k = TraktMatch.matchKey(e.title)
            if (k.isNotEmpty() && k in wantedAll) assertTrue("« ${e.title} » → « $k »", TraktAvailability.mayMatch(e.title, fw))
        }
    }

    @Test
    fun mayMatch_motInconnu_ecarteSansCalculerLaCle() {
        val fw = TraktAvailability.firstWordsOf(setOf("alien", "the thing".removePrefix("the ")))
        assertTrue(TraktAvailability.mayMatch("FR - ALIEN (1979)", fw))
        assertTrue(TraktAvailability.mayMatch("Alién HD", fw))
        assertFalse(TraktAvailability.mayMatch("Predator 1987", fw))
        assertFalse(TraktAvailability.mayMatch("anything", emptySet()))
    }
}

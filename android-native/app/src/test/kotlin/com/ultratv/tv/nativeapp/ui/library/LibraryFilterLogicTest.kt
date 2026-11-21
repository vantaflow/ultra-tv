package com.ultratv.tv.nativeapp.ui.library

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterLogicTest {
    private fun m(id: Long) = Tile(false, id, "Film $id", null, 0f)
    private fun s(id: Long) = Tile(true, id, "Série $id", null, 0f)
    private fun row(id: Long, kind: String, movies: Int, series: Int, shelves: List<Shelf>, name: String = "Srv$id") =
        LibraryRow(ProviderEntity(id = id, name = name, kind = kind, baseUrl = "http://x", username = "", password = ""), MediaLibraryLogic.Status.ONLINE, movies, series, 0, shelves)
    private val plex = row(1, "PLEX", 3, 2, listOf(
        Shelf(ShelfKind.RESUME, "", listOf(m(1), s(2)), null), Shelf(ShelfKind.RECENT, "", listOf(m(1), s(1)), null),
        Shelf(ShelfKind.MOVIES, "", listOf(m(1), m(2)), false), Shelf(ShelfKind.SERIES, "", listOf(s(1), s(2)), true),
        Shelf(ShelfKind.LIBRARY, "Docs", listOf(m(3)), false, "7"), Shelf(ShelfKind.LIBRARY, "Docs", listOf(s(3)), true, "8"),
    ))
    private val nas = row(2, "NAS", 4, 0, listOf(Shelf(ShelfKind.MOVIES, "", listOf(m(10)), false)))
    private val jelly = row(3, "JELLYFIN", 0, 5, listOf(Shelf(ShelfKind.SERIES, "", listOf(s(20)), true)))

    @Test fun listeVide_aucunFiltre_pasDeMessage() {
        val v = LibraryFilterLogic.apply(emptyList(), LibraryFilter(LibraryType.MOVIES, 9))
        assertTrue(v.types.isEmpty() && v.sources.isEmpty() && v.rows.isEmpty()); assertNull(v.empty)
        assertEquals(LibraryFilter(), v.filter)
    }

    @Test fun uneSeuleSource_pasDeFiltreDeSource_etUnSeulType_pasDeFiltreDeType() {
        val v = LibraryFilterLogic.apply(listOf(nas), LibraryFilter())
        assertTrue(v.sources.isEmpty()); assertTrue(v.types.isEmpty())
        val both = LibraryFilterLogic.apply(listOf(plex), LibraryFilter())
        assertTrue(both.sources.isEmpty()); assertEquals(listOf(LibraryType.ALL, LibraryType.MOVIES, LibraryType.SERIES), both.types)
    }

    @Test fun plusieursSources_uneEntreeParBibliotheque_enOrdre() {
        val v = LibraryFilterLogic.apply(listOf(plex, nas, jelly), LibraryFilter())
        assertEquals(listOf(1L, 2L, 3L), v.sources.map { it.id })
        assertEquals(listOf("Srv1 · Plex", "Srv2 · NAS", "Srv3 · Jellyfin"), v.sources.map(LibraryFilterLogic::sourceLabel))
        assertEquals("Plex salon", LibraryFilterLogic.sourceLabel(SourceOption(1, "Plex salon", "PLEX")))
    }

    @Test fun typeFilms_gardeLesFilms_partout() {
        val v = LibraryFilterLogic.apply(listOf(plex, nas, jelly), LibraryFilter(LibraryType.MOVIES))
        assertEquals(listOf(1L, 2L), v.rows.map { it.provider.id }) // Jellyfin n'a que des séries
        val shelves = v.rows.first().shelves
        assertEquals(listOf(ShelfKind.RESUME, ShelfKind.RECENT, ShelfKind.MOVIES, ShelfKind.LIBRARY), shelves.map { it.kind })
        assertTrue(shelves.flatMap { it.tiles }.none { it.isSeries })
        assertEquals("7", shelves.last().libraryId)
        assertNull(v.empty)
    }

    @Test fun reprendre_respecteLeFiltre() {
        val v = LibraryFilterLogic.apply(listOf(plex, nas), LibraryFilter(LibraryType.SERIES))
        val resume = v.rows.single().shelves.first { it.kind == ShelfKind.RESUME }
        assertEquals(listOf(2L), resume.tiles.map { it.id })
    }

    @Test fun combinaison_typeEtSource() {
        val v = LibraryFilterLogic.apply(listOf(plex, nas, jelly), LibraryFilter(LibraryType.SERIES, 1))
        assertEquals(listOf(1L), v.rows.map { it.provider.id })
        assertTrue(v.rows.single().shelves.none { it.kind == ShelfKind.MOVIES })
        assertEquals(LibraryFilter(LibraryType.SERIES, 1), v.filter)
    }

    @Test fun etatsVides_messageSelonLeFiltre() {
        val rows = listOf(plex, nas, jelly)
        assertEquals(LibraryEmpty.SERIES_IN_SOURCE, LibraryFilterLogic.apply(rows, LibraryFilter(LibraryType.SERIES, 2)).empty) // NAS sans série
        assertEquals(LibraryEmpty.MOVIES_IN_SOURCE, LibraryFilterLogic.apply(rows, LibraryFilter(LibraryType.MOVIES, 3)).empty)
        // Un seul type existant : le filtre de type est masqué, une demande « Séries » retombe sur « Tout » sans message.
        val moviesOnly = LibraryFilterLogic.apply(listOf(nas, row(6, "EMBY", 2, 0, listOf(Shelf(ShelfKind.MOVIES, "", listOf(m(30)), false)))), LibraryFilter(LibraryType.SERIES))
        assertNull(moviesOnly.empty); assertEquals(LibraryType.ALL, moviesOnly.filter.type)
        val emptySource = row(5, "EMBY", 0, 0, emptyList())
        assertEquals(LibraryEmpty.SOURCE, LibraryFilterLogic.apply(listOf(plex, emptySource), LibraryFilter(LibraryType.ALL, 5)).empty)
        assertTrue(LibraryFilterLogic.apply(listOf(plex, emptySource), LibraryFilter(LibraryType.ALL, 5)).rows.isEmpty())
        // Sans filtre : jamais de message (comportement d'avant).
        assertNull(LibraryFilterLogic.apply(listOf(plex, emptySource), LibraryFilter()).empty)
    }

    @Test fun choixMemoriseInexistant_retombeSurTout() {
        val v = LibraryFilterLogic.apply(listOf(plex, nas), LibraryFilter(LibraryType.SERIES, 99))
        assertNull(v.filter.sourceId)
        val one = LibraryFilterLogic.apply(listOf(nas), LibraryFilter(LibraryType.MOVIES, 2)) // un seul type, une seule source
        assertEquals(LibraryFilter(), one.filter)
        assertEquals(1, one.rows.size)
    }

    @Test fun typeInconnu_gardeUniquementSousTout_etAucuneCleDupliquee() {
        val odd = row(4, "PLEX", 1, 1, listOf(Shelf(ShelfKind.LIBRARY, "?", listOf(m(1)), null), Shelf(ShelfKind.RECENT, "", listOf(m(1), m(1), s(1)), null)))
        assertEquals(2, LibraryFilterLogic.shelves(odd.shelves, LibraryType.ALL).size)
        val films = LibraryFilterLogic.shelves(odd.shelves, LibraryType.MOVIES)
        assertEquals(1, films.size) // la bibliothèque de type inconnu est écartée
        assertEquals(listOf(1L), films.single().tiles.map { it.id }) // doublon retiré
    }

    @Test fun memoire_gardeLeDernierChoix() {
        val saved = LibraryFilterMemory.last
        try { LibraryFilterMemory.last = LibraryFilter(LibraryType.MOVIES, 2); assertEquals(LibraryFilter(LibraryType.MOVIES, 2), LibraryFilterMemory.last) } finally { LibraryFilterMemory.last = saved }
    }
}

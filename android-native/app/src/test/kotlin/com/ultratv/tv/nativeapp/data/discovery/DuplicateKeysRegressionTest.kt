package com.ultratv.tv.nativeapp.data.discovery

import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.Tile
import com.ultratv.tv.nativeapp.data.sport.LeagueParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** Listes dont la clé d'affichage vient des données : un doublon en entrée ne doit jamais atteindre l'écran (plantage Compose). */
class DuplicateKeysRegressionTest {
    @Test fun `reprendre - deux episodes entames d une meme serie ne donnent qu une tuile`() {
        val started = listOf(Tile(true, 7, "Série", null, 0.4f), Tile(false, 7, "Film", null, 0.2f), Tile(true, 7, "Série", null, 0.1f), Tile(true, 8, "Autre", null, 0.5f))
        val out = MediaLibraryLogic.resumeTiles(started, 10)
        assertEquals(listOf(true to 7L, false to 7L, true to 8L), out.map { it.isSeries to it.id })
        assertEquals(0.4f, out.first().progress, 0f) // l'épisode le plus récent l'emporte
    }

    @Test fun `reprendre - le plafond s applique apres le dedoublonnage`() {
        val started = listOf(Tile(true, 1, "A", null, 0f), Tile(true, 1, "A", null, 0f), Tile(true, 2, "B", null, 0f))
        assertEquals(listOf(1L, 2L), MediaLibraryLogic.resumeTiles(started, 2).map { it.id })
    }

    @Test fun `classement - une ligne repetee par le serveur n apparait qu une fois`() {
        val json = """{"rows":[{"rk":1,"team":"A","pts":9},{"rk":1,"team":"A","pts":9},{"rk":2,"team":"B","pts":6},{"rk":1,"team":"A","g":"B","pts":3}]}"""
        val rows = LeagueParser.table(json)!!
        assertEquals(listOf(Triple(null, 1, "A"), Triple(null, 2, "B"), Triple("B", 1, "A")), rows.map { Triple(it.group, it.rank, it.team) })
    }
}

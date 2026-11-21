package com.ultratv.tv.nativeapp.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.Tile
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** « Mes bibliothèques » : pastilles Type / Source, filtrage, focus à la télécommande, états vides. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryHubFiltersUiTest {
    @get:Rule val rule = createComposeRule()

    private fun m(id: Long) = Tile(false, id, "Film $id", null, 0f)
    private fun s(id: Long) = Tile(true, id, "Serie $id", null, 0f)
    private fun row(id: Long, kind: String, name: String, movies: Int, series: Int, shelves: List<Shelf>) =
        LibraryRow(ProviderEntity(id = id, name = name, kind = kind, baseUrl = "http://x", username = "", password = ""), MediaLibraryLogic.Status.ONLINE, movies, series, 0, shelves)

    private val plex = row(1, "PLEX", "Salon", 2, 2, listOf(
        Shelf(ShelfKind.MOVIES, "", listOf(m(1), m(2)), false), Shelf(ShelfKind.SERIES, "", listOf(s(1), s(2)), true)))
    private val nas = row(2, "NAS", "Disque", 1, 0, listOf(Shelf(ShelfKind.MOVIES, "", listOf(m(10)), false)))
    private val jelly = row(3, "JELLYFIN", "Cave", 0, 1, listOf(Shelf(ShelfKind.SERIES, "", listOf(s(20)), true)))

    private fun show(rows: List<LibraryRow>) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = AppLang.English
        LibraryFilterMemory.last = LibraryFilter()
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                var f by remember { mutableStateOf(LibraryFilter()) }
                val v = remember(f) { LibraryFilterLogic.apply(rows, f) }
                Column(Modifier.fillMaxSize()) {
                    LibraryHubContent(v, false, { f = f.copy(type = it) }, { f = f.copy(sourceId = it) }, {}, {}, { _, _, _ -> }, {})
                }
            }
        }
        rule.waitForIdle()
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun focus(tag: String) { rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle() }
    private fun tiles() = rule.onAllNodesWithTag("library-tile").fetchSemanticsNodes().size

    @Test fun pastilles_presentesSelonLesDonnees() {
        show(listOf(plex, nas, jelly))
        rule.onNodeWithTag("library-filter-type").assertExists()
        for (t in listOf("ALL", "MOVIES", "SERIES")) rule.onNodeWithTag("chip-type-$t").assertExists()
        rule.onNodeWithTag("library-filter-source").assertExists()
        rule.onNodeWithText("All sources").assertExists()
        rule.onNodeWithText("Salon · Plex").assertExists(); rule.onNodeWithText("Disque · NAS").assertExists(); rule.onNodeWithText("Cave · Jellyfin").assertExists()
    }

    @Test fun uneSeuleSourceEtUnSeulType_aucuneBarre() {
        show(listOf(nas))
        rule.onNodeWithTag("library-filter-type").assertDoesNotExist()
        rule.onNodeWithTag("library-filter-source").assertDoesNotExist()
        assertEquals(1, tiles())
    }

    @Test fun filtrage_effectif_etFocusConserveSurLaPastilleChoisie() {
        show(listOf(plex, nas, jelly))
        val before = tiles()
        focus("chip-type-MOVIES"); press(Key.Enter)
        rule.onNodeWithTag("chip-type-MOVIES").assertIsFocused()
        assertEquals(3, tiles()) // Film 1, 2 (Salon), Film 10 (Disque)
        assert(tiles() < before)
        focus("chip-source-2"); press(Key.Enter)
        rule.onNodeWithTag("chip-source-2").assertIsFocused()
        assertEquals(1, tiles())
        focus("chip-source-all"); press(Key.Enter)
        rule.onNodeWithTag("chip-source-all").assertIsFocused()
        assertEquals(3, tiles())
    }

    @Test fun haut_puisBas_revientSurLElementQuitte() {
        show(listOf(plex, nas, jelly))
        rule.onAllNodesWithTag("library-tile")[0].performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        rule.onAllNodesWithTag("library-tile")[0].assertIsFocused()
        press(Key.DirectionUp)
        // Une pastille de filtre a le focus (la rangée juste au-dessus du contenu : les sources).
        rule.onNodeWithTag("library-filter-source").assertExists()
        val onChip = listOf("chip-source-all", "chip-source-1", "chip-source-2", "chip-source-3").any { runCatching { rule.onNodeWithTag(it).assertIsFocused() }.isSuccess }
        assert(onChip) { "▲ depuis le contenu doit atteindre une pastille" }
        press(Key.DirectionDown)
        rule.onAllNodesWithTag("library-tile")[0].assertIsFocused()
    }

    @Test fun filtreSansResultat_afficheUnMessage() {
        show(listOf(plex, nas, jelly))
        focus("chip-type-MOVIES"); press(Key.Enter)
        focus("chip-source-3"); press(Key.Enter) // Cave : que des séries
        rule.onNodeWithText("No movie in this library").assertExists()
        rule.onNodeWithTag("library-content").assertDoesNotExist()
        rule.onNodeWithTag("chip-source-3").assertIsFocused() // le focus reste sur les pastilles
        focus("chip-source-all"); press(Key.Enter)
        rule.onNodeWithText("No movie in this library").assertDoesNotExist()
    }
}

package com.ultratv.tv.nativeapp.ui.live

import com.ultratv.tv.nativeapp.data.db.CategoryCount
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelGroupRow
import com.ultratv.tv.nativeapp.data.repo.CategoryRow
import com.ultratv.tv.nativeapp.ui.player.DrawerCategory
import com.ultratv.tv.nativeapp.ui.player.LiveDrawerLogic
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode
import com.ultratv.tv.nativeapp.ui.player.osd.OsdActionId
import com.ultratv.tv.nativeapp.ui.player.osd.OsdActionInputs
import com.ultratv.tv.nativeapp.ui.player.osd.OsdActions
import com.ultratv.tv.nativeapp.ui.player.osd.OsdLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Place des groupes personnels dans les listes : après Favoris dans le Direct, le Guide et le tiroir ; bouton du bandeau. */
class ChannelGroupListsTest {
    private fun cat(id: String, name: String) = CategoryEntity(providerId = 1, kind = "LIVE", remoteId = id, name = name)

    @Test fun direct_groupesApresFavoris_memeVides_avantTout() {
        val l = buildDirectCategories(
            listOf(cat("10", "Sport")), listOf(CategoryCount("10", 4)), favCount = 2, providerId = 1, hidden = emptySet(),
            groups = listOf(ChannelGroupRow(7, "Mes sports", 3), ChannelGroupRow(8, "Vide", 0)),
        )
        assertEquals(listOf(CATEGORY_FAVORITES, "__grp:7", "__grp:8", CATEGORY_ALL, "10"), l.map { it.id })
        assertTrue(l[1].group && l[2].group)
        assertEquals("Mes sports", l[1].name)
        assertEquals(0, l[2].count)
        // « Tout » ne compte pas les groupes (chaînes déjà comptées dans leurs catégories).
        assertEquals(4, l.first { it.id == CATEGORY_ALL }.count)
    }

    @Test fun ouverture_jamaisSurUnGroupe_parDefaut_maisDernierGroupeRepris() {
        val l = listOf(DirectCategory(CATEGORY_FAVORITES, null, 0), DirectCategory("__grp:7", "G", 3, group = true), DirectCategory(CATEGORY_ALL, null, 9), DirectCategory("10", "Sport", 9))
        assertEquals("10", pickLanding(l, null))
        assertEquals("__grp:7", pickLanding(l, "__grp:7"))
        assertEquals(null, requestedCategory(l, "__grp:7"))
    }

    @Test fun tiroirDuLecteur_groupesApresFavoris() {
        val out = LiveDrawerLogic.categories("Tout", "Favoris", 10, 2, listOf(CategoryRow("a", "A", null, 5, true, "", false)), listOf(DrawerCategory("__grp:3", "G", 1)))
        assertEquals(listOf(CATEGORY_ALL, CATEGORY_FAVORITES, "__grp:3", "a"), out.map { it.id })
    }

    private val labels = OsdLabels(
        play = "Play", pause = "Pause", audio = "Audio", subs = "Subs", aspect = "Picture", aspectNames = emptyMap(),
        channels = "Channels", history = "History", addFavorite = "Add", removeFavorite = "Remove", record = "Record", pip = "PiP", sleep = "Sleep", player = "Player",
        myList = "My list", movies = "Movies", series = "Series", recordings = "Recordings", settings = "Settings", groups = "Groups",
    )

    @Test fun bandeau_boutonGroupes_apresFavori_seulementSiDemande() {
        val base = OsdActionInputs(playing = true, tsActive = false, isFavorite = false, aspect = AspectMode.FIT, replayLabel = null, replayActive = false, sleepActive = false, pipSupported = false)
        assertFalse(OsdActions.build(base, labels).any { it.id == OsdActionId.GROUPS })
        val ids = OsdActions.build(base.copy(groups = true), labels).map { it.id }
        assertEquals(ids.indexOf(OsdActionId.FAVORITE) + 1, ids.indexOf(OsdActionId.GROUPS))
    }
}

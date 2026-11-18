package com.ultratv.tv.nativeapp.ui.components

import com.ultratv.tv.nativeapp.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Mes bibliothèques » est dans le menu principal dès qu'une source Jellyfin, Emby, Plex ou NAS existe. */
class LibrariesRailTest {
    @Test fun sansSourceMultimedia_pasDEntree() {
        assertFalse(hasLibrarySource(listOf("XTREAM", "M3U")))
        assertEquals(railItems.map { it.route }, railItemsFor(false, false).map { it.route })
    }

    @Test fun avecPlexJellyfinEmbyOuNas_entreeApresFavoris() {
        for (k in listOf("PLEX", "JELLYFIN", "EMBY", "NAS")) assertTrue(k, hasLibrarySource(listOf("XTREAM", k)))
        val routes = railItemsFor(false, true).map { it.route }
        assertEquals(1, routes.count { it == Routes.LIBRARIES })
        assertEquals(routes.indexOf("favorites") + 1, routes.indexOf(Routes.LIBRARIES))
        assertTrue(isSelected(Routes.LIBRARIES, Routes.LIBRARIES))
    }
}

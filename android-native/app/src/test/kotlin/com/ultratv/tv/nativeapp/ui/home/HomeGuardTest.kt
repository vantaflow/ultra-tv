package com.ultratv.tv.nativeapp.ui.home

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rangées de l'accueil (Top 10, mieux notés…) : jamais de contenu masqué, verrouillé, désactivé ou adulte. */
class HomeGuardTest {
    private fun cat(kind: String, id: String, name: String, locked: Boolean = false, enabled: Boolean = true) =
        CategoryEntity(providerId = 1, kind = kind, remoteId = id, name = name, locked = locked, enabled = enabled)

    private val cats = listOf(
        cat("MOVIE", "10", "Action"),
        cat("MOVIE", "11", "XXX Adult"),            // adulte par son nom, ni masquée ni verrouillée (pas de code défini)
        cat("MOVIE", "12", "Famille", locked = true),
        cat("MOVIE", "13", "Docs", enabled = false),
        cat("SERIES", "20", "Drame"),
        cat("LIVE", "30", "Sport"),
        cat("LIVE", "31", "Nuit", locked = true),
    )

    @Test fun categorieMasqueeParLeProfil_estExclue() {
        val g = HomeGuard.of(setOf("SERIES:1:20"), cats, emptySet())
        assertFalse(g.allows("SERIES", 1, "20", "Une série"))
        assertTrue(g.allows("MOVIE", 1, "10", "Un film"))
    }

    @Test fun categoriesVerrouilleesDesactiveesEtAdultes_sontExclues() {
        val g = HomeGuard.of(emptySet(), cats, emptySet())
        assertFalse("adulte par le nom", g.allows("MOVIE", 1, "11", "Film"))
        assertFalse("verrouillée", g.allows("MOVIE", 1, "12", "Film"))
        assertFalse("désactivée", g.allows("MOVIE", 1, "13", "Film"))
        assertTrue(g.allows("MOVIE", 1, "10", "Film"))
    }

    @Test fun memeIdentifiantDansUneAutreSourceOuUnAutreType_nEstPasConfondu() {
        val g = HomeGuard.of(emptySet(), cats, emptySet())
        assertTrue(g.allows("MOVIE", 2, "12", "Film"))
        assertTrue(g.allows("SERIES", 1, "12", "Série"))
    }

    @Test fun titreAdulte_estExcluMemeDansUneCategorieOrdinaire() {
        val g = HomeGuard.of(emptySet(), cats, emptySet())
        assertFalse(g.allows("MOVIE", 1, "10", "XXX Hot night"))
        assertTrue("« Heroes » n'est pas adulte", g.allows("MOVIE", 1, "10", "Heroes"))
    }

    @Test fun chainesVerrouilleesOuDeCategorieVerrouillee_sontExclues() {
        val g = HomeGuard.of(emptySet(), cats, setOf("1:500"))
        fun ch(rid: String, cat: String) = ChannelEntity(providerId = 1, remoteId = rid, name = "Chaine $rid", logo = null, streamUrl = "u", categoryId = cat)
        assertFalse(g.allowsChannel(ch("500", "30")))
        assertFalse(g.allowsChannel(ch("501", "31")))
        assertTrue(g.allowsChannel(ch("502", "30")))
    }

    @Test fun sansCategorieConnue_seulLeTitreCompte() {
        assertEquals(true, HomeGuard.NONE.allows("MOVIE", 1, null, "Film"))
        assertEquals(false, HomeGuard.NONE.allows("MOVIE", 1, null, "Porn"))
    }
}

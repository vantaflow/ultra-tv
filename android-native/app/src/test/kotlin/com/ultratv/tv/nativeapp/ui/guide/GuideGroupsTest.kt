package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.db.CategoryCount
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.profile.ProfilePrefs
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
import com.ultratv.tv.nativeapp.ui.live.DirectCategory
import com.ultratv.tv.nativeapp.ui.live.buildDirectCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideGroupsTest {
    private val fav = DirectCategory(CATEGORY_FAVORITES, null, 0)
    private val all = DirectCategory(CATEGORY_ALL, null, 60)
    private val fr = DirectCategory("10", "FRI FRANCE", 40)
    private val uk = DirectCategory("11", "UK", 20)
    private val groups = listOf(fav, all, fr, uk)

    // ── Choix par défaut ──
    @Test fun defaut_groupeMemorise() = assertEquals("11", resolveGuideGroup("11", "10", groups))
    @Test fun defaut_sansMemoire_groupeDuDirect() = assertEquals("10", resolveGuideGroup(null, "10", groups))
    @Test fun defaut_sansRien_estTout() = assertEquals(CATEGORY_ALL, resolveGuideGroup(null, null, groups))
    @Test fun defaut_groupeMemoriseDisparu_retombeSurLeDirect() = assertEquals("10", resolveGuideGroup("99", "10", groups))
    @Test fun defaut_directDisparu_estTout() = assertEquals(CATEGORY_ALL, resolveGuideGroup("99", "98", groups))
    @Test fun defaut_toutMemorise_resteTout() = assertEquals(CATEGORY_ALL, resolveGuideGroup(CATEGORY_ALL, "10", groups))
    @Test fun defaut_favorisMemorises_restentFavorisMemeVides() = assertEquals(CATEGORY_FAVORITES, resolveGuideGroup(CATEGORY_FAVORITES, null, groups))

    // ── Mémorisation par profil ──
    @Test fun memorisation_parProfil() {
        val papa = ProfilePrefs.fromMap(mapOf(ProfilePrefs.GUIDE_GROUP to "10"))
        val enfant = ProfilePrefs.fromMap(mapOf(ProfilePrefs.GUIDE_GROUP to "11"))
        val autre = ProfilePrefs.fromMap(emptyMap())
        assertEquals("10", papa.guideGroup); assertEquals("11", enfant.guideGroup); assertNull(autre.guideGroup)
        assertEquals("10", resolveGuideGroup(papa.guideGroup, null, groups))
        assertEquals("11", resolveGuideGroup(enfant.guideGroup, null, groups))
        assertEquals(CATEGORY_ALL, resolveGuideGroup(autre.guideGroup, null, groups))
    }
    @Test fun memorisation_valeurVideIgnoree() = assertNull(ProfilePrefs.fromMap(mapOf(ProfilePrefs.GUIDE_GROUP to "")).guideGroup)
    @Test fun memorisation_neTouchePasLesPreferencesGlobales() {
        val base = com.ultratv.tv.nativeapp.data.prefs.UserPrefs()
        assertEquals(base, ProfilePrefs(guideGroup = "10").applyTo(base))
    }

    // ── Liste des groupes ──
    private fun cat(id: String, name: String) = CategoryEntity(providerId = 1, kind = "LIVE", remoteId = id, name = name)

    @Test fun liste_favorisToutPuisCategoriesNonVidesHorsMasquees() {
        val cats = listOf(cat("10", "FRI FRANCE"), cat("11", "UK"), cat("12", "Vide"), cat("13", "Masquée"))
        val counts = listOf(CategoryCount("10", 40), CategoryCount("11", 20), CategoryCount("13", 5))
        val l = buildDirectCategories(cats, counts, favCount = 3, providerId = 1, hidden = setOf("LIVE:1:13", "LIVE:2:10"))
        assertEquals(listOf(CATEGORY_FAVORITES, CATEGORY_ALL, "10", "11"), l.map { it.id })
        assertEquals(3, l[0].count); assertEquals(60, l[1].count)
    }
    @Test fun liste_masquageAutreFournisseurSansEffet() {
        val l = buildDirectCategories(listOf(cat("10", "A")), listOf(CategoryCount("10", 4)), 0, 1, setOf("LIVE:2:10"))
        assertEquals(listOf(CATEGORY_FAVORITES, CATEGORY_ALL, "10"), l.map { it.id })
    }
    @Test fun options_libelleAvecCompteurEtUnique() {
        val g = listOf(fav, all, DirectCategory("10", "News", 5), DirectCategory("11", "News", 5))
        val o = guideGroupOptions(g) { it.name ?: it.id }
        assertEquals(4, o.map { it.second }.toSet().size)
        assertEquals("News (5)", o[2].second)
    }
    @Test fun nomCourt_inconnu_retombeSurTout() = assertEquals("T", guideGroupName(groups, "99") { if (it.id == CATEGORY_ALL) "T" else "x" })
}

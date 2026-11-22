package com.ultratv.tv.nativeapp.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Catégorie demandée par lien `ultratv://live?category=…` : jamais une catégorie verrouillée, vide ou inexistante. */
class RequestedCategoryTest {
    private val cats = listOf(
        DirectCategory(CATEGORY_FAVORITES, null, 3), DirectCategory(CATEGORY_ALL, null, 30),
        DirectCategory("10", "Sport", 12), DirectCategory("11", "Adultes", 5, locked = true), DirectCategory("12", "Vide", 0),
    )

    @Test fun categorie_visible_appliquee() = assertEquals("10", requestedCategory(cats, "10"))
    @Test fun categorie_verrouillee_refusee() = assertNull(requestedCategory(cats, "11"))
    @Test fun categorie_vide_ou_inconnue_refusee() { assertNull(requestedCategory(cats, "12")); assertNull(requestedCategory(cats, "99")) }
    @Test fun pseudo_categories_refusees() { assertNull(requestedCategory(cats, CATEGORY_ALL)); assertNull(requestedCategory(cats, CATEGORY_FAVORITES)) }
}

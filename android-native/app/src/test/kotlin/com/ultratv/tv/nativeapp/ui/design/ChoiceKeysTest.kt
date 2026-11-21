package com.ultratv.tv.nativeapp.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Clés de la boîte de choix. Deux options au même libellé (deux variantes « HD · HBO » d'une chaîne, deux profils
 * homonymes) faisaient planter la liste : « Key "HD · HBO" was already used ».
 */
class ChoiceKeysTest {
    @Test fun `libelles uniques - la cle reste le libelle`() {
        assertEquals(listOf("Auto", "4K", "HD"), choiceKeys(listOf("Auto", "4K", "HD")))
    }

    @Test fun `libelles en double - toutes les cles sont distinctes`() {
        val keys = choiceKeys(listOf("HD · HBO", "FHD · HBO", "HD · HBO", "HD · HBO"))
        assertEquals(4, keys.toSet().size)
        assertEquals("HD · HBO", keys[0])
        assertEquals("FHD · HBO", keys[1])
    }

    @Test fun `une cle departagee ne heurte pas un libelle existant`() {
        val first = choiceKeys(listOf("A", "A"))[1]
        val keys = choiceKeys(listOf("A", "A", first))
        assertEquals(3, keys.toSet().size)
    }

    @Test fun `liste vide`() {
        assertEquals(emptyList<String>(), choiceKeys(emptyList()))
    }
}

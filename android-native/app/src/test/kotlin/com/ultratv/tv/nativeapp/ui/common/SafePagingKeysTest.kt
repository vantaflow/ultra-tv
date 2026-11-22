package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * « Key "14100" was already used » (Chromecast HD, 1.3.72 ; déjà « Key "2143" » en 1.2.69) : clé de liste paginée
 * (Direct, Guide, catalogues). Les doublons étaient repérés sur l'instantané lu à la COMPOSITION, mais la clé lue à la
 * MESURE sur l'instantané courant : une page arrivée entre les deux (synchro, pagination par décalage de Room) apportait
 * un doublon non repéré. Les clés sont maintenant tirées d'un seul et même instantané, celui que la mesure lit.
 */
class SafePagingKeysTest {
    private data class Ch(val id: Long)

    @Test fun doublonApparuApresLaComposition_clesToujoursUniques() {
        val keys = SafePagingKeys<Ch> { it.id }
        val avant = listOf(Ch(14099), Ch(14100), null, null)
        avant.indices.forEach { keys.key(avant, it) }        // composition : aucun doublon
        val apres = listOf(Ch(14099), Ch(14100), Ch(14100), Ch(14101)) // page arrivée avant la mesure
        val k = apres.indices.map { keys.key(apres, it) }
        assertEquals(apres.size, k.toSet().size)
        assertEquals(14100L, k[1])                            // la première occurrence garde son identifiant (focus)
        assertEquals(14101L, k[3])
    }

    @Test fun placeholders_horsLimites_etDoublonsMultiples() {
        val keys = SafePagingKeys<Ch> { it.id }
        val s = listOf(null, Ch(1), Ch(1), null, Ch(1), Ch(2))
        val k = (s.indices + listOf(6, 7, -1)).map { keys.key(s, it) }
        assertEquals(k.size, k.toSet().size)
    }

    @Test fun cleStableTantQueLInstantaneNeChangePas() {
        val keys = SafePagingKeys<Ch> { it.id }
        val s = listOf(Ch(5), Ch(5), Ch(6))
        assertEquals(keys.key(s, 1), keys.key(s, 1))
        assertEquals(keys.key(s, 1), SafePagingKeys<Ch> { it.id }.key(s, 1))
    }
}

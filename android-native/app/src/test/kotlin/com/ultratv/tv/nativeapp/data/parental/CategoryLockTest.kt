package com.ultratv.tv.nativeapp.data.parental

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verrou parental PAR CATÉGORIE (retour utilisateur du 22/11) : une catégorie verrouillée n'est plus accessible sans code,
 * quelle que soit l'entrée (liste, Guide, recherche, accueil, lien profond → lecteur).
 */
class CategoryLockTest {
    private fun ch(rid: String, cat: String?) = ChannelEntity(providerId = 1, remoteId = rid, name = "Chaine $rid", logo = null, categoryId = cat, streamUrl = "http://x/$rid")

    private class Source(val keys: Set<String> = emptySet(), val cats: Set<String> = emptySet()) : LockSource {
        override suspend fun lockedChannelKeys() = keys
        override suspend fun lockedCategoryIds(providerId: Long) = if (providerId == 1L) cats else emptySet()
    }

    @Test fun isCategoryLocked_fonctionPure() {
        assertTrue(CategoryLocks.isCategoryLocked("X", setOf("X")))
        assertFalse(CategoryLocks.isCategoryLocked("Y", setOf("X")))
        assertFalse(CategoryLocks.isCategoryLocked(null, setOf("X")))
        assertFalse(CategoryLocks.isCategoryLocked("", setOf("")))
    }

    @Test fun entreeDuLecteur_chaineDUneCategorieVerrouillee_exigeLeCode() = runBlocking {
        val gate = ParentalGate(Source(cats = setOf("ADULT")))
        assertFalse("catégorie verrouillée", gate.admit(ch("1", "ADULT")))
        assertTrue("catégorie libre", gate.admit(ch("2", "NEWS")))
    }

    @Test fun entreeDuLecteur_codeDejaSaisiParLEcranDeDepart_neRedemandePas() = runBlocking {
        ParentalUnlocks.clear()
        val gate = ParentalGate(Source(cats = setOf("ADULT")))
        val c = ch("1", "ADULT")
        ParentalUnlocks.grant(c)
        assertTrue("code saisi juste avant (liste du Direct)", gate.admit(c))
        assertTrue("puis débloquée pour la session", gate.admit(c))
        gate.endSession()
        assertFalse("une seule fois : au lecteur suivant, le code est redemandé", gate.admit(c))
    }

    @Test fun codeAccepteAuLecteur_debloqueLaChainePourLaSession() = runBlocking {
        val gate = ParentalGate(Source(keys = setOf("1:9")))
        val c = ch("9", null)
        assertFalse(gate.admit(c))
        gate.markUnlocked(c)
        assertTrue(gate.admit(c))
        assertNull(gate.pending.value)
    }

    @Test fun clesEffectives_reunissentChainesEtCategoriesVerrouillees() {
        assertEquals(setOf("1:9", "1:5"), CategoryLocks.effectiveKeys(setOf("1:9"), listOf("1:5")))
    }
}

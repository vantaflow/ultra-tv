package com.ultratv.tv.nativeapp.data.parental

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalGateTest {
    private fun ch(id: Long, cat: String = "c1") = ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = "Chaine $id", logo = null, categoryId = cat, streamUrl = "u$id", seq = id.toInt())

    private fun gate(keys: Set<String> = emptySet(), cats: Set<String> = emptySet()) = ParentalGate(object : LockSource {
        override suspend fun lockedChannelKeys() = keys
        override suspend fun lockedCategoryIds(providerId: Long) = cats
    })

    @Test fun chaine_libre_passe_directement() = runBlocking {
        val g = gate(keys = setOf("1:r9"))
        var applied = false
        assertEquals("u2", g.request(ch(2)) { applied = true; "u2" })
        assertTrue(applied); assertNull(g.pending.value)
    }

    @Test fun chaine_verrouillee_attend_le_code_sans_rien_changer() = runBlocking {
        val g = gate(keys = setOf("1:r2"))
        var applied = false
        assertNull(g.request(ch(2)) { applied = true; "u2" })
        assertFalse(applied)
        assertEquals("r2", g.pending.value?.channel?.remoteId)
    }

    @Test fun annulation_ou_refus_reste_sur_la_chaine_actuelle() = runBlocking {
        val g = gate(keys = setOf("1:r2"))
        var applied = false
        g.request(ch(2)) { applied = true; "u2" }
        g.cancel()
        assertFalse(applied); assertNull(g.pending.value); assertNull(g.grant())
    }

    @Test fun code_accepte_bascule_et_vaut_pour_la_session() = runBlocking {
        val g = gate(keys = setOf("1:r2"))
        g.request(ch(2)) { "u2" }
        assertEquals("u2", g.grant())
        assertEquals("u2", g.request(ch(2)) { "u2" })      // même chaîne dans la session : plus de code
        g.endSession()
        assertNull(g.request(ch(2)) { "u2" })              // fin de session : reverrouillée
        assertNotNull(g.pending.value)
    }

    @Test fun un_deblocage_ne_vaut_que_pour_sa_chaine() = runBlocking {
        val g = gate(keys = setOf("1:r2", "1:r3"))
        g.request(ch(2)) { "u2" }; g.grant()
        assertNull(g.request(ch(3)) { "u3" })
    }

    @Test fun categorie_verrouillee_bloque_ses_chaines() = runBlocking {
        val g = gate(cats = setOf("adultes"))
        assertNull(g.request(ch(5, "adultes")) { "u5" })
        assertEquals("u6", g.request(ch(6, "c1")) { "u6" })
    }

    @Test fun logique_pure() {
        assertTrue(ParentalGate.isBlocked(ch(1), setOf("1:r1"), emptySet(), emptySet()))
        assertFalse(ParentalGate.isBlocked(ch(1), setOf("1:r1"), emptySet(), setOf("1:r1")))
        assertFalse(ParentalGate.isBlocked(ch(1), emptySet(), emptySet(), emptySet()))
    }

    @Test fun file_de_zapping_peek_et_moveTo_ne_bougent_qu_a_laccord() {
        val q = LivePlaybackQueue()
        val list = listOf(ch(1), ch(2), ch(3))
        q.set(list, list[0])
        assertEquals(2L, q.peek(true)?.id)
        assertEquals(0, q.state.value?.index)
        assertEquals(3L, q.peek(false)?.id)
        assertTrue(q.moveTo(list[1]))
        assertEquals(1, q.state.value?.index)
        assertFalse(q.moveTo(ch(99)))
    }
}

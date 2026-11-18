package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSeqTest {
    private fun ch(id: String, cat: String = "c") = ChannelEntity(providerId = 1, remoteId = id, name = "Chaîne $id", logo = null, categoryId = cat, streamUrl = "u")

    @Test fun `les chaînes masquées ne prennent pas de numéro`() {
        val out = ChannelSeq(skip = setOf("1", "2")).assign(listOf(ch("1"), ch("2"), ch("3"), ch("4")))
        assertEquals(listOf(0, 0, 1, 2), out.map { it.seq })
    }

    @Test fun `numérotation par catégorie, sur plusieurs lots`() {
        val s = ChannelSeq()
        val a = s.assign(listOf(ch("1", "a"), ch("2", "b")))
        val b = s.assign(listOf(ch("3", "a")))
        assertEquals(listOf(1, 1, 2), (a + b).map { it.seq })
    }

    @Test fun `resserrer après masquage`() {
        // Chaînes 1 et 2 retirées : la 3 devient 1, la 4 devient 2.
        assertEquals(listOf(30L to 1, 40L to 2), ChannelSeq.compact(listOf(30L to 3, 40L to 4)))
        assertEquals(emptyList<Pair<Long, Int>>(), ChannelSeq.compact(listOf(1L to 1, 2L to 2)))
    }
}

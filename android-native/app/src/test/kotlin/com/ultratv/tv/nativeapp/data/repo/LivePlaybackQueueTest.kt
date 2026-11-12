package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LivePlaybackQueueTest {
    private fun ch(id: Long) = ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = "Chaine $id", logo = null, categoryId = "c", streamUrl = "http://s.invalid/$id.ts")

    @Test
    fun `chaine en double dans la liste - gardee une seule fois sans boucle de zapping`() {
        val q = LivePlaybackQueue()
        val a = ch(5); val b = ch(9818); val c = ch(7)
        q.set(listOf(a, b, a, c, b), b)
        assertEquals(listOf(5L, 9818L, 7L), q.state.value!!.channels.map { it.id })
        assertEquals(1, q.state.value!!.index)
        assertEquals(7L, q.next()!!.id)
    }
}

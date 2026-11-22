package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ANR réels : `MediaPlayer.nativeStop` et `nativeSetVideoTrack` (LibVLC) appelés sur le fil principal, bloqués plusieurs
 * secondes sur la sortie vidéo. Les appels natifs passent par un fil dédié au moteur, dans l'ordre, et la libération
 * (stop → detach → release) passe en dernier puis arrête ce fil.
 */
class VlcCommandQueueTest {
    @Test fun lAppelantNeFaitJamaisLAppelNatif_etLOrdreEstGarde() {
        val q = VlcCommandQueue("vlc-test")
        val order = Collections.synchronizedList(mutableListOf<String>())
        val caller = Thread.currentThread()
        val threads = Collections.synchronizedList(mutableListOf<Thread>())
        q.post { threads += Thread.currentThread(); Thread.sleep(200); order += "stop" }   // stop bloquant
        q.post { threads += Thread.currentThread(); order += "media" }
        q.post { threads += Thread.currentThread(); order += "play" }
        val done = CountDownLatch(1)
        val t0 = System.nanoTime()
        q.close { order += "release"; done.countDown() }
        assertTrue("l'appelant ne doit pas attendre l'arrêt natif", (System.nanoTime() - t0) / 1_000_000 < 100)
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("stop", "media", "play", "release"), order)
        threads.forEach { assertNotSame(caller, it) }
    }

    @Test fun apresFermeture_plusRienNePart_etLeFilSArrete() {
        val q = VlcCommandQueue("vlc-test")
        val done = CountDownLatch(1)
        q.close { done.countDown() }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        var ran = false
        q.post { ran = true }
        assertTrue(q.awaitTermination(2_000))
        assertFalse(ran)
        assertFalse(Thread.getAllStackTraces().keys.any { it.name == "vlc-test" && it.isAlive })
    }

    @Test fun attenteBornee_lAppelantRepartMemeSiLeNatifBloque() {
        val q = VlcCommandQueue("vlc-test")
        val t0 = System.nanoTime()
        val finished = q.postAndWait(150) { Thread.sleep(2_000) }
        val waited = (System.nanoTime() - t0) / 1_000_000
        assertFalse(finished)
        assertTrue("attente bornée (${waited} ms)", waited < 1_000)
        assertTrue("la commande suivante passe après la bloquante", q.postAndWait(4_000) { })
        q.close { }
    }
}

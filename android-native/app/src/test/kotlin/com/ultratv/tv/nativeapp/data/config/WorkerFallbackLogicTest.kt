package com.ultratv.tv.nativeapp.data.config

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class WorkerFallbackLogicTest {
    private val p = WorkerFallbackLogic.PRIMARY_HOST
    private val l = WorkerFallbackLogic.LEGACY_HOST

    @Test fun `alternate : les deux hotes du Worker se renvoient l'un a l'autre`() {
        assertEquals(l, WorkerFallbackLogic.alternate(p))
        assertEquals(p, WorkerFallbackLogic.alternate(l))
        assertEquals(l, WorkerFallbackLogic.alternate("TV.Weedy.DPDNS.org"))
    }

    @Test fun `alternate : tout autre hote est ignore`() {
        assertNull(WorkerFallbackLogic.alternate("example.com"))
        assertNull(WorkerFallbackLogic.alternate("api.github.com"))
    }

    @Test fun `preferredHost : dernier hote qui a marche, sinon celui demande`() {
        assertEquals(p, WorkerFallbackLogic.preferredHost(p, null))
        assertEquals(l, WorkerFallbackLogic.preferredHost(p, l))
        assertEquals(p, WorkerFallbackLogic.preferredHost(l, p))
        assertEquals(l, WorkerFallbackLogic.preferredHost(l, "example.com"))
        assertEquals("example.com", WorkerFallbackLogic.preferredHost("example.com", l))
    }

    @Test fun `shouldFallback : pannes reseau oui, autres erreurs non`() {
        assertTrue(WorkerFallbackLogic.shouldFallback(UnknownHostException("dns")))
        assertTrue(WorkerFallbackLogic.shouldFallback(SocketTimeoutException("timeout")))
        assertTrue(WorkerFallbackLogic.shouldFallback(IOException("tls")))
        assertFalse(WorkerFallbackLogic.shouldFallback(IllegalStateException("bug")))
    }

    @Test fun `withHost : chemin et requete conserves`() {
        val u = "https://$p/api/config?a=1&b=2".toHttpUrl()
        assertEquals("https://$l/api/config?a=1&b=2", WorkerFallbackLogic.withHost(u, l).toString())
    }
}

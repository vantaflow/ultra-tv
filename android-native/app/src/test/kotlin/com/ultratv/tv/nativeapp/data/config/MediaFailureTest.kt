package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.config.MediaFailure.Reason
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFailureTest {
    private val timeout = java.net.SocketTimeoutException("failed to connect to /192.168.1.108 (port 8096) from /10.3.0.2 after 10000ms")

    @Test fun `serveur local, appareil sur un autre réseau`() =
        assertEquals(Reason.OTHER_NETWORK, MediaFailure.reason(timeout, "http://192.168.1.108:8096", listOf("10.3.0.2")))

    @Test fun `même réseau mais pas de réponse`() =
        assertEquals(Reason.NO_ANSWER, MediaFailure.reason(timeout, "http://192.168.1.108:8096", listOf("192.168.1.20")))

    @Test fun `connexion refusée et identifiants`() {
        assertEquals(Reason.REFUSED, MediaFailure.reason(java.net.ConnectException("refused"), "http://jf.example.com:8096", emptyList()))
        assertEquals(Reason.AUTH, MediaFailure.reason(com.ultratv.tv.nativeapp.data.net.HttpStatusException(401), "https://jf.example.com", emptyList()))
    }

    @Test fun `hôte et port affichés`() {
        assertEquals("192.168.1.108:8096", MediaFailure.hostOf("http://192.168.1.108:8096/web"))
        assertEquals("jf.example.com", MediaFailure.hostOf("https://jf.example.com"))
    }
}

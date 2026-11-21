package com.ultratv.tv.nativeapp.data.net

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/** Uniquement des hôtes fictifs (.invalid) : aucune vraie source dans les tests. */
class NetErrorsTest {
    @Test fun hoteInexistant_nxdomain() =
        assertEquals(SyncErrorKind.HOST_NOT_FOUND, NetErrors.classify(UnknownHostException("serveur-inexistant.invalid")))

    @Test fun delaiDepasse() {
        assertEquals(SyncErrorKind.TIMEOUT, NetErrors.classify(SocketTimeoutException("timeout")))
        assertEquals(SyncErrorKind.TIMEOUT, NetErrors.classify(InterruptedIOException("timeout")))
    }

    @Test fun connexionRefuseeOuSansRoute() {
        assertEquals(SyncErrorKind.UNREACHABLE, NetErrors.classify(ConnectException("refused")))
        assertEquals(SyncErrorKind.UNREACHABLE, NetErrors.classify(NoRouteToHostException()))
    }

    @Test fun codesHttp() {
        assertEquals(SyncErrorKind.UNAUTHORIZED, NetErrors.classify(HttpStatusException(401)))
        assertEquals(SyncErrorKind.UNAUTHORIZED, NetErrors.classify(HttpStatusException(403)))
        assertEquals(SyncErrorKind.NOT_FOUND, NetErrors.classify(HttpStatusException(404)))
        assertEquals(SyncErrorKind.SERVER_ERROR, NetErrors.classify(HttpStatusException(503)))
        // Code non standard renvoyé par certains fournisseurs pour refuser get.php (M3U).
        assertEquals(SyncErrorKind.PROVIDER_BLOCKED, NetErrors.classify(HttpStatusException(884)))
    }

    @Test fun tls() =
        assertEquals(SyncErrorKind.TLS, NetErrors.classify(SSLHandshakeException("bad cert")))

    @Test fun causeImbriquee_estTrouvee() =
        assertEquals(SyncErrorKind.HOST_NOT_FOUND, NetErrors.classify(RuntimeException("x", IOException("y", UnknownHostException()))))

    @Test fun tropDeDemandes_429_nEstPlusConfonduAvecUnRefusDuTypeDAcces() =
        assertEquals(SyncErrorKind.RATE_LIMITED, NetErrors.classify(HttpStatusException(429)))

    /** Télémétrie : « Unexpected JSON token… had '<' » (page HTML à la place de la liste) tombait dans « a échoué ». */
    @Test fun reponseIllisible_pageWebALaPlaceDeLaListe() {
        assertEquals(SyncErrorKind.INVALID_RESPONSE, NetErrors.classify(kotlinx.serialization.SerializationException("Unexpected JSON token at offset 0")))
        assertEquals(SyncErrorKind.INVALID_RESPONSE, NetErrors.classify(RuntimeException("x", org.xmlpull.v1.XmlPullParserException("bad"))))
    }

    /** Télémétrie : « failed to connect to /192.168.1.123 (port 80) from /10.2.0.2 » = serveur local, appareil derrière un VPN. */
    @Test fun adresseDuReseauLocalInjoignable_signaleeAPart() {
        assertEquals(SyncErrorKind.LOCAL_NETWORK, NetErrors.classifyFor(SocketTimeoutException("failed to connect"), "http://192.168.1.123/get.php?x=1"))
        assertEquals(SyncErrorKind.LOCAL_NETWORK, NetErrors.classifyFor(ConnectException("refused"), "http://10.0.0.5:8080"))
        assertEquals(SyncErrorKind.LOCAL_NETWORK, NetErrors.classifyFor(SocketTimeoutException(), "172.20.1.9:80"))
    }

    @Test fun adressePublique_ouAutreErreur_inchangees() {
        assertEquals(SyncErrorKind.TIMEOUT, NetErrors.classifyFor(SocketTimeoutException(), "http://serveur.invalid:8080"))
        assertEquals(SyncErrorKind.TIMEOUT, NetErrors.classifyFor(SocketTimeoutException(), "http://8.8.8.8"))
        assertEquals(SyncErrorKind.TIMEOUT, NetErrors.classifyFor(SocketTimeoutException(), "http://172.32.0.1"))
        assertEquals(SyncErrorKind.TIMEOUT, NetErrors.classifyFor(SocketTimeoutException(), null))
        // Le serveur local a répondu : le réseau n'est pas en cause.
        assertEquals(SyncErrorKind.UNAUTHORIZED, NetErrors.classifyFor(HttpStatusException(401), "http://192.168.1.123"))
        assertEquals(SyncErrorKind.NOT_FOUND, NetErrors.classifyFor(HttpStatusException(404), "http://192.168.1.123"))
    }

    @Test fun inconnu() = assertEquals(SyncErrorKind.OTHER, NetErrors.classify(IllegalStateException()))
}

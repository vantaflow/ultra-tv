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

    @Test fun inconnu() = assertEquals(SyncErrorKind.OTHER, NetErrors.classify(IllegalStateException()))
}

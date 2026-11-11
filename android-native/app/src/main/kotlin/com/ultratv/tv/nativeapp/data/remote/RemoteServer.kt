package com.ultratv.tv.nativeapp.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Petit serveur HTTP sur [ServerSocket], dans l'esprit de TimeshiftProxy : aucune dépendance, une coroutine par
 * connexion (8 au plus), délai de lecture de 5 s. Il n'écoute que sur [bindAddress] (l'adresse locale de la box) et
 * refuse tout client hors réseau local. Le routage et le jeton sont dans [RemoteRouter].
 */
class RemoteServer(
    private val router: RemoteRouter,
    bindAddress: InetAddress,
    firstPort: Int = DEFAULT_PORT,
    private val localOnly: Boolean = true,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(8)
    private val server: ServerSocket = bind(bindAddress, firstPort)
    val port: Int get() = server.localPort

    fun start() { scope.launch { acceptLoop() } }

    private suspend fun acceptLoop() {
        while (!server.isClosed) {
            val s = try { server.accept() } catch (_: Exception) { return }
            if (localOnly && !RemoteNet.isLocalPeer(s.inetAddress)) { runCatching { s.close() }; continue }
            if (!slots.tryAcquire()) { runCatching { s.close() }; continue }
            scope.launch { try { serve(s) } finally { slots.release() } }
        }
    }

    private suspend fun serve(s: Socket) {
        s.use {
            try {
                s.soTimeout = 5_000
                val req = HttpParser.read(s.getInputStream())
                val resp = router.handle(req)
                // Mauvais jeton : petit délai, contre les essais en rafale.
                if (resp.status == 401) delay(300)
                s.getOutputStream().apply { write(resp.toBytes()); flush() }
            } catch (e: HttpParser.Bad) {
                runCatching { s.getOutputStream().write(RemoteRouter.json(e.status, org.json.JSONObject().put("error", "bad_request")).toBytes()) }
            } catch (_: Exception) {
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
        scope.cancel()
    }

    companion object {
        const val DEFAULT_PORT = 8766

        /** Premier port libre à partir de [first] (8766, 8767…), pour ne pas échouer si un autre service l'occupe. */
        private fun bind(addr: InetAddress, first: Int): ServerSocket {
            var last: Exception? = null
            for (p in first until first + 10) {
                try { return ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(addr, p), 8) } } catch (e: Exception) { last = e }
            }
            throw last ?: IllegalStateException("no port")
        }
    }
}

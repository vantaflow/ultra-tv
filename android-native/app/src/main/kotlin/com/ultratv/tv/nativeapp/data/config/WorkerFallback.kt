package com.ultratv.tv.nativeapp.data.config

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Repli automatique entre les deux adresses du Worker (domaine personnalisé / workers.dev), certains réseaux
 * bloquant *.workers.dev. Décision pure ici, interceptor OkHttp plus bas.
 */
object WorkerFallbackLogic {
    const val PRIMARY_HOST = "tv.weedy.dpdns.org"
    const val LEGACY_HOST = "ultratv-config.khalilbenaz.workers.dev"

    /** Dernier hôte qui a répondu (mémoire du processus) ; null = aucun encore. */
    @Volatile var lastWorking: String? = null

    /** Hôte de repli pour [host], ou null s'il ne s'agit pas d'un hôte du Worker. */
    fun alternate(host: String): String? = when (host.lowercase()) {
        PRIMARY_HOST -> LEGACY_HOST
        LEGACY_HOST -> PRIMARY_HOST
        else -> null
    }

    /** Hôte à utiliser d'emblée : le dernier qui a marché s'il fait partie des deux, sinon celui demandé. */
    fun preferredHost(requested: String, last: String?): String {
        val r = requested.lowercase()
        return if (alternate(r) != null && last != null && alternate(last.lowercase()) != null) last.lowercase() else r
    }

    /** Repli seulement pour une panne réseau (DNS, connexion, TLS, délai), jamais pour un code HTTP. */
    fun shouldFallback(e: Throwable): Boolean = e is IOException

    fun withHost(url: HttpUrl, host: String): HttpUrl = url.newBuilder().host(host).build()
}

class WorkerFallbackInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val requested = req.url.host
        if (WorkerFallbackLogic.alternate(requested) == null) return chain.proceed(req)
        val first = WorkerFallbackLogic.preferredHost(requested, WorkerFallbackLogic.lastWorking)
        val firstReq = if (first == requested.lowercase()) req else req.newBuilder().url(WorkerFallbackLogic.withHost(req.url, first)).build()
        try {
            val resp = chain.proceed(firstReq)
            WorkerFallbackLogic.lastWorking = first
            return resp
        } catch (e: IOException) {
            if (!WorkerFallbackLogic.shouldFallback(e) || chain.call().isCanceled()) throw e
            val other = WorkerFallbackLogic.alternate(first) ?: throw e
            val retry = req.newBuilder().url(WorkerFallbackLogic.withHost(req.url, other)).build()
            val resp = try { chain.proceed(retry) } catch (e2: IOException) { e.addSuppressed(e2); throw e }
            WorkerFallbackLogic.lastWorking = other
            return resp
        }
    }
}

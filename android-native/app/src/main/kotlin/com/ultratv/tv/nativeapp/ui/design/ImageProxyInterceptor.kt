package com.ultratv.tv.nativeapp.ui.design

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.Dimension
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder

/**
 * Fonds, vignettes et logos de Fanart.tv et TVmaze : ces hébergeurs servent des images d'origine (PNG/JPEG de plusieurs
 * centaines de Ko, jusqu'à 4 K). On les fait passer par wsrv.nl (service open source de redimensionnement) qui les rend
 * en WebP à la bonne largeur. Seuls ces deux hébergeurs sont concernés : image.tmdb.org et tout autre hôte restent
 * intacts. Si wsrv.nl échoue ou dépasse 4 s, on retombe sur l'URL d'origine.
 */
object ImageProxy {
    const val TIMEOUT_MS = 4000L
    const val W_BACKGROUND = 1280
    const val W_BACKGROUND_LOW = 960
    const val W_THUMB = 640
    const val W_LOGO = 500

    private val HOSTS = setOf("assets.fanart.tv", "static.tvmaze.com")

    enum class Kind { BACKGROUND, THUMB, LOGO }

    private fun hostOf(url: String): String? {
        val rest = when {
            url.startsWith("https://") -> url.substring(8)
            url.startsWith("http://") -> url.substring(7)
            else -> return null
        }
        return rest.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').lowercase()
    }

    fun isEligible(url: String): Boolean = hostOf(url) in HOSTS

    /** Type d'image déduit du chemin Fanart (hdmovielogo, showbackground, moviethumb…) ; TVmaze : fond si le cadre est large. */
    fun kindOf(url: String, frameWidthPx: Int?): Kind {
        val p = url.lowercase()
        return when {
            "logo" in p -> Kind.LOGO
            "background" in p -> Kind.BACKGROUND
            hostOf(url) == "static.tvmaze.com" && (frameWidthPx ?: 0) >= 1000 -> Kind.BACKGROUND
            else -> Kind.THUMB
        }
    }

    fun width(kind: Kind, lowMem: Boolean): Int = when (kind) {
        Kind.BACKGROUND -> if (lowMem) W_BACKGROUND_LOW else W_BACKGROUND
        Kind.THUMB -> W_THUMB
        Kind.LOGO -> W_LOGO
    }

    /** URL via wsrv.nl, ou null (préférence désactivée, hôte non concerné, URL déjà proxifiée). */
    fun rewrite(url: String, enabled: Boolean, lowMem: Boolean, frameWidthPx: Int? = null): String? {
        if (!enabled || !isEligible(url)) return null
        val bare = url.removePrefix("https://").removePrefix("http://")
        val w = width(kindOf(url, frameWidthPx), lowMem)
        return "https://wsrv.nl/?url=" + URLEncoder.encode(bare, "UTF-8") + "&w=$w&output=webp&q=75"
    }

    /** Résultat via [proxied] ; s'il est nul ou en erreur (échec, délai de 4 s dépassé), on rejoue avec [original]. */
    suspend fun <T> withFallback(isError: (T) -> Boolean, proxied: suspend () -> T, original: suspend () -> T): T {
        val r = withTimeoutOrNull(TIMEOUT_MS) { runCatching { proxied() }.getOrNull() }
        return if (r != null && !isError(r)) r else original()
    }
}

class ImageProxyInterceptor(
    private val enabled: () -> Boolean,
    private val lowMem: () -> Boolean,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val req = chain.request
        val url = req.data as? String ?: return chain.proceed()
        val frameW = (chain.size.width as? Dimension.Pixels)?.px
        val nu = ImageProxy.rewrite(url, enabled(), lowMem(), frameW) ?: return chain.proceed()
        return ImageProxy.withFallback(
            isError = { it is ErrorResult },
            proxied = { chain.withRequest(req.newBuilder().data(nu).build()).proceed() },
            original = { chain.proceed() },
        )
    }
}

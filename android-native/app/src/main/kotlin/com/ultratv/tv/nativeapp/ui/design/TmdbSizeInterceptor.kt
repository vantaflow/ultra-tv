package com.ultratv.tv.nativeapp.ui.design

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size

/**
 * Affiches et fonds hébergés par TMDB : on demande la plus petite taille standard qui couvre le cadre à remplir.
 * Les fournisseurs renvoient souvent `original` ou `w600_and_h900_bestv2` (JPEG progressifs de plusieurs centaines de
 * Ko) pour une carte de 200 px : le décodage (≈ 60 ms par image sur une box 32 bits, quatre à la fois, en plein
 * défilement) mangeait un quart du processeur de l'appli. Les URL d'autres hébergeurs sont laissées telles quelles.
 */
object TmdbSizeInterceptor : Interceptor {
    private val SIZES = intArrayOf(92, 154, 185, 342, 500, 780, 1280)
    private val PATTERN = Regex("^(https?://image\\.tmdb\\.org/t/p/)(original|w\\d+(?:_and_h\\d+_[A-Za-z0-9]+)?)(/.+)$")

    /** Nouvelle URL pour un cadre de [widthPx] pixels, ou null si l'URL n'est pas une image TMDB (ou déjà à la bonne taille). */
    fun rewrite(url: String, widthPx: Int): String? {
        val m = PATTERN.matchEntire(url) ?: return null
        val wanted = SIZES.firstOrNull { it >= widthPx } ?: SIZES.last()
        val target = "w$wanted"
        return if (m.groupValues[2] == target) null else m.groupValues[1] + target + m.groupValues[3]
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val req = chain.request
        val url = req.data as? String ?: return chain.proceed()
        val size: Size = chain.size
        val w = (size.width as? Dimension.Pixels)?.px ?: return chain.proceed()
        val nu = rewrite(url, w) ?: return chain.proceed()
        return chain.withRequest(req.newBuilder().data(nu).build()).proceed()
    }
}

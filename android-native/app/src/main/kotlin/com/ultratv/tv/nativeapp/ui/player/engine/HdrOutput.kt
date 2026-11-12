package com.ultratv.tv.nativeapp.ui.player.engine

import android.content.Context

/**
 * Sortie HDR (pur, testé). Une TextureView est composée par le GPU en SDR : un flux HDR10 / HLG / Dolby Vision y paraît
 * terne. Sur un écran HDR, il faut une SurfaceView (couche matérielle) pour que l'image soit envoyée en HDR.
 */
object HdrOutput {
    // Valeurs de C.COLOR_TRANSFER_ST2084 / C.COLOR_TRANSFER_HLG (Media3), recopiées pour garder cette logique sans dépendance.
    const val TRANSFER_ST2084 = 6
    const val TRANSFER_HLG = 7
    const val MIME_DOLBY_VISION = "video/dolby-vision"

    /** « hdr10 », « hlg » ou « dv » ; null = contenu SDR (ou inconnu). */
    fun contentKind(colorTransfer: Int?, mime: String?): String? = when {
        mime.equals(MIME_DOLBY_VISION, ignoreCase = true) -> "dv"
        colorTransfer == TRANSFER_ST2084 -> "hdr10"
        colorTransfer == TRANSFER_HLG -> "hlg"
        else -> null
    }

    fun isHdrContent(colorTransfer: Int?, mime: String?): Boolean = contentKind(colorTransfer, mime) != null

    /** Écran HDR : types HDR pris en charge (Display.HdrCapabilities) non vides, ou Display.isHdr. */
    fun displayHdrCapable(supportedHdrTypes: IntArray?, isHdr: Boolean?): Boolean =
        (supportedHdrTypes?.isNotEmpty() == true) || isHdr == true

    /**
     * Faut-il relancer la lecture en SurfaceView pour cet élément ? Seulement si : contenu HDR, écran HDR, rendu actuel en
     * TextureView, réglage « auto » (un choix imposé n'est jamais contourné), pas déjà tenté pour cet élément, et la
     * SurfaceView n'a pas déjà échoué (image invisible) sur cet appareil.
     */
    fun shouldSwitchToSurface(
        contentHdr: Boolean, displayHdr: Boolean, currentlyTexture: Boolean,
        userPref: String, alreadySwitched: Boolean, surfaceKnownBad: Boolean,
    ): Boolean = contentHdr && displayHdr && currentlyTexture && userPref == "auto" && !alreadySwitched && !surfaceKnownBad

    /** Ligne de journal (jamais d'adresse). */
    fun describe(kind: String, displayHdr: Boolean, texture: Boolean, engine: EngineKind): String =
        "hdr content=$kind display=${if (displayHdr) "hdr" else "sdr"} surface=${if (texture) "texture" else "surface"} engine=${engine.name}"

    /** Lecture de l'écran par défaut. Faux en cas d'échec. */
    fun displayHdrCapable(ctx: Context): Boolean = runCatching {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val d = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return@runCatching false
        @Suppress("DEPRECATION") val types = d.hdrCapabilities?.supportedHdrTypes
        displayHdrCapable(types, d.isHdr)
    }.getOrDefault(false)

    private const val PREFS = "ultra_hdr_output"
    private const val KEY_SURFACE_BAD = "surface_bad"

    /** SurfaceView sans image sur cet appareil (mémorisé : on ne réessaie pas à chaque lecture HDR). */
    fun surfaceKnownBad(ctx: Context): Boolean =
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SURFACE_BAD, false) }.getOrDefault(false)

    fun rememberSurfaceBad(ctx: Context) {
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SURFACE_BAD, true).apply() }
    }
}

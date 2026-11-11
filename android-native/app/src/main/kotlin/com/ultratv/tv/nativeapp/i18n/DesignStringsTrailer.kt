package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.ui.trailer.TrailerErrorKind

/**
 * Libellés du lecteur de bande-annonce intégré (ui/trailer). Fichier à part pour ne pas toucher à [DesignStrings].
 * Le bouton d'ouverture reste `D.trailerLabel` (DesignStringsB1).
 */
val DesignStrings.trailerPlay get() = tx(R.string.trailer_play)
val DesignStrings.trailerPause get() = tx(R.string.trailer_pause)
val DesignStrings.trailerBack10 get() = tx(R.string.trailer_back10)
val DesignStrings.trailerFwd10 get() = tx(R.string.trailer_fwd10)
val DesignStrings.trailerClose get() = tx(R.string.trailer_close)
val DesignStrings.trailerLoading get() = tx(R.string.trailer_loading)
val DesignStrings.trailerShowQr get() = tx(R.string.trailer_show_qr)
val DesignStrings.trailerQrHint get() = tx(R.string.trailer_qr_hint)

/** Message clair pour une erreur du lecteur (jamais d'ouverture externe automatique). */
fun DesignStrings.trailerError(kind: TrailerErrorKind): String = when (kind) {
    TrailerErrorKind.NotFound -> tx(R.string.trailer_err_not_found)
    TrailerErrorKind.EmbedBlocked -> tx(R.string.trailer_err_embed)
    TrailerErrorKind.Config -> tx(R.string.trailer_err_config)
    TrailerErrorKind.NoWebView -> tx(R.string.trailer_err_no_webview)
    TrailerErrorKind.Unknown -> tx(R.string.trailer_err_unknown)
}

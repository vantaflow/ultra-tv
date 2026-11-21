package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.engine.PlayErrorKind

/**
 * Ce que l'écran d'erreur du lecteur dit à l'utilisateur (logique pure, testée) : une cause en clair et quoi faire.
 *
 * Avant, tout échec réseau affichait « Problème de réseau » sans autre indication, y compris quand le serveur du
 * fournisseur répondait 503 (panne chez lui) ou 429 (trop de lectures simultanées) : l'utilisateur vérifiait sa box
 * alors que sa connexion n'était pas en cause.
 */
enum class PlayErrorCase {
    /** Aucune réponse du serveur : connexion Internet coupée ou serveur de la chaîne hors ligne. */
    UNREACHABLE,
    /** 5xx : panne côté fournisseur. */
    SERVER_DOWN,
    /** 429 : le fournisseur limite le nombre de lectures simultanées. */
    TOO_MANY,
    /** Autre code 4xx : demande rejetée par le fournisseur. */
    REJECTED,
    REFUSED, PORTAL_REFUSED, NO_LINK, NOT_FOUND,
    FORMAT, DECODER, NO_PICTURE, START_TIMEOUT,
    DRM_UNSUPPORTED, DRM_LICENSE, DRM_INVALID,
    /** Cause inconnue : message générique, mais jamais d'écran sans explication ni action. */
    NO_RESPONSE,
}

object PlayErrorText {
    /** Cas affiché pour [kind] et le dernier code HTTP reçu ([http], null si le serveur n'a pas répondu). */
    fun case(kind: PlayErrorKind, http: Int?): PlayErrorCase = when (kind) {
        PlayErrorKind.NETWORK -> when {
            http == 429 -> PlayErrorCase.TOO_MANY
            http != null && http in 500..599 -> PlayErrorCase.SERVER_DOWN
            http != null && http in 400..499 -> PlayErrorCase.REJECTED
            else -> PlayErrorCase.UNREACHABLE
        }
        PlayErrorKind.REFUSED -> PlayErrorCase.REFUSED
        PlayErrorKind.PORTAL_REFUSED -> PlayErrorCase.PORTAL_REFUSED
        PlayErrorKind.NO_LINK -> PlayErrorCase.NO_LINK
        PlayErrorKind.NOT_FOUND -> PlayErrorCase.NOT_FOUND
        PlayErrorKind.FORMAT -> PlayErrorCase.FORMAT
        PlayErrorKind.DECODER -> PlayErrorCase.DECODER
        PlayErrorKind.NO_PICTURE -> PlayErrorCase.NO_PICTURE
        PlayErrorKind.START_TIMEOUT -> PlayErrorCase.START_TIMEOUT
        PlayErrorKind.DRM_UNSUPPORTED -> PlayErrorCase.DRM_UNSUPPORTED
        PlayErrorKind.DRM_LICENSE -> PlayErrorCase.DRM_LICENSE
        PlayErrorKind.DRM_INVALID -> PlayErrorCase.DRM_INVALID
        PlayErrorKind.BEHIND_LIVE, PlayErrorKind.UNKNOWN -> PlayErrorCase.NO_RESPONSE
    }

    /**
     * Code technique affiché en petit sous le message (« HTTP 503 »), à donner au support ou au fournisseur. Seulement
     * quand il éclaire la cause : jamais pour un format ou un décodeur, où un vieux code HTTP induirait en erreur.
     */
    fun code(case: PlayErrorCase, http: Int?): String? = when (case) {
        PlayErrorCase.SERVER_DOWN, PlayErrorCase.TOO_MANY, PlayErrorCase.REJECTED, PlayErrorCase.REFUSED,
        PlayErrorCase.PORTAL_REFUSED, PlayErrorCase.NOT_FOUND -> http?.takeIf { it in 100..999 }?.let { "HTTP $it" }
        else -> null
    }
}

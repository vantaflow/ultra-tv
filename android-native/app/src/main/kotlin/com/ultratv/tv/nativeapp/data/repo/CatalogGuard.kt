package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.net.EmptyCatalogException

/**
 * Garde anti-effacement de la synchro : une liste reçue vide, ou effondrée, alors que la base contient un catalogue
 * n'écrase rien. Un compte expiré renvoie souvent `[]` en HTTP 200 ; sans cette garde, chaînes, films et séries
 * disparaissaient (écran vide, favoris et reprises invisibles).
 *
 * Seuil de la baisse brutale : refus quand la source avait au moins [RATIO_MIN_BEFORE] éléments et qu'il en reste
 * moins de 1/[MIN_SHARE_DIVISOR] (2 %). 10 000 → 12 est refusé ; 10 000 → 2 000 (un bouquet retiré) passe. Sous
 * 1 000 éléments, seule la liste entièrement vide est refusée : une petite playlist peut vraiment fondre.
 * Une catégorie seule qui se vide reste acceptée (le total ne tombe pas à zéro).
 */
object CatalogGuard {
    const val RATIO_MIN_BEFORE = 1_000
    const val MIN_SHARE_DIVISOR = 50

    /** Faut-il refuser [received] éléments reçus alors que la base en a [before] ? */
    fun refuses(before: Int, received: Int): Boolean =
        before > 0 && (received <= 0 || (before >= RATIO_MIN_BEFORE && received.toLong() * MIN_SHARE_DIVISOR < before))

    /** Lève [EmptyCatalogException] (et le journalise, sans identifiant) si [refuses]. */
    fun check(part: String, before: Int, received: Int) {
        if (!refuses(before, received)) return
        com.ultratv.tv.nativeapp.RemoteLog.warn("SyncGuard", "liste refusée partie=$part avant=$before reçu=$received : catalogue gardé")
        throw EmptyCatalogException(part, before, received)
    }
}

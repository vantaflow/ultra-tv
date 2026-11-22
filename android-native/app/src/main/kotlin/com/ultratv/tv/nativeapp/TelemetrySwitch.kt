package com.ultratv.tv.nativeapp

/**
 * Interrupteur général des envois de télémétrie (journaux, plantages, blocages, relevé anonyme).
 *
 * Coupé par défaut sous Robolectric (empreinte « robolectric ») et hors Android (empreinte illisible) : les tests unitaires
 * envoyaient de faux plantages et événements « robolectric robolectric » à la production. Sur un vrai appareil il est
 * ouvert et seul le réglage utilisateur ([RemoteLog.telemetryEnabled]) décide, comme avant. Un test qui vérifie l'envoi
 * vers son propre serveur local l'ouvre lui-même et le referme après.
 */
object TelemetrySwitch {
    /** Ouvert sur un vrai appareil ; fermé sous Robolectric ou sans empreinte. */
    fun allowedByDefault(fingerprint: String?): Boolean = fingerprint != null && fingerprint != "robolectric"

    @Volatile var sendingAllowed: Boolean =
        allowedByDefault(runCatching { android.os.Build.FINGERPRINT }.getOrNull())
}

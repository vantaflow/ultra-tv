package com.ultratv.tv.nativeapp.nav

/** Écran ouvert au lancement (Réglages › Affichage › Ouvrir sur). */
enum class StartScreen(val key: String) {
    HOME("home"), LIVE("live"), MOVIES("movies"), SERIES("series"), GUIDE("guide"), LAST_CHANNEL("last"), LAST_SCREEN("last_screen");

    companion object {
        /**
         * Réglage enregistré ; vide ou inconnu → ancien réglage « Reprendre au lancement » ([legacyAutoPlayLast]),
         * pour qu'une mise à jour ne change pas l'écran d'ouverture de ceux qui l'avaient activé.
         */
        fun parse(raw: String?, legacyAutoPlayLast: Boolean): StartScreen =
            entries.firstOrNull { it.key == raw?.lowercase() } ?: if (legacyAutoPlayLast) LAST_CHANNEL else HOME
    }
}

/** Ce que le lancement doit faire : rien (l'accueil est la destination de départ), ouvrir une rubrique, ou relancer la dernière chaîne. */
sealed interface StartupTarget {
    data object Stay : StartupTarget
    data class Route(val route: String) : StartupTarget
    data object LastChannel : StartupTarget
}

/**
 * Rubriques que « Dernier écran » peut rouvrir (pas les fiches, le lecteur, la recherche ni les réglages).
 * Renvoie la route si elle est mémorisable, sinon null.
 */
fun rememberableScreen(route: String?): String? = route?.takeIf { it in REMEMBERABLE }

private val REMEMBERABLE = setOf(Routes.HOME, Routes.LIVE, Routes.MOVIES, Routes.SERIES, Routes.GUIDE, Routes.FAVORITES, Routes.SPORT, Routes.RECORDINGS, Routes.DOWNLOADS)

/** Correspondance écran d'ouverture → action. L'accueil reste toujours sous la rubrique ouverte (Retour y ramène). */
fun startupTarget(s: StartScreen, lastScreen: String? = null): StartupTarget = when (s) {
    StartScreen.HOME -> StartupTarget.Stay
    StartScreen.LIVE -> StartupTarget.Route(Routes.LIVE)
    StartScreen.MOVIES -> StartupTarget.Route(Routes.MOVIES)
    StartScreen.SERIES -> StartupTarget.Route(Routes.SERIES)
    StartScreen.GUIDE -> StartupTarget.Route(Routes.GUIDE)
    StartScreen.LAST_CHANNEL -> StartupTarget.LastChannel
    // Dernière rubrique ouverte à la fermeture ; l'accueil (ou rien de mémorisé) : on reste sur l'accueil.
    StartScreen.LAST_SCREEN -> rememberableScreen(lastScreen)?.takeIf { it != Routes.HOME }?.let { StartupTarget.Route(it) } ?: StartupTarget.Stay
}

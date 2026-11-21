package com.ultratv.tv.nativeapp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.ultratv.tv.nativeapp.nav.Routes

/**
 * État du menu latéral partagé avec la racine : [expanded] est tenu par le menu (il se referme quand il perd le focus),
 * [open] est appelé par Retour (voir [railBackAction]).
 */
@Stable
class RailMenuState {
    var expanded by mutableStateOf(false)
    /** Pose le focus dans le menu (sur l'entrée de l'écran courant) ; posé par le menu tant qu'il est composé. */
    internal var focusRail: (() -> Boolean)? by mutableStateOf(null)

    val canOpen: Boolean get() = focusRail != null

    /** Ouvre le menu et y place le focus, tout de suite (aucun effet différé). Faux si le menu n'est pas là. */
    fun open(): Boolean {
        val focus = focusRail ?: return false
        expanded = true
        if (focus()) return true
        expanded = false
        return false
    }
}

internal enum class RailBackAction {
    /** Laisse Retour aux autres gestionnaires (retours locaux, retour arrière, règle de sortie). */
    PASS,
    /** Ouvre le menu latéral, focus sur l'entrée de l'écran. */
    OPEN_MENU,
    /** Menu déjà ouvert sur un autre écran que l'accueil : retour à l'accueil (comportement d'avant). */
    MENU_TO_HOME,
}

/** Écrans principaux : exactement une entrée du menu (les sous-niveaux « movies/12 », le lecteur, la recherche n'en sont pas). */
private val mainRoutes: Set<String> by lazy { railItems.map { it.route }.toSet() + Routes.SPORT + Routes.LIBRARIES }

/**
 * Effet de Retour (TV, menu latéral) quand les retours locaux n'ont rien à fermer :
 * écran principal, menu fermé → ouvrir le menu ; menu ouvert hors de l'accueil → retour à l'accueil ; menu ouvert sur
 * l'accueil → [RailBackAction.PASS] (règle de sortie de l'appli : message « Maintenez Retour… », appui long).
 * Mode simple, fenêtre ouverte, menu masqué ou écran hors menu : toujours [RailBackAction.PASS].
 */
internal fun railBackAction(route: String?, menuOpen: Boolean, railShown: Boolean, simpleHome: Boolean, modalOpen: Boolean): RailBackAction = when {
    !railShown || simpleHome || modalOpen || route == null || route !in mainRoutes -> RailBackAction.PASS
    !menuOpen -> RailBackAction.OPEN_MENU
    route != Routes.HOME -> RailBackAction.MENU_TO_HOME
    else -> RailBackAction.PASS
}

/** Relie [RailMenuState.open] au menu : [railRoot] est son groupe de focus (`enter` = entrée de la page active). */
@Composable
internal fun RailMenuFocusLink(menu: RailMenuState, railRoot: FocusRequester) {
    DisposableEffect(menu, railRoot) {
        val link: () -> Boolean = { runCatching { railRoot.requestFocus() }.getOrDefault(false) }
        menu.focusRail = link
        onDispose { if (menu.focusRail === link) menu.focusRail = null }
    }
}

/**
 * Retour racine de la TV. À composer APRÈS le graphe de navigation et les hôtes de fenêtres de la racine : les gestionnaires
 * de Retour les plus récemment enregistrés passent en premier, donc les retours locaux (fenêtre, sous-dossier, catégorie,
 * fiche) et le retour arrière du graphe pour les sous-niveaux gardent la priorité.
 */
@Composable
internal fun RailBackHandler(
    route: String?, menu: RailMenuState, modalOpen: Boolean, simpleHome: Boolean, railShown: Boolean,
    onMenuToHome: () -> Unit,
) {
    val action = railBackAction(route, menu.expanded, railShown, simpleHome, modalOpen)
    BackHandler(enabled = action != RailBackAction.PASS && (action != RailBackAction.OPEN_MENU || menu.canOpen)) {
        when (action) {
            RailBackAction.OPEN_MENU -> menu.open()
            RailBackAction.MENU_TO_HOME -> onMenuToHome()
            RailBackAction.PASS -> Unit
        }
    }
}

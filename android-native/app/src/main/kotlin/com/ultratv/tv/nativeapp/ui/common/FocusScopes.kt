package com.ultratv.tv.nativeapp.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

/** Signal « la navigation vient de la barre latérale / du bandeau du haut ». */
object NavFocusHint {
    @Volatile private var navDriven = false
    fun markNavDriven() { navDriven = true }
    fun consume(): Boolean { val v = navDriven; navDriven = false; return v }
}

/**
 * Pose le focus D-pad sur le premier élément focalisable de [requester] et
 * retente tant que le contenu n'est pas là (listes alimentées par Room de façon
 * asynchrone : au premier frame il n'y a encore rien à focaliser).
 *
 * Sans cela, sur une TV rien n'est focalisé au lancement : la télécommande
 * « ne fait rien » jusqu'au premier appui directionnel, et DPAD_CENTER est
 * ignoré.
 */
@Composable
fun RequestInitialFocus(
    requester: FocusRequester,
    hasFocus: () -> Boolean,
    key: Any? = Unit,
    attempts: Int = INITIAL_FOCUS_ATTEMPTS,
) {
    LaunchedEffect(key) {
        repeat(attempts) {
            if (hasFocus()) return@LaunchedEffect
            val ok = runCatching { requester.requestFocus() }.getOrDefault(false)
            if (ok && hasFocus()) return@LaunchedEffect
            delay(INITIAL_FOCUS_RETRY_MS)
        }
    }
}

/**
 * Enveloppe le contenu d'un écran : groupe de focus + focus initial + restauration
 * du dernier élément focalisé quand on y revient.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreenFocusHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val requester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    // Navigation déclenchée depuis la barre de navigation : le focus reste dans
    // la barre (l'utilisateur appuie sur droite pour entrer dans l'écran).
    val keepNavFocus = remember { NavFocusHint.consume() }
    if (!keepNavFocus) RequestInitialFocus(requester, hasFocus = { hasFocus })
    Box(
        modifier
            .fillMaxSize()
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusRestorer()
            .focusRequester(requester)
            .focusGroup(),
    ) { content() }
}

/**
 * Surcouche modale pour TV : le focus y est piégé (les flèches ne ressortent pas
 * vers l'écran en dessous, qui reste composé), le focus initial est posé dedans,
 * et BACK la ferme.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ModalFocusScope(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentAlignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val requester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    RequestInitialFocus(requester, hasFocus = { hasFocus })
    if (onBack != null) BackHandler(onBack = onBack)
    Box(
        modifier
            .fillMaxSize()
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusRequester(requester)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = contentAlignment,
    ) { content() }
}

private const val INITIAL_FOCUS_ATTEMPTS = 30
private const val INITIAL_FOCUS_RETRY_MS = 100L

/**
 * D-pad HAUT/BAS quittent TOUJOURS un champ texte vers le champ précédent/suivant, clavier
 * visible ou non (sinon le champ consomme la touche et le focus reste bloqué : l'URL se
 * retrouvait saisie dans le champ Nom). GAUCHE/DROITE restent au curseur.
 */
fun Modifier.leaveOnVerticalDpad(): Modifier = composed {
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    onPreviewKeyEvent { ev ->
        val vertical = ev.key == Key.DirectionDown || ev.key == Key.DirectionUp
        if (!vertical) false
        else {
            if (ev.type == KeyEventType.KeyDown) {
                fm.moveFocus(if (ev.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
            }
            true
        }
    }
}

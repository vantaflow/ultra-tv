package com.ultratv.tv.nativeapp.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
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
    // Tactile : aucun focus programmatique (il afficherait un anneau sur un élément que personne n'a visé).
    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) return
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
            // Fond opaque : pendant l'image où l'écran sortant et l'écran entrant coexistent, le nouvel écran (encore
            // vide ou en squelette) ne laisse plus voir l'ancien à travers lui (superposition visible en changeant de page).
            .background(com.ultratv.tv.nativeapp.ui.design.Ux.Bg)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusRestorer()
            .focusRequester(requester)
            .focusGroup(),
    ) { androidx.compose.runtime.CompositionLocalProvider(LocalScreenFocus provides requester) { content() } }
    // Écran courant connu hors de son sous-arbre (menu latéral) : un focus perdu y revient, sur l'élément mémorisé.
    androidx.compose.runtime.DisposableEffect(requester) {
        CurrentScreenFocus.requester = requester
        onDispose { if (CurrentScreenFocus.requester === requester) CurrentScreenFocus.requester = null }
    }
}

/**
 * Groupe de focus de l'écran affiché, accessible au menu latéral (qui n'est pas dans le sous-arbre de l'écran). Quand
 * Compose rend un focus perdu au premier élément en haut à gauche (le menu), le menu le renvoie à l'écran par ce
 * groupe ([focusRestorer] : l'élément qui l'avait) plutôt que par un déplacement géométrique qui le faisait « sauter ».
 */
object CurrentScreenFocus {
    @Volatile var requester: FocusRequester? = null
}

/**
 * Groupe de focus de l'écran courant ([ScreenFocusHost], avec focusRestorer) : une modale qui se ferme y rend le focus,
 * donc à l'élément qui l'a ouverte.
 */
val LocalScreenFocus = androidx.compose.runtime.compositionLocalOf<FocusRequester?> { null }

/**
 * Couche d'incrustation plein écran : les modales ne sont PAS composées là où elles sont déclarées (dans un volet
 * défilant, elles se retrouvaient décalées et rognées) mais ici, par-dessus tout l'écran, rail compris.
 */
class OverlayHost {
    internal val layers = androidx.compose.runtime.mutableStateListOf<Layer>()
    internal class Layer(val state: ModalState, val content: @Composable () -> Unit)
    /** Au moins une modale ouverte (lu hors composition, ex. dans un rappel de focus). */
    val hasModal: Boolean get() = layers.isNotEmpty()
}

/**
 * Élément qui vient d'ouvrir une modale (ligne de réglage cliquée) : la modale le retient à sa création et lui rend le
 * focus à sa fermeture. Le focusRestorer de l'écran ne suffisait pas dans un volet défilant : après « Aperçu vidéo dans
 * le Direct », le focus revenait sur « Épisode suivant automatique », la ligne restée à la même hauteur.
 */
object ModalOpener {
    private var last: FocusRequester? = null
    private var at = 0L
    fun mark(r: FocusRequester) { last = r; at = android.os.SystemClock.uptimeMillis() }
    /** L'ouvreur marqué il y a moins d'une seconde (sinon null : modale ouverte autrement), consommé. */
    internal fun take(): FocusRequester? {
        val r = last.takeIf { android.os.SystemClock.uptimeMillis() - at < 1_000 }
        last = null
        return r
    }
}

/** État de focus d'une modale, partagé entre sa déclaration (dans l'écran) et son corps (dans l'OverlayLayer). */
internal class ModalState {
    val requester = FocusRequester()
    /** Élément qui a ouvert la modale ([ModalOpener]) : il reprend le focus à la fermeture. */
    val opener: FocusRequester? = ModalOpener.take()
    var hasFocus by mutableStateOf(false)
    /** La modale a déjà tenu le focus (le focus initial y est arrivé). */
    var hadFocus = false
    /** Dernier champ focalisé de la modale ([modalFocusMemory]) : cible de la reprise du focus perdu. */
    var last: FocusRequester? = null
    /** Modale en cours de fermeture : le focus a le droit d'en sortir (sinon `exit = Cancel` bloque le retour du focus). */
    var closing = false
}

internal val LocalModalState = androidx.compose.runtime.compositionLocalOf<ModalState?> { null }

/**
 * Mémorise [requester] comme dernier élément focalisé de la modale qui l'entoure : si la modale perd le focus (clavier à
 * l'écran sur certaines box), il revient sur CET élément et non sur le premier de la modale.
 */
@Composable
fun Modifier.modalFocusMemory(requester: FocusRequester): Modifier {
    val st = LocalModalState.current ?: return this
    return this.onFocusChanged { if (it.isFocused) st.last = requester }
}

val LocalOverlayHost = androidx.compose.runtime.compositionLocalOf<OverlayHost?> { null }

/** À placer une fois, en dernier enfant de la racine plein écran. */
@Composable
fun OverlayLayer(host: OverlayHost) {
    host.layers.forEach { layer -> key(layer) { layer.content() } }
}

/**
 * Surcouche modale pour TV : le focus y est piégé (les flèches ne ressortent pas
 * vers l'écran en dessous, qui reste composé), le focus initial est posé dedans,
 * et BACK la ferme. Rendue dans l'[OverlayHost] quand il existe (plein écran, centrée sur l'écran).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ModalFocusScope(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentAlignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val screen = LocalScreenFocus.current
    val state = remember { ModalState() }
    val latestBack = androidx.compose.runtime.rememberUpdatedState(onBack)
    val latestContent = androidx.compose.runtime.rememberUpdatedState(content)
    val latestModifier = androidx.compose.runtime.rememberUpdatedState(modifier)
    if (host == null) {
        ModalBody(state, null, latestBack.value, latestModifier.value, contentAlignment, latestContent.value)
    } else {
        androidx.compose.runtime.DisposableEffect(host) {
            lateinit var layer: OverlayHost.Layer
            layer = OverlayHost.Layer(state) {
                ModalBody(state, { host.layers.lastOrNull() === layer }, latestBack.value, latestModifier.value, contentAlignment, latestContent.value)
            }
            host.layers.add(layer)
            onDispose {
                host.layers.remove(layer)
                // Le corps de la modale est encore en place (l'OverlayLayer ne le retire qu'à sa prochaine recomposition) :
                // on rend le focus MAINTENANT. Sinon, l'élément focalisé disparaissant, Compose pose le focus sur le premier
                // élément en haut à gauche (le menu latéral), qui le renvoie à droite au hasard de la géométrie : la sélection
                // « saute », et une rubrique des Réglages qui suit le focus faisait disparaître le volet (et la modale suivante).
                state.closing = true
                if (state.hasFocus) returnFocus(host.layers.lastOrNull()?.state?.requester, state.opener, screen)
            }
        }
    }
}

/**
 * Focus rendu à la modale restée dessous, sinon à l'élément qui a ouvert la modale, sinon à l'écran (son focusRestorer
 * revient à l'élément qui l'avait).
 */
private fun returnFocus(underneath: FocusRequester?, opener: FocusRequester?, screen: FocusRequester?) {
    fun tryFocus(r: FocusRequester?) = r != null && runCatching { r.requestFocus() }.getOrDefault(false)
    if (!tryFocus(underneath) && !tryFocus(opener) && screen != null) runCatching { screen.requestFocus() }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ModalBody(
    state: ModalState,
    /** Modale du dessus (null : pas d'OverlayHost, aucune reprise automatique du focus). */
    isTopmost: (() -> Boolean)?,
    onBack: (() -> Unit)?,
    modifier: Modifier,
    contentAlignment: androidx.compose.ui.Alignment,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val requester = state.requester
    RequestInitialFocus(requester, hasFocus = { state.hasFocus })
    // Focus PERDU alors que la modale est toujours au premier plan (la vue Compose perd le focus quand le clavier à l'écran
    // apparaît ou disparaît sur certaines box Google TV, ou l'élément focalisé disparaît) : on le reprend, sur le dernier
    // élément focalisé de la modale (focusRestorer). Sans cela il retombait derrière la modale et le clavier se refermait.
    if (isTopmost != null && !com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        LaunchedEffect(state.hasFocus) {
            if (state.hasFocus || !state.hadFocus) return@LaunchedEffect
            delay(MODAL_REFOCUS_DELAY_MS)
            if (!state.hasFocus && isTopmost()) {
                val back = state.last?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false
                if (!back) runCatching { requester.requestFocus() }
            }
        }
    }
    if (onBack != null) BackHandler(onBack = onBack)
    Box(
        modifier
            .fillMaxSize()
            .onFocusChanged { state.hasFocus = it.hasFocus; if (it.hasFocus) state.hadFocus = true }
            .focusRestorer()
            .focusRequester(requester)
            .focusProperties { exit = { if (state.closing) FocusRequester.Default else FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = contentAlignment,
    ) { androidx.compose.runtime.CompositionLocalProvider(LocalModalState provides state) { content() } }
}

/** Délai avant de reprendre le focus perdu par une modale : laisse passer une transition légitime (modale ouverte par-dessus). */
internal const val MODAL_REFOCUS_DELAY_MS = 150L

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

/**
 * Marge de débordement à gauche d'une rangée horizontale (LazyRow) : une LazyRow rogne tout ce qui dépasse de ses
 * bords, donc la PREMIÈRE carte, agrandie et cerclée au focus, était coupée à gauche. La rangée est élargie de [bleed]
 * vers la gauche puis décalée d'autant : les cartes restent à la même position, mais la zone de rognage commence
 * [bleed] plus tôt. À combiner avec `contentPadding(start = bleed)`. Le parent doit disposer de cette marge.
 */
fun Modifier.rowBleedStart(bleed: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = (constraints.minWidth + extra).coerceAtMost(maxW), maxWidth = maxW))
    val w = (placeable.width - extra).coerceAtLeast(0)
    layout(w, placeable.height) { placeable.place(-extra, 0) }
}

/** Débordement standard des rangées d'affiches : agrandissement au focus (≈ 6 %) + liseré. */
val RowBleed: androidx.compose.ui.unit.Dp get() = 24.design

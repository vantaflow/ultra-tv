package com.ultratv.tv.nativeapp.ui.trailer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.tmdb.TmdbTrailer
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.trailerBack10
import com.ultratv.tv.nativeapp.i18n.trailerClose
import com.ultratv.tv.nativeapp.i18n.trailerError
import com.ultratv.tv.nativeapp.i18n.trailerFwd10
import com.ultratv.tv.nativeapp.i18n.trailerLoading
import com.ultratv.tv.nativeapp.i18n.trailerPause
import com.ultratv.tv.nativeapp.i18n.trailerPlay
import com.ultratv.tv.nativeapp.i18n.trailerQrHint
import com.ultratv.tv.nativeapp.i18n.trailerShowQr
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.QrCode
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import kotlinx.coroutines.delay

private const val ICON_PLAY = "M7 4v16l13-8z"
private const val ICON_PAUSE = "M7 4h3.5v16H7zM13.5 4H17v16h-3.5z"
private const val ICON_REW = "M11 5L4 12l7 7M20 5l-7 7 7 7"
private const val ICON_FWD = "M13 5l7 7-7 7M4 5l7 7-7 7"
private const val ICON_CLOSE = "M6 6l12 12M18 6L6 18"

/**
 * Bande-annonce lue DANS l'appli : fenêtre plein écran noire, lecteur officiel YouTube (IFrame Player API) en arrière-plan
 * dans une WebView, commandes à nous en surimpression (lecture / pause, −10 s / +10 s, progression, titre, Fermer).
 * Pilotable au D-pad : focus initial sur Lecture / Pause, Gauche / Droite = −10 / +10 s, touches média, Retour ferme.
 * Les commandes se masquent après 3 s de lecture. Aucune ouverture de l'appli ou du site YouTube : une erreur
 * (vidéo privée, intégration interdite, WebView absente…) affiche un message avec Fermer ; le lien en QR code n'est
 * proposé que si l'utilisateur le choisit.
 *
 * Rendu par [ModalFocusScope] (surcouche de l'appli : focus piégé, Retour ferme) : peut être posé n'importe où dans un écran.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TrailerPlayer(key: String, title: String, onClose: () -> Unit) {
    val D = LocalDs.current
    val touch = LocalTouch.current
    val ctx = LocalContext.current
    val state = remember(key) { mutableStateOf(TrailerState()) }
    val dispatch: (TrailerEvent) -> Unit = remember(key) { { e -> state.value = state.value.reduce(e) } }
    // La WebView peut manquer ou être désactivée (certaines box) : sa création lève alors une exception.
    val host = remember(key) { TrailerHost.create(ctx, key, dispatch) }
    LaunchedEffect(host) { if (host == null) dispatch(TrailerEvent.Failed(TrailerErrorKind.NoWebView)) }
    // Aucune réponse du lecteur (hors ligne, WebView trop ancienne) : message clair plutôt qu'un écran noir.
    LaunchedEffect(host) {
        if (host == null) return@LaunchedEffect
        delay(TRAILER_NO_ANSWER_MS)
        if (!state.value.ready && state.value.phase != TrailerPhase.Error) dispatch(TrailerEvent.Failed(TrailerErrorKind.Unknown))
    }

    // Cycle de vie : pause en arrière-plan, libération complète à la fermeture.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(host, lifecycle) {
        val observer = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_PAUSE -> host?.pause()
                Lifecycle.Event.ON_RESUME -> host?.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); host?.release() }
    }

    val st = state.value
    var showQr by remember { mutableStateOf(false) }
    var osdVisible by remember { mutableStateOf(true) }
    var lastActivity by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val poke: () -> Unit = { lastActivity = SystemClock.uptimeMillis(); osdVisible = true }
    // Masquage des commandes après 3 s de lecture (elles restent affichées en pause, chargement, fin ou erreur).
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            if (osdVisible && osdShouldHide(lastActivity, SystemClock.uptimeMillis(), osdKeepVisible(state.value))) osdVisible = false
        }
    }

    val failed = st.phase == TrailerPhase.Error
    val live = host != null && !failed
    val toggle: () -> Unit = { if (live) toggleCommands(state.value).forEach { host?.send(it) } }
    val skip: (Int) -> Unit = { delta ->
        if (live) {
            val to = clampSeek(state.value.positionS, delta.toDouble(), state.value.durationS)
            host?.send(TrailerJs.seek(to))
            dispatch(TrailerEvent.Seeked(to))
        }
    }
    val playFocus = remember { FocusRequester() }
    val controlsShown = live && (osdVisible || !touch)
    // Une erreur ou un échec : la page est libérée tout de suite (plus de son ni de réseau derrière le message).
    LaunchedEffect(failed) { if (failed) host?.release() }

    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                // Premier focus sur Lecture / Pause (le plus bas dans la maquette, mais le premier composé ci-dessous).
                .focusProperties { enter = { if (controlsShown && !touch) playFocus else FocusRequester.Default } }
                .focusGroup()
                .onPreviewKeyEvent { ev ->
                    val action = trailerKeyAction(ev.key.nativeKeyCode, osdVisible)
                    if (action == null) {
                        if (ev.type == KeyEventType.KeyDown) poke()
                        false
                    } else {
                        // Touche prise en compte : on avale aussi le relâchement, pour qu'un bouton focalisé ne clique pas.
                        if (ev.type == KeyEventType.KeyDown) {
                            poke()
                            when (action) {
                                TrailerAction.Toggle -> toggle()
                                TrailerAction.Play -> if (live) host?.send(TrailerJs.PLAY)
                                TrailerAction.Pause -> if (live) host?.send(TrailerJs.PAUSE)
                                TrailerAction.SkipBack -> skip(-TRAILER_SKIP_S)
                                TrailerAction.SkipForward -> skip(TRAILER_SKIP_S)
                                TrailerAction.Close -> onClose()
                                TrailerAction.Reveal -> Unit
                            }
                        }
                        true
                    }
                },
        ) {
            if (live && host != null) {
                AndroidView(
                    // Un cadre ne peut avoir qu'un parent : si la surcouche est recomposée ailleurs, on le détache d'abord.
                    factory = { (host.frame.parent as? android.view.ViewGroup)?.removeView(host.frame); host.frame },
                    modifier = Modifier.fillMaxSize(),
                )
                if (touch) {
                    // Tactile : la WebView avalerait les appuis ; un appui affiche les commandes, puis bascule lecture / pause.
                    Box(Modifier.fillMaxSize().pointerInput(osdVisible) { detectTapGestures { if (osdVisible) toggle() else poke() } })
                }
            }
            if (live && !st.ready && st.phase == TrailerPhase.Loading || live && st.phase == TrailerPhase.Buffering) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(if (touch) 40.dp else 64.design).semantics { contentDescription = D.trailerLoading })
                }
            }

            if (failed || host == null) {
                TrailerError(
                    kind = st.error ?: TrailerErrorKind.NoWebView, qrUrl = TmdbTrailer.webUrl(key), showQr = showQr,
                    onShowQr = { showQr = true }, onClose = onClose, touch = touch,
                )
            } else if (osdVisible || !touch) {
                Controls(
                    state = st, title = title, visible = osdVisible, touch = touch, playFocus = playFocus,
                    onToggle = { poke(); toggle() }, onBack = { poke(); skip(-TRAILER_SKIP_S) }, onForward = { poke(); skip(TRAILER_SKIP_S) },
                    onSeek = { ratio -> poke(); val to = seekFromRatio(ratio, state.value.durationS); host.send(TrailerJs.seek(to)); dispatch(TrailerEvent.Seeked(to)) },
                    onClose = onClose,
                )
            }
        }
    }
}

/** Tailles : dp au toucher, cotes de la maquette (design) sur TV. */
private fun sz(touch: Boolean, tvPx: Int, touchDp: Int): Dp = if (touch) touchDp.dp else tvPx.design

@Composable
private fun Controls(
    state: TrailerState, title: String, visible: Boolean, touch: Boolean, playFocus: FocusRequester,
    onToggle: () -> Unit, onBack: () -> Unit, onForward: () -> Unit, onSeek: (Float) -> Unit, onClose: () -> Unit,
) {
    val D = LocalDs.current
    // Invisibles mais toujours focalisables sur TV : un appui sur une touche les réaffiche sans perdre le focus.
    Box(Modifier.fillMaxSize().alpha(if (visible) 1f else 0f)) {
        // Bas : progression puis boutons. Composé AVANT le haut pour que Lecture / Pause soit le premier focalisable.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = sz(touch, 96, 20), vertical = sz(touch, 48, 16)),
            verticalArrangement = Arrangement.spacedBy(sz(touch, 24, 12)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sz(touch, 20, 10))) {
                Text(trailerClock(state.positionS), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = if (touch) 13.sp else (24 / 2f).sp)
                ProgressBar(progressRatio(state.positionS, state.durationS), touch, Modifier.weight(1f), onSeek)
                Text(trailerClock(state.durationS), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = if (touch) 13.sp else (24 / 2f).sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                RoundButton(ICON_REW, D.trailerBack10, sz(touch, 96, 48), sz(touch, 40, 22), onBack)
                Spacer(Modifier.width(sz(touch, 32, 20)))
                RoundButton(
                    if (state.playing) ICON_PAUSE else ICON_PLAY, if (state.playing) D.trailerPause else D.trailerPlay,
                    sz(touch, 120, 64), sz(touch, 52, 30), onToggle, fill = true, modifier = Modifier.focusRequester(playFocus),
                )
                Spacer(Modifier.width(sz(touch, 32, 20)))
                RoundButton(ICON_FWD, D.trailerFwd10, sz(touch, 96, 48), sz(touch, 40, 22), onForward)
            }
        }
        // Haut : titre et Fermer.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(horizontal = sz(touch, 96, 16), vertical = sz(touch, 40, 12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 18.sp else (36 / 2f).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(sz(touch, 24, 12)))
            RoundButton(ICON_CLOSE, D.trailerClose, sz(touch, 80, 48), sz(touch, 32, 22), onClose)
        }
    }
}

/** Bouton rond focalisable (fond sombre ; au focus blanc, comme le reste de l'appli). Couleurs fixes : le fond est la vidéo. */
@Composable
private fun RoundButton(
    icon: String, label: String, box: Dp, glyph: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Boolean = false,
) {
    FocusSurface(
        onClick = onClick, shape = CircleShape, bg = if (fill) Color.White else Color(0xB31C1C21), focusedBg = Color.White,
        modifier = modifier.size(box).semantics { contentDescription = label },
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DIcon(icon, glyph, if (fill || focused) Color(0xFF0A0A0C) else Color.White, fill = fill, strokeWidth = 2.2f)
        }
    }
}

/** Barre de progression ; au toucher, un appui saute à cet endroit. */
@Composable
private fun ProgressBar(ratio: Float, touch: Boolean, modifier: Modifier, onSeek: (Float) -> Unit) {
    val h = sz(touch, 10, 5)
    Box(
        modifier.height(if (touch) 24.dp else h)
            .then(if (touch) Modifier.pointerInput(Unit) { detectTapGestures { o -> onSeek(o.x / size.width.toFloat()) } } else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(h).background(Color(0x55FFFFFF), RoundedCornerShape(h)))
        Box(Modifier.fillMaxWidth(ratio).height(h).background(Color.White, RoundedCornerShape(h)))
    }
}

/** Erreur : message, Fermer (focus initial) et, au choix de l'utilisateur, le lien en QR code. */
@Composable
private fun TrailerError(kind: TrailerErrorKind, qrUrl: String, showQr: Boolean, onShowQr: () -> Unit, onClose: () -> Unit, touch: Boolean) {
    val D = LocalDs.current
    // Le QR ne sert à rien pour une vidéo supprimée.
    val canQr = kind != TrailerErrorKind.NotFound
    Column(
        Modifier.fillMaxSize().padding(sz(touch, 120, 24)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(
            D.trailerError(kind), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = if (touch) 16.sp else (32 / 2f).sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(if (touch) 1f else 0.6f),
        )
        Spacer(Modifier.height(sz(touch, 40, 20)))
        Row(horizontalArrangement = Arrangement.spacedBy(sz(touch, 24, 12)), verticalAlignment = Alignment.CenterVertically) {
            PillAction(D.trailerClose, touch, onClose)
            if (canQr && !showQr) PillAction(D.trailerShowQr, touch, onShowQr)
        }
        if (canQr && showQr) {
            Spacer(Modifier.height(sz(touch, 40, 20)))
            QrCode(qrUrl, sz(touch, 320, 180))
            Spacer(Modifier.height(sz(touch, 16, 8)))
            Text(D.trailerQrHint, color = Color(0xFFC4C4CC), fontFamily = Manrope, fontSize = if (touch) 13.sp else (24 / 2f).sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PillAction(label: String, touch: Boolean, onClick: () -> Unit) {
    val h = sz(touch, 76, 48)
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(h / 2), bg = Color(0xFF26262D), focusedBg = Color.White, modifier = Modifier.height(h)) { focused ->
        Box(Modifier.fillMaxHeight().padding(horizontal = sz(touch, 40, 20)), contentAlignment = Alignment.Center) {
            Text(
                label, color = if (focused) Color(0xFF0A0A0C) else Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = if (touch) 14.sp else (28 / 2f).sp, maxLines = 1,
            )
        }
    }
}

/** Pont JavaScript → Kotlin. Les appels arrivent sur un fil de la WebView : on repasse sur le fil principal, et plus rien après la fermeture. */
internal class TrailerBridge(private val onEvent: (TrailerEvent) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var closed = false
    private fun post(e: TrailerEvent) { if (!closed) main.post { if (!closed) onEvent(e) } }
    @JavascriptInterface fun onReady() = post(TrailerEvent.Ready)
    @JavascriptInterface fun onState(code: Int) = post(TrailerEvent.PlayerState(code))
    @JavascriptInterface fun onTime(position: Double, duration: Double) = post(TrailerEvent.Time(position, duration))
    @JavascriptInterface fun onError(code: Int) = post(TrailerEvent.Error(code))
}

/** La WebView du lecteur (dans un cadre : on la détache avant `destroy`) et son pont. */
internal class TrailerHost private constructor(val frame: FrameLayout, private val web: WebView, private val bridge: TrailerBridge) {
    private var released = false

    /** Envoie un ordre à la page (`ut.play()`, `ut.seek(12.00)`…). */
    fun send(js: String) { if (!released) web.evaluateJavascript(js, null) }

    fun pause() { if (released) return; web.evaluateJavascript(TrailerJs.PAUSE, null); web.onPause() }
    fun resume() { if (!released) web.onResume() }

    /** Fermeture : plus d'événements, page vidée, WebView détachée puis détruite (aucune fuite, aucun son résiduel). */
    fun release() {
        if (released) return
        released = true
        bridge.closed = true
        runCatching {
            web.stopLoading()
            web.removeJavascriptInterface(TRAILER_BRIDGE)
            web.webViewClient = WebViewClient()
            frame.removeAllViews()
            web.loadUrl("about:blank")
            web.onPause()
            web.destroy()
        }
    }

    companion object {
        /**
         * Crée la WebView et charge la page du lecteur. Null si le composant WebView manque ou est désactivé (certaines box :
         * le constructeur lève alors `AndroidRuntimeException`). L'origine https de la page ([TRAILER_BASE_URL]) donne à
         * YouTube le Referer exigé pour les intégrations (sinon erreurs 152/153).
         */
        @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
        fun create(ctx: Context, key: String, onEvent: (TrailerEvent) -> Unit): TrailerHost? = runCatching {
            val web = WebView(ctx)
            val bridge = TrailerBridge(onEvent)
            web.setBackgroundColor(AColor.BLACK)
            // Le focus reste dans Compose : la WebView ne le prend pas (le D-pad serait sinon capté par la page).
            web.isFocusable = false
            web.isFocusableInTouchMode = false
            with(web.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true                       // le lecteur YouTube en a besoin
                mediaPlaybackRequiresUserGesture = false       // lecture automatique à l'ouverture
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
            }
            web.addJavascriptInterface(bridge, TRAILER_BRIDGE)
            web.webViewClient = object : WebViewClient() {
                // Aucune navigation hors de la page du lecteur : pas de bascule vers YouTube.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    onEvent(TrailerEvent.Failed(TrailerErrorKind.Unknown))
                    return true
                }
            }
            val frame = FrameLayout(ctx).apply { addView(web, FrameLayout.LayoutParams(-1, -1)) }
            web.loadDataWithBaseURL(TRAILER_BASE_URL, trailerPageHtml(key), "text/html", "utf-8", null)
            TrailerHost(frame, web, bridge)
        }.getOrNull()
    }
}

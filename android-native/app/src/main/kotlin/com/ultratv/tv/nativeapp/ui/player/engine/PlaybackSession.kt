package com.ultratv.tv.nativeapp.ui.player.engine

import android.content.Context
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.PlaybackAdapter
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Phase { LOADING, PLAYING, ENDED, ERROR }

/** Avis discret à l'utilisateur (toast) ; le texte est localisé par l'interface. */
enum class Notice { USING_EXO, USING_VLC, USING_SOFTWARE, RETRYING }

data class SessionState(
    val phase: Phase = Phase.LOADING,
    val error: PlayErrorKind? = null,
    val combo: Combo = Combo(EngineKind.EXO, DecoderMode.AUTO),
    val bufferPreset: BufferPreset = BufferPreset.AUTO,
    val hasPicture: Boolean = false,
    /** Flux protégé (Widevine / ClearKey) : Media3 seul, aucun repli ; le choix VLC et le lecteur externe ne sont pas proposés. */
    val isProtected: Boolean = false,
)

/**
 * Une session de lecture : UN seul moteur vivant à la fois (connexion unique du fournisseur : `release()`
 * précède toujours l'ouverture suivante). Gère le repli automatique moteur/décodage, la mémorisation par
 * chaîne, la nouvelle tentative après un 403 et l'adaptation du débit pendant la lecture.
 */
/** Direct bloqué en chargement après avoir joué : délai avant reconnexion. */
private const val STALL_MS = 12_000L

@UnstableApi
class PlaybackSession(
    private val ctx: Context,
    private val scope: CoroutineScope,
    val container: FrameLayout,
    private val settings: () -> ResolvedPlayback,
    private val memory: ChannelPlaybackMemory,
    private val network: NetworkMonitor,
    private val isLive: Boolean,
    private val autoFrameRate: Boolean,
    private val userAgent: String,
    private val textureView: Boolean = false,
    /** Style des sous-titres et langues préférées au moment du lancement du moteur. */
    private val subtitles: () -> com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings = { com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings() },
    /** Langue de l'interface (ISO 639-1) : langue audio par défaut quand aucune préférence n'est réglée. */
    private val uiLanguage: () -> String = { java.util.Locale.getDefault().language },
    /** Propriétés de lecture (flux protégé) d'une chaîne « fournisseur:chaîne » ; null = aucune recherche (flux ordinaires seulement). */
    private val propsLookup: (suspend (String) -> com.ultratv.tv.nativeapp.data.m3u.StreamProps?)? = null,
    /** Contrôle d'un flux protégé avant d'ouvrir un lecteur (système pris en charge, propriétés complètes) ; null = lecture possible. */
    private val drmPrecheck: (com.ultratv.tv.nativeapp.data.m3u.StreamProps) -> PlayErrorKind? = { DrmMedia.precheck(it) },
    private val engineFactory: (EngineKind, EngineConfig) -> PlayerEngine = { k, c -> if (k == EngineKind.EXO) ExoEngine(ctx, c) else VlcEngine(ctx, c) },
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    var engine: PlayerEngine? = null
        private set

    private var url = ""
    private var props: com.ultratv.tv.nativeapp.data.m3u.StreamProps? = null
    private val isProtected get() = props?.isProtected == true
    private var startJob: Job? = null
    private var key: String? = null
    private var resumeMs = 0L
    private var tried = mutableSetOf<Combo>()
    private var sameRetries = 0
    private var combo = Combo(EngineKind.EXO, DecoderMode.AUTO)
    private var presetOverride: BufferPreset? = null
    private var eventsJob: Job? = null
    private var watchdog: Job? = null
    /** Contrôle de l'image réelle après la première image (décodeur qui ne rend que du noir). */
    private var blackJob: Job? = null
    private var rememberJob: Job? = null
    private val adapter = PlaybackAdapter()
    private var firstFrame = false
    private var adaptJob: Job? = null
    private var manualSwitch = false
    private var launchedAtNs = 0L
    // Reconnexion automatique du direct (serveurs qui ferment la session, flux gelé).
    private var reconnects = 0
    private var everPlayed = false
    private var reconnectJob: Job? = null
    private var stallJob: Job? = null
    private var stableJob: Job? = null
    /** Nouvelle tentative / repli programmés par [onError] : annulés par un zap ou un nouveau moteur (double connexion sinon). */
    private var errorJob: Job? = null

    /**
     * Reconnexion automatique autorisée pour la lecture en cours : VRAI direct seulement. Un replay (« Depuis le
     * début ») ou le relais du différé ont une FIN normale — les relancer en boucle n'aurait pas de sens.
     * Fixé par le lecteur avant chaque [start].
     */
    var liveReconnect: Boolean = true
    private val reconnectAllowed get() = isLive && liveReconnect

    /** Son coupé (aperçu du Direct) : appliqué au moteur en place ET à chaque moteur recréé (repli, nouvelle tentative). */
    var muted: Boolean = false
        set(v) { field = v; engine?.setMuted(v) }

    /**
     * Vue vidéo à placer dans un nouvel hôte (AndroidView de l'aperçu ou du plein écran). Retirée de l'ancien parent
     * en prévenant le moteur (LibVLC coupe sa sortie vidéo avant la destruction de la surface), puis relancée une fois
     * rattachée — sans recharger le flux.
     */
    fun takeVideoView(): android.view.View {
        val old = container.parent as? android.view.ViewGroup
        // Premier hôte : rien à prévenir. Ensuite, la vue a déjà été affichée ailleurs (même si son ancien hôte a déjà
        // quitté l'écran) : la surface a été ou va être détruite.
        if (!hostedOnce) old?.removeView(container)
        else {
            engine?.onVideoDetaching()
            old?.removeView(container)
            container.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {
                    v.removeOnAttachStateChangeListener(this)
                    // Après la passe de mise en page qui crée la nouvelle surface.
                    v.post { engine?.onVideoReattached() }
                }
                override fun onViewDetachedFromWindow(v: android.view.View) {}
            })
        }
        hostedOnce = true
        container.visibility = android.view.View.VISIBLE
        return container
    }
    private var hostedOnce = false

    /** Ouvre [url]. [channelKey] = « fournisseur:chaîne » pour la mémoire par chaîne (null = pas de mémoire). */
    fun start(url: String, channelKey: String?, resumeMs: Long = 0, props: com.ultratv.tv.nativeapp.data.m3u.StreamProps? = null) {
        startJob?.cancel()
        val lookup = propsLookup
        if (props == null && lookup != null && channelKey != null) {
            // Les propriétés d'un flux protégé sont lues dans la base (jamais dans l'URL) avant d'ouvrir le moteur.
            startJob = scope.launch { begin(url, channelKey, resumeMs, runCatching { lookup(channelKey) }.getOrNull()) }
        } else begin(url, channelKey, resumeMs, props)
    }

    private fun begin(url: String, channelKey: String?, resumeMs: Long, found: com.ultratv.tv.nativeapp.data.m3u.StreamProps?) {
        this.url = url; this.key = channelKey; this.resumeMs = resumeMs
        this.props = found?.takeIf { it.isProtected }
        tried = mutableSetOf(); sameRetries = 0; manualSwitch = false
        reconnects = 0; everPlayed = false; reconnectJob?.cancel(); stallJob?.cancel(); stableJob?.cancel(); errorJob?.cancel()
        val s = settings()
        presetOverride = channelKey?.let { memory.bufferPreset(it) }
        val protectedNow = isProtected
        val next = PlaybackPlanner.initial(s.engine, s.decoder, if (protectedNow) null else channelKey?.let { memory.combo(it) }, protectedNow)
        props?.let(drmPrecheck)?.let { bad ->
            // Appareil sans le système demandé ou propriétés incomplètes : erreur claire, AUCUN moteur (et jamais VLC).
            watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel(); eventsJob?.cancel(); adaptJob?.cancel()
            releaseEngine(); combo = next
            _state.value = SessionState(Phase.ERROR, bad, next, presetOverride ?: s.bufferPreset, false, true)
            return
        }
        val e = engine
        // Zapping : même moteur, même décodage, même tampon → on enchaîne le flux sur le moteur EN PLACE au lieu de le
        // détruire et d'en recréer un (lecteur, vue, décodeurs : plusieurs centaines de ms sur une box modeste).
        // Réglages de sous-titres changés depuis la création du moteur : on le recrée (ils sont fixés à sa création).
        if (isLive && e != null && e.reusable && next == combo && bufferFor(s) == launchedBuffer && subtitles() == launchedSubs && _state.value.phase != Phase.ERROR) {
            watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel()
            tried = mutableSetOf(combo); firstFrame = false; launchedAtNs = System.nanoTime()
            _state.value = SessionState(Phase.LOADING, null, combo, presetOverride ?: s.bufferPreset, false, protectedNow)
            loadOn(e)
        } else {
            combo = next
            launch(combo)
        }
        adaptJob?.cancel()
        adaptJob = scope.launch { while (true) { delay(5_000); applyQualityLimit(adapter.tick(System.currentTimeMillis())) } }
    }

    /** « Réessayer » / retour sur l'appli : tout repart de zéro (sinon, compteur à 8 = écran d'erreur à la 1re coupure). */
    fun retry() { tried = mutableSetOf(); sameRetries = 0; reconnects = 0; everPlayed = false; launch(combo) }

    /** Changement manuel (pilule « Lecteur ») : on le mémorise tout de suite pour cette chaîne. */
    fun switchTo(c0: Combo) {
        val c = PlaybackPlanner.manualCombo(c0, isProtected)   // flux protégé : le choix VLC est ignoré
        manualSwitch = true; tried = mutableSetOf(); combo = c; if (!isProtected) key?.let { memory.remember(it, c, null) }; launch(c)
    }

    /** Choix manuel de la piste audio : appliqué, puis sa langue est retenue pour ce titre (jamais l'identifiant de piste). */
    fun selectAudio(id: String) {
        val e = engine ?: return
        val lang = e.audioTracks().firstOrNull { it.id == id }?.language
        e.selectAudio(id)
        if (lang != null) key?.let { memory.rememberAudioLang(it, lang) }
    }

    fun setBufferPreset(p: BufferPreset) { presetOverride = p; key?.let { memory.remember(it, null, p) }; launch(combo) }

    /** Ouvre le flux sur [e] : en flux protégé, avec ses propriétés de licence. */
    private fun loadOn(e: PlayerEngine, resume: Long = resumeMs) {
        val p = props
        if (p != null && p.isProtected) e.loadProtected(url, resume, p) else e.load(url, resume)
    }

    private fun launch(c0: Combo) {
        val c = PlaybackPlanner.manualCombo(c0, isProtected)
        eventsJob?.cancel(); watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel()
        // Nouveau moteur : une reconnexion ou une nouvelle tentative encore en attente rechargerait un flux qui joue.
        reconnectJob?.cancel(); stallJob?.cancel(); stableJob?.cancel(); errorJob?.cancel()
        releaseEngine()
        combo = c; tried += c; firstFrame = false; launchedAtNs = System.nanoTime()
        val s = settings()
        val buffer = bufferFor(s)
        launchedBuffer = buffer
        _state.value = SessionState(Phase.LOADING, null, c, presetOverride ?: s.bufferPreset, false, isProtected)
        // Box basse : on libère les images en mémoire avant que le décodeur ne réclame la sienne.
        if (s.lowRam) runCatching { coil3.SingletonImageLoader.get(ctx).memoryCache?.clear() }
        val sub = subtitles()
        launchedSubs = sub
        // Sous-titres : jamais activés d'office ; seulement si l'utilisateur les avait activés la dernière fois.
        // Langue audio : choix manuel mémorisé pour ce titre > réglage Langues > langue de l'interface (jamais « la première piste »).
        val ui = uiLanguage()
        val tc = com.ultratv.tv.nativeapp.data.subtitles.TrackChoice
        val audioPref = tc.effectivePreferred(sub.languages.audio, key?.let { memory.audioLang(it) }, ui)
        val textPref = if (sub.autoOn) tc.effectivePreferred(sub.languages.text, null, ui) else emptyList()
        val cfg = EngineConfig(c.decoder, buffer, isLive, autoFrameRate, userAgent, sub.style, audioPref, textPref, textOff = !sub.autoOn, uiLanguage = ui, textureView = textureView)
        val e = runCatching { engineFactory(c.engine, cfg) }.getOrElse { onError(PlayErrorKind.UNKNOWN); return }
        if (isProtected && !e.supportsProtected) {
            runCatching { e.release() }
            _state.value = _state.value.copy(phase = Phase.ERROR, error = PlayErrorKind.DRM_UNSUPPORTED)
            return
        }
        engine = e
        container.removeAllViews(); container.addView(e.view, FrameLayout.LayoutParams(-1, -1))
        if (muted) e.setMuted(true)
        // Masqué en quittant le lecteur (couche vidéo retirée tout de suite) : toujours réaffiché au lancement suivant.
        container.visibility = android.view.View.VISIBLE
        e.limitQuality(s.maxVideoHeight, PlaybackAdapter.scaleBitrate(s.maxVideoBitrateBps, adapter.level))
        eventsJob = scope.launch { e.events.collect { onEvent(it, e) } }
        loadOn(e)
    }

    /** Tampon effectif du moteur lancé : un zap ne réutilise le moteur que si ce tampon n'a pas changé. */
    private var launchedBuffer: BufferParams? = null
    private var launchedSubs: com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings? = null

    private fun bufferFor(s: ResolvedPlayback): BufferParams {
        val preset = presetOverride
        return if (preset != null) BufferPlanner.resolve(preset, CustomBuffer(), s.heapClassMb, s.lowRam) else s.buffer
    }

    private fun applyQualityLimit(level: Int) {
        val s = settings()
        engine?.limitQuality(s.maxVideoHeight, PlaybackAdapter.scaleBitrate(s.maxVideoBitrateBps, level))
    }

    private fun onEvent(ev: EngineEvent, e: PlayerEngine) {
        when (ev) {
            EngineEvent.Buffering -> if (firstFrame) {
                android.util.Log.i("UltraPlay", "rebuffer engine=${combo.engine}"); network.onRebuffer(); applyQualityLimit(adapter.onRebuffer(System.currentTimeMillis()))
                // Direct figé en chargement (serveur qui ne renvoie plus rien sans fermer) : reconnexion au bout de 12 s.
                if (reconnectAllowed && stallJob?.isActive != true) stallJob = scope.launch { delay(STALL_MS); reconnectLive() }
            } else Unit
            EngineEvent.Ready -> { stallJob?.cancel(); if (!firstFrame && watchdog?.isActive != true) armPictureWatchdog(e) }
            EngineEvent.FirstFrame -> {
                // Déjà vue pour ce lancement : le moteur la resignale quand sa surface change (aperçu → plein écran) ;
                // la durée depuis le lancement n'aurait plus de sens (41 s affichées pour 1,7 s réelles).
                val again = firstFrame
                firstFrame = true; watchdog?.cancel(); blackJob?.cancel(); stallJob?.cancel()
                everPlayed = true
                // 30 s de lecture stable : le compteur de reconnexions repart de zéro.
                stableJob?.cancel(); stableJob = scope.launch { delay(30_000); reconnects = 0 }
                // Mesure (jamais d'URL) : moteur, décodage et délai jusqu'à la première image.
                if (again) android.util.Log.i("UltraPlay", "picture again (surface changed) engine=${combo.engine}")
                else android.util.Log.i("UltraPlay", "firstFrame engine=${combo.engine} decoder=${combo.decoder} ms=${(System.nanoTime() - launchedAtNs) / 1_000_000}")
                _state.value = _state.value.copy(phase = Phase.PLAYING, error = null, hasPicture = true)
                armBlackPictureCheck(e)
                // La combinaison est retenue pour la chaîne si elle a demandé un repli ou un choix manuel et tient 5 s.
                rememberJob?.cancel()
                rememberJob = scope.launch { delay(5_000); if (tried.size > 1 || manualSwitch) key?.let { memory.remember(it, combo, null) } }
                sameRetries = 0
            }
            // Direct : une « fin » est une session fermée par le serveur, pas la fin du programme → on se reconnecte.
            EngineEvent.Ended -> if (reconnectAllowed) reconnectLive() else _state.value = _state.value.copy(phase = Phase.ENDED)
            is EngineEvent.Error -> onError(ev.kind)
        }
    }

    /** Le son passe mais aucune image au bout de 8 s alors que le flux a une piste vidéo : on change de décodeur/moteur. */
    private fun armPictureWatchdog(e: PlayerEngine) {
        watchdog = scope.launch {
            delay(8_000)
            if (!firstFrame && e.hasVideo) onError(PlayErrorKind.NO_PICTURE)
        }
    }

    /**
     * Le décodeur dit avoir rendu une image, mais l'écran peut rester noir (HEVC 10 bits sur certains Exynos, son seul) :
     * trois vignettes entièrement noires sur ~6 s au démarrage → même traitement que « aucune image » (décodeur suivant).
     * Une copie impossible (surface pas prête) ne conclut rien.
     */
    private fun armBlackPictureCheck(e: PlayerEngine) {
        blackJob = scope.launch {
            delay(3_000)
            repeat(3) { i ->
                val px = FrameProbe.sample(e.view) ?: return@launch
                if (!FrameProbe.isBlack(px)) return@launch
                if (i < 2) delay(1_500)
            }
            if (engine === e && e.hasVideo) {
                android.util.Log.i("UltraPlay", "black picture engine=${combo.engine} decoder=${combo.decoder}")
                onError(PlayErrorKind.NO_PICTURE)
            }
        }
    }

    private fun onError(kind0: PlayErrorKind) {
        // Flux protégé : décodeur sécurisé absent ou image noire = niveau de sécurité de l'appareil insuffisant.
        val kind = if (isProtected && (kind0 == PlayErrorKind.DECODER || kind0 == PlayErrorKind.NO_PICTURE)) PlayErrorKind.DRM_UNSUPPORTED else kind0
        android.util.Log.i("UltraPlay", "error kind=$kind engine=${combo.engine} decoder=${combo.decoder}")
        // Direct qui jouait : coupure du serveur ou du réseau → reconnexion, pas de changement de moteur.
        // VLC ne donne pas de code : après un rechargement, une coupure serveur arrive classée FORMAT ; si le flux a déjà
        // joué, c'est une coupure, pas un format illisible.
        val k = if (everPlayed && kind == PlayErrorKind.FORMAT && combo.engine == EngineKind.VLC) PlayErrorKind.NETWORK else kind
        if (reconnectAllowed && everPlayed && PlaybackPlanner.shouldReconnectLive(k)) { reconnectLive(); return }
        network.onError()
        val settingsNow = settings()
        errorJob?.cancel()
        errorJob = scope.launch {
            if (PlaybackPlanner.shouldRetrySame(kind) && sameRetries < 1) {
                sameRetries++
                _notices.tryEmit(Notice.RETRYING)
                releaseEngine()          // libère la connexion AVANT de rouvrir (connexion unique)
                delay(1_500)
                launch(combo)
                return@launch
            }
            if (PlaybackPlanner.shouldFallBack(kind, isProtected)) {
                val next = PlaybackPlanner.next(settingsNow.engine, tried, isProtected)
                if (next != null) {
                    _notices.tryEmit(if (next.engine == EngineKind.VLC) Notice.USING_VLC else if (next.decoder == DecoderMode.SOFTWARE) Notice.USING_SOFTWARE else Notice.USING_EXO)
                    launch(next)
                    return@launch
                }
            }
            releaseEngine()
            _state.value = _state.value.copy(phase = Phase.ERROR, error = kind)
        }
    }

    /** Reconnexion du direct sur la même adresse (délais croissants) ; au-delà, écran d'erreur. */
    private fun reconnectLive() {
        stallJob?.cancel(); stableJob?.cancel()
        if (reconnectJob?.isActive == true) return
        // Appli en arrière-plan (lecteur en pause) : surtout pas de rechargement — le son repartirait. Le retour sur
        // l'appli relance la lecture (retry) de toute façon.
        if (!com.ultratv.tv.nativeapp.ui.common.AppForeground.visible) return
        val wait = PlaybackPlanner.liveReconnectDelayMs(reconnects) ?: run {
            releaseEngine()
            _state.value = _state.value.copy(phase = Phase.ERROR, error = PlayErrorKind.NETWORK)
            return
        }
        reconnects++
        android.util.Log.i("UltraPlay", "live reconnect #$reconnects in ${wait}ms engine=${combo.engine}")
        _notices.tryEmit(Notice.RETRYING)
        reconnectJob = scope.launch {
            delay(wait)
            firstFrame = false; launchedAtNs = System.nanoTime(); watchdog?.cancel(); blackJob?.cancel()
            val e = engine
            if (e != null && e.reusable) loadOn(e, 0) else launch(combo)
        }
    }

    private fun releaseEngine() {
        engine?.let { runCatching { it.release() } }
        engine = null
        launchedBuffer = null
        container.removeAllViews()
    }

    fun release() {
        startJob?.cancel(); eventsJob?.cancel(); watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel(); adaptJob?.cancel()
        reconnectJob?.cancel(); stallJob?.cancel(); stableJob?.cancel(); errorJob?.cancel()
        releaseEngine()
    }
}

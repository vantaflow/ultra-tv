package com.ultratv.tv.nativeapp.ui.player.engine

import android.content.Context
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.PlaybackAdapter
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import com.ultratv.tv.nativeapp.data.stalker.StalkerLink
import com.ultratv.tv.nativeapp.data.stalker.StalkerPlayLog
import com.ultratv.tv.nativeapp.data.stalker.StreamOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ultratv.tv.nativeapp.ui.player.StreamDiagnostic

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
    /** Type HDR détecté par le moteur pour le flux en cours (« hdr10 », « hlg », « dv ») ; null = SDR ou pas encore connu. */
    val hdr: String? = null,
    /** Remise en mémoire tampon en cours (après la première image) : sert à proposer « Recharger ». */
    val buffering: Boolean = false,
    /** Dernier code HTTP reçu avant l'erreur (null si le serveur n'a pas répondu) : précise le message affiché. */
    val errorHttp: Int? = null,
    /** Flux sans piste vidéo (radio) : le moteur est prêt mais aucune image ne viendra ; le son suffit à « montrer » le flux. */
    val audioOnly: Boolean = false,
) {
    /** Le flux est présenté à l'utilisateur : image affichée (en lecture) ou flux audio seul prêt. */
    val showsMedia: Boolean get() = (phase == Phase.PLAYING && hasPicture) || audioOnly
}

/**
 * Une session de lecture : UN seul moteur vivant à la fois (connexion unique du fournisseur : `release()`
 * précède toujours l'ouverture suivante). Gère le repli automatique moteur/décodage, la mémorisation par
 * chaîne, la nouvelle tentative après un 403 et l'adaptation du débit pendant la lecture.
 */
/**
 * « Audio seul » (radio) n'est confirmé que si, ce délai après « prêt », le flux n'a toujours ni piste vidéo ni image : avec VLC,
 * « prêt » (Playing) peut précéder l'ajout de la piste vidéo (HLS, TS) ; conclure tout de suite ferait entendre le son avant l'image.
 */
internal const val AUDIO_ONLY_CONFIRM_MS = 1_500L

/** Direct bloqué en chargement après avoir joué : délai avant reconnexion. */
private const val STALL_MS = 12_000L

/**
 * Démarrage figé : délai sans première image ni progrès de chargement avant de traiter le lancement comme une erreur
 * (repli vers la combinaison suivante, puis panneau d'erreur). Plus long avec le tampon « Stable » (démarrage plus large).
 */
private const val STARTUP_MS = 15_000L
private const val STARTUP_SLOW_MS = 25_000L
/** Plafond absolu d'un démarrage qui progresse (VOD lente) : au-delà, même vivant, il est jugé figé. */
private const val STARTUP_MAX_MS = 90_000L

/**
 * Plafond de la DEMANDE DE LIEN (portail Stalker : poignée de main + create_link ; serveur multimédia ; NAS) depuis l'appui :
 * direct 12 s, film ou épisode 20 s (un épisode Stalker peut demander deux modules, `vod` puis `series`, 10 s chacun).
 * Global pour l'appui tant qu'aucun lien n'a été obtenu (une nouvelle tentative après un refus rapide ne rouvre pas 12 s
 * de plus) ; ensuite, par appel (repli de moteur, reconnexion du direct). La phase moteur garde son propre délai
 * ([STARTUP_MS], prolongé tant que le chargement progresse) : un flux 4K lent à démarrer n'est pas coupé par ce plafond.
 */
internal const val OPEN_LIVE_MS = 12_000L
internal const val OPEN_VOD_MS = 20_000L

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
    /** Réglage utilisateur « Surface vidéo » (auto / texture / surface) : « auto » seulement autorise la bascule en SurfaceView pour le HDR. */
    private val videoSurfacePref: () -> String = { "auto" },
    /** Style des sous-titres et langues préférées au moment du lancement du moteur. */
    private val subtitles: () -> com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings = { com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings() },
    /** Langue de l'interface (ISO 639-1) : langue audio par défaut quand aucune préférence n'est réglée. */
    private val uiLanguage: () -> String = { java.util.Locale.getDefault().language },
    /** Propriétés de lecture (flux protégé) d'une chaîne « fournisseur:chaîne » ; null = aucune recherche (flux ordinaires seulement). */
    private val propsLookup: (suspend (String) -> com.ultratv.tv.nativeapp.data.m3u.StreamProps?)? = null,
    /** Contrôle d'un flux protégé avant d'ouvrir un lecteur (système pris en charge, propriétés complètes) ; null = lecture possible. */
    private val drmPrecheck: (com.ultratv.tv.nativeapp.data.m3u.StreamProps) -> PlayErrorKind? = { DrmMedia.precheck(it) },
    /**
     * Adresse stockée → flux prêt à ouvrir. Appelé avant CHAQUE ouverture d'une adresse `stalker://` (démarrage, nouvelle
     * tentative, reconnexion, changement de moteur) : le lien create_link d'un portail ne sert qu'une fois.
     */
    private val opener: suspend (String) -> StreamOpen = { StreamOpen(it) },
    /**
     * Signal de présence d'un boîtier MAG (watchdog du portail), appelé toutes les [presenceIntervalMs] tant qu'une adresse
     * `stalker://` est lue : arrêté à la fin de la lecture ou au changement de source ; jamais lancé pour une autre source.
     */
    private val presence: (suspend (String) -> Unit)? = null,
    private val presenceIntervalMs: Long = 120_000L,
    private val engineFactory: (EngineKind, EngineConfig) -> PlayerEngine = { k, c -> if (k == EngineKind.EXO) ExoEngine(ctx, c) else VlcEngine(ctx, c) },
    /** Horloge monotone (ms) du plafond de la demande de lien ; temps virtuel dans les tests. */
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
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
    /** Confirmation différée de « audio seul » ([AUDIO_ONLY_CONFIRM_MS]) : annulée à chaque nouveau flux, à l'image, à l'arrêt. */
    private var audioOnlyJob: Job? = null
    private var adaptJob: Job? = null
    private var manualSwitch = false
    private var launchedAtNs = 0L
    // Reconnexion automatique du direct (serveurs qui ferment la session, flux gelé).
    private var reconnects = 0
    private var everPlayed = false
    private var reconnectJob: Job? = null
    private var stallJob: Job? = null
    /** Direct figé en lecture (position ou images bloquées sans erreur) : voir [FrozenLiveDetector]. */
    private var frozenJob: Job? = null
    /** Délai de démarrage : armé à chaque lancement de moteur, levé par la première image (ou Ready, relais du contrôle d'image). */
    private var startupJob: Job? = null
    private var stableJob: Job? = null
    /** Nouvelle tentative / repli programmés par [onError] : annulés par un zap ou un nouveau moteur (double connexion sinon). */
    private var errorJob: Job? = null
    /** Demande de lien (adresse `stalker://`) en cours, et en-têtes du flux chargé dans le moteur en place. */
    private var openJob: Job? = null
    private var launchedHeaders: Map<String, String> = emptyMap()
    private var launchedUa: String = userAgent
    private var presenceJob: Job? = null
    /** Dernier code HTTP vu et début de la lecture en cours : journal des reconnexions Stalker. */
    private var lastHttp: Int? = null
    private var playedSinceMs = 0L
    /** Échéance de la demande de lien de l'appui en cours ([OPEN_LIVE_MS] / [OPEN_VOD_MS]) ; 0 = un lien a été obtenu. */
    private var openDeadlineMs = 0L
    private val openBudgetMs get() = if (isLive) OPEN_LIVE_MS else OPEN_VOD_MS

    /** Appui (lancement, « Réessayer », choix manuel) : le plafond de la demande de lien repart de maintenant. */
    private fun armOpenDeadline() { openDeadlineMs = if (needsOpener) nowMs() + openBudgetMs else 0L }

    // Diagnostic du flux (Réglages du lecteur › Statistiques) : mesures du flux en cours, remises à zéro à chaque ouverture.
    private var diagStartMs = 0L
    private var diagFirstFrameMs: Long? = null
    private var diagRebuffers = 0
    private var diagRebufferTotalMs = 0L
    private var diagBufferingSinceMs = 0L
    private val diagErrors = ArrayList<StreamDiagnostic.ErrorEvent>()

    private fun diagEndBuffering() {
        if (diagBufferingSinceMs != 0L) { diagRebufferTotalMs += System.currentTimeMillis() - diagBufferingSinceMs; diagBufferingSinceMs = 0L }
    }

    /** Mesures du flux en cours pour [StreamDiagnostic] ; le moteur fournit débit, variantes et images perdues. */
    fun diagnosticInput(): StreamDiagnostic.Input {
        val e = engine
        val st = runCatching { e?.stats() }.getOrNull()
        val now = System.currentTimeMillis()
        return StreamDiagnostic.Input(
            engine = (if (combo.engine == EngineKind.EXO) "ExoPlayer" else "VLC") + "/" + combo.decoder.name.lowercase(),
            fellBack = tried.size > 1,
            firstFrameMs = diagFirstFrameMs,
            rebufferCount = diagRebuffers,
            rebufferTotalMs = diagRebufferTotalMs + if (diagBufferingSinceMs != 0L) now - diagBufferingSinceMs else 0L,
            droppedFrames = st?.droppedFrames,
            renderedFrames = e?.renderedFrames() ?: -1L,
            errors = diagErrors.toList(),
            playing = _state.value.phase == Phase.PLAYING,
            resolution = st?.resolution,
            currentKbps = st?.videoBitrateKbps,
            bandwidthKbps = st?.bandwidthKbps,
            variants = st?.variants.orEmpty(),
        )
    }
    /** Surface effective du prochain moteur : [textureView], ou SurfaceView imposée pour un contenu HDR (cet élément seulement). */
    private var textureNow = textureView
    /** Type de HDR de l'élément en cours (null = SDR / inconnu) ; bascule SurfaceView déjà tentée ; essai de SurfaceView en cours. */
    private var hdrKind: String? = null
    private var hdrSwitched = false
    private var hdrSurfaceTrial = false

    /**
     * Reconnexion automatique autorisée pour la lecture en cours : VRAI direct seulement. Un replay (« Depuis le
     * début ») ou le relais du différé ont une FIN normale — les relancer en boucle n'aurait pas de sens.
     * Fixé par le lecteur avant chaque [start].
     */
    var liveReconnect: Boolean = true
    /**
     * Le nom de la chaîne annonce du HDR (« HDR », « HDR10 », « HDR10+ ») : sur un écran HDR avec le réglage « auto », la
     * lecture démarre directement en SurfaceView, sans attendre que le flux le signale (certains flux HDR10 ne le
     * signalent jamais et restaient en SDR). Fixé par le lecteur avant chaque [start].
     */
    var hdrHint: Boolean = false
    /**
     * Aperçu (Guide, Direct) : une absence d'image ou un échec n'enchaîne pas les replis du plein écran (autre décodeur, autre
     * moteur, nouvel essai) — un seul essai, puis l'état d'erreur (l'encadré garde le logo) ; la relance vient du changement de
     * chaîne ou du retour du focus. Pas d'image tant que la vidéo n'est pas affichée (vue absente de l'écran) : ce n'est pas
     * une panne. Fixé par [SharedLiveSession] (vrai pour l'aperçu, faux dès que le plein écran reprend la session).
     */
    var previewMode: Boolean = false
    private var hdrDiagJob: Job? = null
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
            // Les propriétés d'un flux protégé sont lues dans la base (jamais dans l'URL) avant d'ouvrir le moteur. Pendant cette
            // lecture (asynchrone, longue sur une box chargée), l'état du flux PRÉCÉDENT ne doit plus passer pour celui du nouveau :
            // retour au chargement, sans image (l'écran qui coupe le son tant qu'il n'y a pas d'image le lit).
            val st = _state.value
            // Une erreur reste une erreur : [begin] n'enchaîne pas sur le moteur en place après elle.
            if (st.phase == Phase.PLAYING || st.phase == Phase.ENDED || st.audioOnly) {
                _state.value = st.copy(phase = Phase.LOADING, error = null, errorHttp = null, hasPicture = false, buffering = false, audioOnly = false)
            }
            audioOnlyJob?.cancel()
            startJob = scope.launch { begin(url, channelKey, resumeMs, runCatching { lookup(channelKey) }.getOrNull()) }
        } else begin(url, channelKey, resumeMs, props)
    }

    private fun begin(url: String, channelKey: String?, resumeMs: Long, found: com.ultratv.tv.nativeapp.data.m3u.StreamProps?) {
        this.url = url; this.key = channelKey; this.resumeMs = resumeMs
        armOpenDeadline()
        this.props = found?.takeIf { it.isProtected }
        tried = mutableSetOf(); sameRetries = 0; manualSwitch = false
        hdrKind = null; hdrSwitched = false; hdrSurfaceTrial = false; hdrDiagJob?.cancel()
        val hintSurface = hdrHint && videoSurfacePref() == "auto" && textureView && HdrOutput.displayHdrCapable(ctx) && !HdrOutput.surfaceKnownBad(ctx)
        val wantTexture = if (hintSurface) false else textureView
        syncPresence(); lastHttp = null; playedSinceMs = 0L
        diagStartMs = System.currentTimeMillis(); diagFirstFrameMs = null; diagRebuffers = 0; diagRebufferTotalMs = 0L; diagBufferingSinceMs = 0L; diagErrors.clear()
        reconnects = 0; everPlayed = false; reconnectJob?.cancel(); stallJob?.cancel(); frozenJob?.cancel(); startupJob?.cancel(); stableJob?.cancel(); errorJob?.cancel(); openJob?.cancel(); audioOnlyJob?.cancel()
        val s = settings()
        presetOverride = channelKey?.let { memory.bufferPreset(it) }
        val protectedNow = isProtected
        val next0 = PlaybackPlanner.initial(s.engine, s.decoder, if (protectedNow) null else channelKey?.let { memory.combo(it) }, protectedNow)
        // Stalker : VLC (LibVLC 3.x) ne sait pas envoyer le cookie `mac` (aucune option) → ExoPlayer, sauf VLC imposé par l'utilisateur.
        val next = if (needsOpener && s.engine != EngineChoice.VLC && next0.engine == EngineKind.VLC) Combo(EngineKind.EXO, next0.decoder) else next0
        props?.let(drmPrecheck)?.let { bad ->
            // Appareil sans le système demandé ou propriétés incomplètes : erreur claire, AUCUN moteur (et jamais VLC).
            watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel(); eventsJob?.cancel(); adaptJob?.cancel(); startupJob?.cancel()
            releaseEngine(); combo = next
            _state.value = SessionState(Phase.ERROR, bad, next, presetOverride ?: s.bufferPreset, false, true)
            return
        }
        val e = engine
        // Zapping : même moteur, même décodage, même tampon → on enchaîne le flux sur le moteur EN PLACE au lieu de le
        // détruire et d'en recréer un (lecteur, vue, décodeurs : plusieurs centaines de ms sur une box modeste).
        // Réglages de sous-titres changés depuis la création du moteur : on le recrée (ils sont fixés à sa création).
        if (isLive && e != null && e.reusable && next == combo && bufferFor(s) == launchedBuffer && subtitles() == launchedSubs && textureNow == wantTexture && _state.value.phase != Phase.ERROR && (isStalkerUrl || launchedHeaders.isEmpty())) {
            watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel()
            tried = mutableSetOf(combo); firstFrame = false; launchedAtNs = System.nanoTime()
            _state.value = SessionState(Phase.LOADING, null, combo, presetOverride ?: s.bufferPreset, false, protectedNow)
            if (!isStalkerUrl) { loadOn(e); armStartup(e) }
            else openJob = scope.launch {
                // Lien frais à chaque ouverture ; en-têtes d'un autre portail (zap entre sources) : moteur recréé.
                val o = openNow() ?: return@launch
                if (engine === e && o.headers == launchedHeaders && (o.userAgent ?: userAgent) == launchedUa) { e.load(o.url, resumeMs); armStartup(e) } else launch(combo, o)
            }
        } else {
            combo = next
            textureNow = wantTexture   // un élément précédent en SurfaceView (HDR) ne dicte pas la surface du suivant
            if (hintSurface) { hdrSwitched = true; hdrSurfaceTrial = true; android.util.Log.i("UltraPlay", "hdr hint (name) -> surface") }
            launch(combo)
        }
        adaptJob?.cancel()
        adaptJob = scope.launch { while (true) { delay(5_000); applyQualityLimit(adapter.tick(System.currentTimeMillis())) } }
    }

    private val isStalkerUrl get() = StalkerLink.isStalker(url)

    /** Adresse à résoudre avant ouverture : portail Stalker (lien frais) ou serveur Jellyfin / Emby (adresse directe + jeton en en-tête). */
    private val needsOpener get() = isStalkerUrl || com.ultratv.tv.nativeapp.data.mediaserver.MediaLink.isMedia(url) || com.ultratv.tv.nativeapp.data.nas.Nas.NasLink.isNas(url)

    /** (Re)lance ou arrête la boucle de présence selon l'adresse courante. */
    private fun syncPresence() {
        presenceJob?.cancel(); presenceJob = null
        val p = presence ?: return
        if (!isStalkerUrl) return
        val raw = url
        presenceJob = scope.launch {
            while (true) {
                try { p(raw) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { /* le prochain passage réessaie */ }
                delay(presenceIntervalMs)
            }
        }
    }

    /**
     * Ouvre l'adresse courante : lien create_link frais pour `stalker://` (échec journalisé sans MAC ni hôte, puis classé
     * comme une erreur de lecture), l'adresse telle quelle sinon. Null = échec déjà traité.
     */
    private suspend fun openNow(reconnecting: Boolean = false): StreamOpen? {
        if (!needsOpener) return StreamOpen(url)
        // Plafond (voir OPEN_LIVE_MS) : reste de l'échéance de l'appui tant qu'aucun lien n'a été obtenu, sinon par appel.
        val budget = if (!reconnecting && openDeadlineMs > 0L) minOf(openDeadlineMs - nowMs(), openBudgetMs) else openBudgetMs
        // La demande tourne À CÔTÉ de l'attente : un appel réseau bloquant (OkHttp) n'est pas interruptible, l'attente si.
        // Elle est annulée en sortie (délai, zap, fermeture) ; son résultat tardif est ignoré.
        val holder = kotlinx.coroutines.SupervisorJob()
        val pending = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext().minusKey(Job) + holder).async { opener(url) }
        return try {
            val o = (if (budget > 0L) kotlinx.coroutines.withTimeoutOrNull(budget) { pending.await() } else null)
                ?: return openTimedOut(budget.coerceAtLeast(0L), reconnecting)
            // Jamais d'adresse stalker:// au moteur (il ne sait pas l'ouvrir → « problème réseau ») : un opener qui la rend telle quelle est un échec.
            o.also { if (StalkerLink.isStalker(it.url)) throw com.ultratv.tv.nativeapp.data.stalker.StalkerLinkException("unresolved") }
                .also { openDeadlineMs = 0L }
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) {
            if (isStalkerUrl) {
                android.util.Log.i("UltraPlay", StalkerPlayLog.openFailed(t))
                com.ultratv.tv.nativeapp.RemoteLog.info("StalkerPlay", StalkerPlayLog.openFailed(t))
            }
            if (reconnecting) reconnectJob = null   // sinon onError → reconnectLive croit la reconnexion en cours et ne fait rien
            onError(
                when {
                    t is com.ultratv.tv.nativeapp.data.stalker.StalkerLinkException -> PlayErrorKind.NO_LINK   // pas de nouvel essai : le portail n'a rien donné
                    StalkerPlayLog.isPortalRefusal(StalkerPlayLog.httpOf(t)) -> PlayErrorKind.REFUSED
                    else -> PlayErrorKind.NETWORK
                }, StalkerPlayLog.httpOf(t), logged = true,
            )
            null
        } finally {
            holder.cancel()
        }
    }

    /**
     * Demande de lien sans réponse dans le plafond. Direct qui jouait (reconnexion) : compte comme un essai de reconnexion
     * raté, la reconnexion bornée continue. Sinon : écran d'erreur « le flux ne démarre pas » TOUT DE SUITE, sans nouvelle
     * tentative ni repli (chacun rouvrirait une attente). Journal sans adresse ni identifiant.
     */
    private fun openTimedOut(budget: Long, reconnecting: Boolean): StreamOpen? {
        val line = "open timeout ms=$budget live=$isLive stalker=$isStalkerUrl reconnect=${reconnecting || (reconnectAllowed && everPlayed)}"
        android.util.Log.i("UltraPlay", line)
        com.ultratv.tv.nativeapp.RemoteLog.warn("play", line)
        if (reconnecting || (reconnectAllowed && everPlayed)) {
            if (reconnecting) reconnectJob = null   // sinon reconnectLive croit la reconnexion en cours et ne fait rien
            onError(PlayErrorKind.NETWORK, null, logged = true)
            return null
        }
        network.onError()
        startupJob?.cancel(); errorJob?.cancel()
        releaseEngine()
        presenceJob?.cancel()
        if (diagErrors.size < 20) diagErrors += StreamDiagnostic.ErrorEvent(PlayErrorKind.START_TIMEOUT, null)
        _state.value = _state.value.copy(phase = Phase.ERROR, error = PlayErrorKind.START_TIMEOUT, errorHttp = null, buffering = false)
        return null
    }

    /** « Réessayer » / retour sur l'appli : tout repart de zéro (sinon, compteur à 8 = écran d'erreur à la 1re coupure). */
    fun retry() { tried = mutableSetOf(); sameRetries = 0; reconnects = 0; everPlayed = false; armOpenDeadline(); launch(combo) }

    /** « Recharger » pendant une mise en mémoire tampon : relance le même flux (VOD : à la position courante, direct : même chaîne). */
    fun reload() {
        engine?.let { if (!isLive && it.positionMs > 0) resumeMs = it.positionMs }
        _state.value = _state.value.copy(buffering = false)
        retry()
    }

    /** Changement manuel (pilule « Lecteur ») : on le mémorise tout de suite pour cette chaîne. */
    fun switchTo(c0: Combo) {
        val c = PlaybackPlanner.manualCombo(c0, isProtected)   // flux protégé : le choix VLC est ignoré
        manualSwitch = true; tried = mutableSetOf(); combo = c; if (!isProtected) key?.let { memory.remember(it, c, null) }; armOpenDeadline(); launch(c)
    }

    /** Choix manuel de la piste audio : appliqué, puis sa langue est retenue pour ce titre (jamais l'identifiant de piste). */
    fun selectAudio(id: String) {
        val e = engine ?: return
        val lang = e.audioTracks().firstOrNull { it.id == id }?.language
        e.selectAudio(id)
        if (lang != null) key?.let { memory.rememberAudioLang(it, lang) }
    }

    fun setBufferPreset(p: BufferPreset) { presetOverride = p; key?.let { memory.remember(it, null, p) }; armOpenDeadline(); launch(combo) }

    /** Ouvre [u] sur [e] : en flux protégé, avec ses propriétés de licence. */
    private fun loadOn(e: PlayerEngine, u: String = url, resume: Long = resumeMs) {
        val p = props
        if (p != null && p.isProtected) e.loadProtected(u, resume, p) else e.load(u, resume)
    }

    private fun launch(c0: Combo, pre: StreamOpen? = null) {
        val c = PlaybackPlanner.manualCombo(c0, isProtected)
        eventsJob?.cancel(); watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel()
        // Nouveau moteur : une reconnexion ou une nouvelle tentative encore en attente rechargerait un flux qui joue.
        reconnectJob?.cancel(); stallJob?.cancel(); frozenJob?.cancel(); startupJob?.cancel(); stableJob?.cancel(); errorJob?.cancel(); openJob?.cancel(); audioOnlyJob?.cancel()
        releaseEngine()
        combo = c; tried += c; firstFrame = false; launchedAtNs = System.nanoTime()
        val s = settings()
        val buffer = bufferFor(s)
        launchedBuffer = buffer
        _state.value = SessionState(Phase.LOADING, null, c, presetOverride ?: s.bufferPreset, false, isProtected)
        // Box basse : on libère les images en mémoire avant que le décodeur ne réclame la sienne.
        if (s.lowRam) runCatching { coil3.SingletonImageLoader.get(ctx).memoryCache?.clear() }
        // Adresse stalker:// : le lien (à usage unique) est demandé à CHAQUE lancement de moteur, avant sa création.
        if (pre == null && needsOpener) openJob = scope.launch { openNow()?.let { launchEngine(c, s, buffer, it) } }
        else launchEngine(c, s, buffer, pre ?: StreamOpen(url))
    }

    private fun launchEngine(c: Combo, s: ResolvedPlayback, buffer: BufferParams, o: StreamOpen) {
        launchedHeaders = o.headers; launchedUa = o.userAgent ?: userAgent
        val sub = subtitles()
        launchedSubs = sub
        // Sous-titres : jamais activés d'office ; seulement si l'utilisateur les avait activés la dernière fois.
        // Langue audio : choix manuel mémorisé pour ce titre > réglage Langues > langue de l'interface (jamais « la première piste »).
        val ui = uiLanguage()
        val tc = com.ultratv.tv.nativeapp.data.subtitles.TrackChoice
        val audioPref = tc.effectivePreferred(sub.languages.audio, key?.let { memory.audioLang(it) }, ui)
        val textPref = if (sub.autoOn) tc.effectivePreferred(sub.languages.text, null, ui) else emptyList()
        val cfg = EngineConfig(c.decoder, buffer, isLive, autoFrameRate, launchedUa, sub.style, audioPref, textPref, textOff = !sub.autoOn, uiLanguage = ui, textureView = textureNow, headers = o.headers,
            maxQuality = s.maxQuality, maxVideoBitrateBps = PlaybackAdapter.scaleBitrate(s.maxVideoBitrateBps, adapter.level), tunneling = s.tunneling)
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
        e.limitQuality(s.maxVideoHeight, if (s.maxQuality) null else PlaybackAdapter.scaleBitrate(s.maxVideoBitrateBps, adapter.level))
        eventsJob = scope.launch { e.events.collect { onEvent(it, e) } }
        loadOn(e, o.url)
        armStartup(e)
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
        engine?.limitQuality(s.maxVideoHeight, if (s.maxQuality) null else PlaybackAdapter.scaleBitrate(s.maxVideoBitrateBps, level))
    }

    /**
     * Au « prêt » : une piste vidéo connue ou une image annule toute marque audio seul ; sans l'une ni l'autre, le flux n'est
     * déclaré audio seul qu'après [AUDIO_ONLY_CONFIRM_MS] (et seulement s'il est toujours sans vidéo, sur le même moteur).
     */
    private fun confirmAudioOnly(e: PlayerEngine) {
        if (e.hasVideo || firstFrame) {
            audioOnlyJob?.cancel()
            if (_state.value.audioOnly) _state.value = _state.value.copy(audioOnly = false)
            return
        }
        if (_state.value.audioOnly || audioOnlyJob?.isActive == true) return
        audioOnlyJob = scope.launch {
            delay(AUDIO_ONLY_CONFIRM_MS)
            if (engine === e && !firstFrame && !e.hasVideo && _state.value.phase != Phase.ERROR) _state.value = _state.value.copy(audioOnly = true)
        }
    }

    private fun onEvent(ev: EngineEvent, e: PlayerEngine) {
        when (ev) {
            EngineEvent.Buffering -> if (firstFrame) {
                if (!_state.value.buffering) { _state.value = _state.value.copy(buffering = true); diagRebuffers++; diagBufferingSinceMs = System.currentTimeMillis() }
                android.util.Log.i("UltraPlay", "rebuffer engine=${combo.engine}"); network.onRebuffer(); applyQualityLimit(adapter.onRebuffer(System.currentTimeMillis()))
                // Direct figé en chargement (serveur qui ne renvoie plus rien sans fermer) : reconnexion au bout de 12 s.
                if (reconnectAllowed && stallJob?.isActive != true) stallJob = scope.launch { delay(STALL_MS); reconnectLive() }
            } else if (e.hasVideo && startupJob?.isActive != true) armStartup(e)   // rechargement avant toute image (après Ready) : même garde-fou
            EngineEvent.Ready -> { diagEndBuffering(); if (_state.value.buffering) _state.value = _state.value.copy(buffering = false); confirmAudioOnly(e); stallJob?.cancel(); startupJob?.cancel(); if (!firstFrame && watchdog?.isActive != true) armPictureWatchdog(e) }
            EngineEvent.FirstFrame -> {
                // Déjà vue pour ce lancement : le moteur la resignale quand sa surface change (aperçu → plein écran) ;
                // la durée depuis le lancement n'aurait plus de sens (41 s affichées pour 1,7 s réelles).
                val again = firstFrame
                diagEndBuffering()
                if (diagFirstFrameMs == null) diagFirstFrameMs = System.currentTimeMillis() - diagStartMs
                firstFrame = true; audioOnlyJob?.cancel(); watchdog?.cancel(); blackJob?.cancel(); stallJob?.cancel(); startupJob?.cancel()
                everPlayed = true
                if (playedSinceMs == 0L) playedSinceMs = System.currentTimeMillis()
                // 30 s de lecture stable : le compteur de reconnexions repart de zéro.
                stableJob?.cancel(); stableJob = scope.launch { delay(30_000); reconnects = 0 }
                // Mesure (jamais d'URL) : moteur, décodage et délai jusqu'à la première image.
                if (again) android.util.Log.i("UltraPlay", "picture again (surface changed) engine=${combo.engine}")
                else android.util.Log.i("UltraPlay", "firstFrame engine=${combo.engine} decoder=${combo.decoder} ms=${(System.nanoTime() - launchedAtNs) / 1_000_000}")
                if (reconnectAllowed && frozenJob?.isActive != true) armFrozenWatch(e)
                // Nom « HDR » mais aucun HDR signalé par le flux au bout de 10 s : signalisation couleur brute au journal (diagnostic).
                if (hdrHint && !again && hdrDiagJob?.isActive != true) hdrDiagJob = scope.launch {
                    delay(10_000)
                    if (engine === e && hdrKind == null) com.ultratv.tv.nativeapp.RemoteLog.warn("play", "hdr named but not signalled engine=${combo.engine.name} surface=${if (textureNow) "texture" else "surface"} ${e.colorDebug() ?: "-"}")
                }
                // La lecture a repris : un code HTTP d'une tentative précédente (503 au démarrage, puis succès) ne doit
                // pas qualifier une erreur ultérieure sans réponse du serveur (Wi-Fi coupé une heure plus tard).
                lastHttp = null
                _state.value = _state.value.copy(phase = Phase.PLAYING, error = null, errorHttp = null, hasPicture = true, buffering = false)
                armBlackPictureCheck(e)
                // La combinaison est retenue pour la chaîne si elle a demandé un repli ou un choix manuel et tient 5 s.
                rememberJob?.cancel()
                rememberJob = scope.launch { delay(5_000); if (tried.size > 1 || manualSwitch) key?.let { memory.remember(it, combo, null) } }
                sameRetries = 0
            }
            // Direct : une « fin » est une session fermée par le serveur, pas la fin du programme → on se reconnecte.
            is EngineEvent.HdrDetected -> onHdr(ev.kind, e)
            EngineEvent.Ended -> if (reconnectAllowed) reconnectLive() else _state.value = _state.value.copy(phase = Phase.ENDED)
            is EngineEvent.Error -> onError(ev.kind, ev.httpStatus)
        }
    }

    /**
     * Contenu HDR : une TextureView le rend en SDR (image terne). Sur un écran HDR, avec le réglage « auto », on relance
     * UNE fois cet élément en SurfaceView (VOD : à la position courante ; direct : simple reconnexion). Si la SurfaceView ne
     * montre aucune image (Shield, issue #7), [onError] revient à la TextureView et le mémorise pour l'appareil.
     */
    private fun onHdr(kind: String, e: PlayerEngine) {
        if (engine !== e) return
        hdrKind = kind
        _state.value = _state.value.copy(hdr = kind)
        val displayHdr = HdrOutput.displayHdrCapable(ctx)
        val texture = e.config.textureView
        val line = HdrOutput.describe(kind, displayHdr, texture, e.kind)
        android.util.Log.i("UltraPlay", line)
        com.ultratv.tv.nativeapp.RemoteLog.info("play", line)
        val switch = HdrOutput.shouldSwitchToSurface(
            contentHdr = true, displayHdr = displayHdr, currentlyTexture = texture, userPref = videoSurfacePref(),
            alreadySwitched = hdrSwitched, surfaceKnownBad = HdrOutput.surfaceKnownBad(ctx),
        )
        if (!switch) return
        hdrSwitched = true; hdrSurfaceTrial = true; textureNow = false
        if (!isLive && e.positionMs > 0) resumeMs = e.positionMs
        android.util.Log.i("UltraPlay", "hdr -> surface relaunch engine=${combo.engine}")
        launch(combo)
    }

    /**
     * Direct en lecture dont la position ou les images ne bougent plus depuis 10 s, sans erreur ni chargement : on se
     * reconnecte (mêmes délais et même plafond que pour une coupure). Le journal dit ce qui a figé, jamais l'adresse.
     */
    private fun armFrozenWatch(e: PlayerEngine) {
        frozenJob?.cancel()
        val detector = FrozenLiveDetector()
        frozenJob = scope.launch {
            while (true) {
                delay(2_000)
                if (engine !== e || !reconnectAllowed) return@launch
                val now = System.currentTimeMillis()
                val checkFrames = e.hasVideo && e.view.isShown
                if (detector.feed(now, firstFrame && e.isPlaying, e.positionMs, e.renderedFrames(), checkFrames)) {
                    val line = "live frozen (${detector.reason(now)}) engine=${combo.engine} decoder=${combo.decoder} played=${if (playedSinceMs == 0L) 0 else (now - playedSinceMs) / 1000}s"
                    android.util.Log.i("UltraPlay", line); com.ultratv.tv.nativeapp.RemoteLog.warn("play", line)
                    val played = if (playedSinceMs == 0L) 0L else now - playedSinceMs
                    // Gel juste après une bascule de fréquence de l'écran : la sortie vidéo, pas le décodeur — on recharge
                    // avec la MÊME combinaison (le décodage logiciel rendrait tout lent sans rien réparer).
                    val afterSwitch = DisplayModeGate.lastSwitchMs > 0 && now - DisplayModeGate.lastSwitchMs < PlaybackPlanner.EARLY_FREEZE_MS
                    if (!afterSwitch && PlaybackPlanner.frozenMeansDecoder(played, combo.decoder) && fallbackAfter(PlayErrorKind.DECODER) != null) {
                        onError(PlayErrorKind.DECODER); return@launch
                    }
                    reconnectLive(); return@launch
                }
            }
        }
    }

    /** Le son passe mais aucune image au bout de 8 s alors que le flux a une piste vidéo : on change de décodeur/moteur. */
    private fun armPictureWatchdog(e: PlayerEngine) {
        watchdog = scope.launch {
            delay(8_000)
            // Aperçu dont la vidéo n'est pas à l'écran : aucune surface où rendre, ce n'est pas une panne du décodeur.
            while (previewMode && !firstFrame && e.hasVideo && !e.view.isShown && engine === e) delay(2_000)
            if (!firstFrame && e.hasVideo) onError(PlayErrorKind.NO_PICTURE)
        }
    }

    /**
     * Délai de démarrage : sans première image ni progrès de chargement (position tamponnée du moteur, quand il l'expose)
     * dans le délai, le lancement est traité comme une erreur de démarrage (repli, puis panneau d'erreur) au lieu de
     * charger sans fin. Un démarrage qui progresse (VOD lente) prolonge le délai, jusqu'à [STARTUP_MAX_MS].
     */
    private fun armStartup(e: PlayerEngine) {
        startupJob?.cancel()
        val step = if ((presetOverride ?: settings().bufferPreset) == BufferPreset.STABLE) STARTUP_SLOW_MS else STARTUP_MS
        startupJob = scope.launch {
            var last = e.loadProgress()
            var waited = 0L
            while (true) {
                delay(step); waited += step
                if (firstFrame || engine !== e) return@launch
                val p = e.loadProgress()
                if (p > last && waited < STARTUP_MAX_MS) { last = p; continue }
                break
            }
            // Direct qui jouait puis rechargé après une coupure : on retente la reconnexion (bornée), pas de changement de moteur.
            if (reconnectAllowed && everPlayed) {
                android.util.Log.i("UltraPlay", "startup stall engine=${combo.engine} ms=$waited -> reconnect")
                reconnectLive(); return@launch
            }
            val next = fallbackAfter(PlayErrorKind.START_TIMEOUT)
            android.util.Log.i("UltraPlay", "startup stall engine=${combo.engine} ms=$waited -> " + (next?.let { "fallback ${it.engine}" } ?: "error"))
            onError(PlayErrorKind.START_TIMEOUT)
        }
    }

    /**
     * Combinaison de repli après [kind], ou null (aucun repli : flux protégé, moteur imposé, VLC exclu pour Stalker).
     * Un démarrage figé n'est pas un souci de décodeur : les combinaisons en décodage logiciel sont sautées (elles ne changent rien à un flux qui n'arrive pas).
     */
    private fun fallbackAfter(kind: PlayErrorKind): Combo? {
        if (!PlaybackPlanner.shouldFallBack(kind, isProtected)) return null
        val choice = settings().engine
        val skipVlc = if (needsOpener && choice != EngineChoice.VLC) setOf(Combo(EngineKind.VLC, DecoderMode.AUTO), Combo(EngineKind.VLC, DecoderMode.SOFTWARE)) else emptySet()
        val skipSame = if (kind == PlayErrorKind.START_TIMEOUT) setOf(Combo(EngineKind.EXO, DecoderMode.SOFTWARE), Combo(EngineKind.VLC, DecoderMode.SOFTWARE)) else emptySet()
        return PlaybackPlanner.next(choice, tried + skipVlc + skipSame, isProtected, hdr = hdrKind != null)
    }

    /**
     * Le décodeur dit avoir rendu une image, mais l'écran peut rester noir (HEVC 10 bits sur certains Exynos, son seul) :
     * trois vignettes entièrement noires sur ~6 s au démarrage → même traitement que « aucune image » (décodeur suivant).
     * Une copie impossible (surface pas prête) ne conclut rien.
     */
    private fun armBlackPictureCheck(e: PlayerEngine) {
        blackJob = scope.launch {
            delay(3_000)
            // Bascule de fréquence de l'écran en cours (HDMI noir 1 à 3 s) : on attend qu'elle soit finie avant de juger.
            var waits = 0
            while (DisplayModeGate.settling() && waits++ < 3) delay(DisplayModeGate.SETTLE_MS / 2)
            repeat(3) { i ->
                val px = FrameProbe.sample(e.view) ?: return@launch
                if (!FrameProbe.isBlack(px)) return@launch
                if (DisplayModeGate.settling()) return@launch   // noir dû à une bascule survenue pendant la mesure : pas une panne
                if (i < 2) delay(1_500)
            }
            if (engine === e && e.hasVideo) {
                android.util.Log.i("UltraPlay", "black picture engine=${combo.engine} decoder=${combo.decoder}")
                onError(PlayErrorKind.NO_PICTURE)
            }
        }
    }

    private fun onError(kind0: PlayErrorKind, http: Int? = null, logged: Boolean = false) {
        // Flux protégé : décodeur sécurisé absent ou image noire = niveau de sécurité de l'appareil insuffisant.
        val kind = if (isProtected && (kind0 == PlayErrorKind.DECODER || kind0 == PlayErrorKind.NO_PICTURE)) PlayErrorKind.DRM_UNSUPPORTED else kind0
        android.util.Log.i("UltraPlay", "error kind=$kind engine=${combo.engine} decoder=${combo.decoder}")
        // SurfaceView essayée pour le HDR mais sans image : retour à la TextureView pour cet élément, mémorisé pour l'appareil.
        if (hdrSurfaceTrial && kind == PlayErrorKind.NO_PICTURE) {
            hdrSurfaceTrial = false; textureNow = true
            HdrOutput.rememberSurfaceBad(ctx)
            com.ultratv.tv.nativeapp.RemoteLog.warn("play", "hdr surface without picture -> texture engine=${combo.engine.name}")
            startupJob?.cancel(); errorJob?.cancel()
            errorJob = scope.launch { launch(combo) }
            return
        }
        if (http != null) lastHttp = http
        if (diagErrors.size < 20) diagErrors += StreamDiagnostic.ErrorEvent(kind, http)
        // Direct Stalker : code HTTP et type d'erreur (sans MAC ni hôte) pour diagnostiquer les retours.
        if (isStalkerUrl && !logged) StalkerPlayLog.playerError(kind.name, http, combo.engine.name).let { android.util.Log.i("UltraPlay", it); com.ultratv.tv.nativeapp.RemoteLog.info("StalkerPlay", it) }
        // Direct qui jouait : coupure du serveur ou du réseau → reconnexion, pas de changement de moteur.
        // VLC ne donne pas de code : après un rechargement, une coupure serveur arrive classée FORMAT ; si le flux a déjà
        // joué, c'est une coupure, pas un format illisible.
        val k = if (everPlayed && kind == PlayErrorKind.FORMAT && combo.engine == EngineKind.VLC) PlayErrorKind.NETWORK else kind
        if (reconnectAllowed && everPlayed && PlaybackPlanner.shouldReconnectLive(k)) { reconnectLive(); return }
        network.onError()
        startupJob?.cancel()
        errorJob?.cancel()
        errorJob = scope.launch {
            if (previewMode) {
                // Aperçu : un seul essai, aucun repli de décodeur ni de moteur (connexion unique, petite box) ; état d'erreur muet.
                com.ultratv.tv.nativeapp.RemoteLog.warn("play", "preview error kind=$kind engine=${combo.engine} decoder=${combo.decoder} (no fallback)")
                releaseEngine()
                presenceJob?.cancel()
                _state.value = _state.value.copy(phase = Phase.ERROR, error = kind, errorHttp = lastHttp)
                return@launch
            }
            if (PlaybackPlanner.shouldRetrySame(kind) && sameRetries < 1) {
                sameRetries++
                _notices.tryEmit(Notice.RETRYING)
                releaseEngine()          // libère la connexion AVANT de rouvrir (connexion unique)
                delay(1_500)
                launch(combo)
                return@launch
            }
            val next = fallbackAfter(kind)
            // Journal serveur (appareil appairé, télémétrie active) : anomalies de lecture jamais signalées par les
            // utilisateurs (bascule de moteur, échec final). Aucune adresse, seulement type, moteur, format.
            val st = runCatching { engine?.stats() }.getOrNull()
            val line = PlayAnomaly.describe(kind, lastHttp, combo, next, isLive, st?.resolution, st?.videoCodec)
            if (next != null) com.ultratv.tv.nativeapp.RemoteLog.warn("play", line) else com.ultratv.tv.nativeapp.RemoteLog.error("play", line)
            if (next != null) {
                _notices.tryEmit(if (next.engine == EngineKind.VLC) Notice.USING_VLC else if (next.decoder == DecoderMode.SOFTWARE) Notice.USING_SOFTWARE else Notice.USING_EXO)
                launch(next)
                return@launch
            }
            releaseEngine()
            // Portail Stalker qui refuse la lecture : message dédié (MAC peut-être utilisée sur un autre appareil).
            presenceJob?.cancel()
            _state.value = _state.value.copy(phase = Phase.ERROR, error = if (isStalkerUrl && kind == PlayErrorKind.REFUSED) PlayErrorKind.PORTAL_REFUSED else kind, errorHttp = lastHttp)
        }
    }

    /** Reconnexion du direct sur la même adresse (délais croissants) ; au-delà, écran d'erreur. */
    private fun reconnectLive() {
        stallJob?.cancel(); frozenJob?.cancel(); startupJob?.cancel(); stableJob?.cancel()
        if (reconnectJob?.isActive == true) return
        // Appli en arrière-plan (lecteur en pause) : surtout pas de rechargement — le son repartirait. Le retour sur
        // l'appli relance la lecture (retry) de toute façon.
        if (!com.ultratv.tv.nativeapp.ui.common.AppForeground.visible) return
        val wait = PlaybackPlanner.liveReconnectDelayMs(reconnects) ?: run {
            releaseEngine()
            _state.value = _state.value.copy(phase = Phase.ERROR, error = PlayErrorKind.NETWORK, errorHttp = lastHttp)
            return
        }
        reconnects++
        if (isStalkerUrl) {
            val line = StalkerPlayLog.reconnect(reconnects, lastHttp, if (playedSinceMs == 0L) 0L else System.currentTimeMillis() - playedSinceMs)
            android.util.Log.i("UltraPlay", line); com.ultratv.tv.nativeapp.RemoteLog.info("StalkerPlay", line)
            lastHttp = null; playedSinceMs = 0L
        }
        android.util.Log.i("UltraPlay", "live reconnect #$reconnects in ${wait}ms engine=${combo.engine}")
        _notices.tryEmit(Notice.RETRYING)
        reconnectJob = scope.launch {
            delay(wait)
            firstFrame = false; launchedAtNs = System.nanoTime(); watchdog?.cancel(); blackJob?.cancel()
            val e = engine
            if (e != null && e.reusable) {
                if (!isStalkerUrl) { loadOn(e, url, 0); armStartup(e) }
                else {
                    // Direct Stalker : nouveau lien (l'ancien est à usage unique) ; en-têtes inchangés → même moteur.
                    val o = openNow(reconnecting = true) ?: return@launch
                    if (engine === e && o.headers == launchedHeaders && (o.userAgent ?: userAgent) == launchedUa) { e.load(o.url, 0); armStartup(e) } else launch(combo, o)
                }
            } else launch(combo)
        }
    }

    private fun releaseEngine() {
        audioOnlyJob?.cancel()
        engine?.let { runCatching { it.release() } }
        engine = null
        launchedBuffer = null
        container.removeAllViews()
    }

    fun release() {
        startJob?.cancel(); eventsJob?.cancel(); watchdog?.cancel(); blackJob?.cancel(); rememberJob?.cancel(); adaptJob?.cancel()
        reconnectJob?.cancel(); stallJob?.cancel(); frozenJob?.cancel(); startupJob?.cancel(); stableJob?.cancel(); errorJob?.cancel(); openJob?.cancel(); presenceJob?.cancel()
        releaseEngine()
    }
}


/** Ligne de journal d'une anomalie de lecture (pure, testée). Jamais d'adresse ni d'identifiant de flux. */
internal object PlayAnomaly {
    fun describe(kind: PlayErrorKind, http: Int?, from: Combo, to: Combo?, live: Boolean, resolution: String?, codec: String?): String = buildString {
        append(if (to != null) "fallback" else "failed")
        append(" kind=").append(kind.name)
        if (http != null) append(" http=").append(http)
        append(" from=").append(from.engine.name).append('/').append(from.decoder.name)
        if (to != null) append(" to=").append(to.engine.name).append('/').append(to.decoder.name)
        append(" live=").append(live)
        if (!resolution.isNullOrBlank()) append(" res=").append(resolution)
        if (!codec.isNullOrBlank()) append(" codec=").append(codec.take(24))
    }
}

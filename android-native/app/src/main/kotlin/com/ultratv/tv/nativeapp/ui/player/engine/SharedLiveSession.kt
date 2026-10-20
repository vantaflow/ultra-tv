package com.ultratv.tv.nativeapp.ui.player.engine

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.util.UnstableApi
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Qui affiche le flux partagé du Direct. */
enum class LiveOwner { NONE, PREVIEW, FULLSCREEN }

/** Passage de main au lecteur plein écran. [playing] : le flux de l'aperçu continue tel quel (aucun `start`). */
@UnstableApi
class LiveAdoption(val session: PlaybackSession, val playing: Boolean)

/**
 * Réglages lus par la session partagée au lancement d'un moteur. Rebranchés par l'écran qui l'affiche (aperçu du
 * Direct, puis lecteur plein écran) : la session, elle, ne change pas.
 */
class LiveEnv(
    val settings: () -> ResolvedPlayback,
    val subtitles: () -> com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings,
    val uiLanguage: () -> String,
)

/**
 * Session de lecture du Direct PARTAGÉE entre l'aperçu (liste des chaînes) et le lecteur plein écran : OK sur la
 * chaîne déjà en aperçu ouvre le plein écran SANS recharger le flux (même moteur, même connexion), Retour rend la main
 * à l'aperçu qui continue de jouer la même chaîne (celle du dernier zap).
 *
 * Invariants :
 * - UNE seule session, donc un seul moteur et une seule connexion fournisseur (`release()` avant toute autre ouverture) ;
 * - aperçu pas prêt (chargement, erreur, autre chaîne) : OK ferme d'abord son flux, puis le lecteur ouvre le sien ;
 * - flux rendu à l'aperçu mais aucun aperçu affiché (retour vers un autre écran) : libéré après [orphanTimeoutMs].
 *
 * Tenue par la racine de l'activité ([LocalSharedLive]) ; elle ne garde aucune référence à un écran.
 */
@UnstableApi
class SharedLiveSession(
    /** Portée des sessions créées pour le Direct : vit autant que l'activité (pas autant qu'un écran). */
    val scope: CoroutineScope,
    private val orphanTimeoutMs: Long = 2_000,
    /** OK pressé mais lecteur jamais ouvert (résolution en échec) : la session est libérée après ce délai. */
    private val adoptTimeoutMs: Long = 10_000,
) {
    var owner: LiveOwner = LiveOwner.NONE
        private set
    var session: PlaybackSession? = null
        private set

    /** Ligne de la liste qui a demandé l'aperçu (« fournisseur:chaîne » de la ligne, avant choix de la variante). */
    var rowKey: String? = null
        private set

    private val _channelKey = MutableStateFlow<String?>(null)
    /** Chaîne réellement lue (variante comprise) ; suit le zapping du plein écran. */
    val channelKey: StateFlow<String?> = _channelKey.asStateFlow()

    /** Réglages courants (voir [LiveEnv]) ; à fournir avant le premier [preview] / [openFullscreen]. */
    var env: LiveEnv? = null

    /** Son coupé dans l'aperçu (réglage « Son de l'aperçu ») ; réappliqué au retour du plein écran. */
    var previewMuted: Boolean = true

    private var promoted = false
    private var pendingAdoption = false
    private var hosts = 0
    private var orphanJob: Job? = null

    /** Réglage « Son de l'aperçu » changé : appliqué tout de suite à l'aperçu en cours. */
    fun applyPreviewMuted(muted: Boolean) { previewMuted = muted; if (owner == LiveOwner.PREVIEW) session?.muted = muted }

    /** Lecture de l'aperçu prête (image affichée) pour la ligne [rowKey]. */
    fun isReady(rowKey: String): Boolean =
        owner == LiveOwner.PREVIEW && rowKey == this.rowKey && session?.state?.value?.phase == Phase.PLAYING

    /**
     * Aperçu de la ligne [rowKey] (chaîne lue [key], adresse [url]). Sans effet si cette chaîne est déjà en aperçu
     * (aucun rechargement). Le moteur en place enchaîne le flux suivant (zapping de [PlaybackSession.start]).
     * Vrai si un flux a été (re)lancé.
     */
    fun preview(rowKey: String, key: String, url: String, muted: Boolean, create: () -> PlaybackSession): Boolean {
        if (owner == LiveOwner.FULLSCREEN) return false
        val cur = session
        if (owner == LiveOwner.PREVIEW && cur != null && key == _channelKey.value && cur.state.value.phase != Phase.ERROR) {
            this.rowKey = rowKey; previewMuted = muted; cur.muted = muted
            return false
        }
        val s = cur ?: create().also { session = it }
        previewMuted = muted
        s.muted = muted
        s.liveReconnect = true
        owner = LiveOwner.PREVIEW; this.rowKey = rowKey; _channelKey.value = key; promoted = false
        s.start(url, key)
        return true
    }

    /**
     * OK sur la ligne [rowKey]. Vrai : son aperçu joue déjà → il devient le plein écran tel quel (voir [adopt]).
     * Faux : le flux de l'aperçu est fermé TOUT DE SUITE (connexion unique) ; le lecteur ouvrira le sien sur la même
     * session, ce qui permet quand même à Retour de rendre la chaîne à l'aperçu sans la rouvrir.
     */
    fun openFullscreen(rowKey: String, create: () -> PlaybackSession): Boolean {
        orphanJob?.cancel()
        val instant = isReady(rowKey)
        if (!instant) { session?.release(); _channelKey.value = null }
        if (session == null) session = create()
        session?.muted = false
        owner = LiveOwner.FULLSCREEN; promoted = instant
        orphanJob = scope.launch { delay(adoptTimeoutMs); if (owner == LiveOwner.FULLSCREEN && pendingAdoption) stop() }
        pendingAdoption = true
        return instant
    }

    /**
     * Le lecteur plein écran reprend la session pour la chaîne [key] (« fournisseur:chaîne » de l'élément lu).
     * Null : aucun passage de main en attente (lecteur ouvert depuis un autre écran) — tout aperçu restant est fermé
     * pour que le lecteur soit seul à tenir la connexion.
     */
    fun adopt(key: String?): LiveAdoption? {
        val s = session
        if (owner != LiveOwner.FULLSCREEN || s == null) { stop(); return null }
        val playing = promoted && key != null && key == _channelKey.value
        // Variante différente de celle de l'aperçu (qualité choisie, chaîne exacte) : le lecteur repart proprement.
        if (promoted && !playing) s.release()
        promoted = false; pendingAdoption = false; orphanJob?.cancel()
        s.muted = false
        _channelKey.value = key
        return LiveAdoption(s, playing)
    }

    /** Zapping dans le plein écran : l'aperçu montrera cette chaîne au retour. */
    fun onFullscreenChannel(key: String?) { if (owner == LiveOwner.FULLSCREEN) _channelKey.value = key }

    /** Retour du plein écran vers la liste : le flux continue dans l'aperçu (aucun rechargement). */
    fun demote() {
        val s = session ?: return
        if (owner != LiveOwner.FULLSCREEN) return
        owner = LiveOwner.PREVIEW
        rowKey = _channelKey.value
        s.muted = previewMuted
        if (hosts == 0) armOrphan()
    }

    /** Lecteur plein écran détruit sans [demote] (Accueil, veille, erreur, différé…) : tout est libéré. */
    fun onFullscreenGone() { if (owner == LiveOwner.FULLSCREEN) stop() }

    /** Un aperçu affiche la vidéo partagée. */
    fun hostAttached() { hosts++; orphanJob?.cancel() }
    fun hostDetached() { hosts = (hosts - 1).coerceAtLeast(0); if (hosts == 0 && owner == LiveOwner.PREVIEW) armOrphan() }

    /** Écran Direct quitté, appli en arrière-plan, aperçu désactivé : l'aperçu est fermé (jamais le plein écran). */
    fun stopPreview() { if (owner == LiveOwner.PREVIEW) stop() }

    /** Libère tout : moteur, connexion, session. */
    fun stop() {
        orphanJob?.cancel(); orphanJob = null
        session?.release()
        session = null
        owner = LiveOwner.NONE; rowKey = null; promoted = false; pendingAdoption = false
        _channelKey.value = null
    }

    private fun armOrphan() {
        orphanJob?.cancel()
        orphanJob = scope.launch { delay(orphanTimeoutMs); if (owner == LiveOwner.PREVIEW && hosts == 0) stop() }
    }
}

/** Session partagée du Direct (null hors de l'activité principale : aperçu vidéo et passage de main désactivés). */
@UnstableApi
val LocalSharedLive = staticCompositionLocalOf<SharedLiveSession?> { null }

package com.ultratv.tv.nativeapp.ui.trailer

import android.view.KeyEvent
import java.util.Locale

/*
 * Lecteur de bande-annonce : toute la logique SANS Android (testable en JVM), hors les constantes de touches.
 * Le lecteur officiel YouTube (IFrame Player API) joue la vidéo dans une WebView ; l'interface de commandes est la nôtre
 * (voir TrailerPlayer.kt). Rien ici n'extrait le flux vidéo, ne masque le logo YouTube ni ne bloque les publicités.
 */

/**
 * Origine de la page hôte. YouTube refuse les intégrations sans Referer ni origine valides (erreurs 152/153) : la page
 * locale est donc chargée avec une base https stable, celle du site du projet. Seule l'origine est lue par YouTube ;
 * aucune requête n'est faite vers ce site. Repli possible : `https://ultratv.local` (valide aussi pour le contrôle de
 * schéma, mais sans propriétaire) — à n'utiliser que si l'origine du projet venait à changer.
 */
const val TRAILER_ORIGIN = "https://vantaflow.github.io"
const val TRAILER_BASE_URL = "$TRAILER_ORIGIN/ultra-tv/"

/** Nom de l'objet exposé au JavaScript de la page (`addJavascriptInterface`). */
const val TRAILER_BRIDGE = "UltraTrailer"

/** Pas des commandes −10 s / +10 s. */
const val TRAILER_SKIP_S = 10

/** Délai de masquage des commandes. */
const val TRAILER_OSD_MS = 3000L

/** Sans aucune réponse du lecteur au bout de ce délai (hors ligne, WebView trop ancienne) : erreur claire plutôt qu'un écran noir. */
const val TRAILER_NO_ANSWER_MS = 15_000L

enum class TrailerErrorKind { NotFound, EmbedBlocked, Config, NoWebView, Unknown }

/** Codes d'erreur de l'API : 100/105 introuvable ou privée, 101/150 intégration interdite, 152/153 configuration de l'hôte. */
fun trailerErrorKind(code: Int): TrailerErrorKind = when (code) {
    100, 105 -> TrailerErrorKind.NotFound
    101, 150 -> TrailerErrorKind.EmbedBlocked
    152, 153 -> TrailerErrorKind.Config
    else -> TrailerErrorKind.Unknown
}

enum class TrailerPhase { Loading, Playing, Paused, Buffering, Ended, Error }

data class TrailerState(
    val phase: TrailerPhase = TrailerPhase.Loading,
    val positionS: Double = 0.0,
    val durationS: Double = 0.0,
    val error: TrailerErrorKind? = null,
    /** Le lecteur a répondu (onReady) : sert au délai « aucune réponse ». */
    val ready: Boolean = false,
) {
    val playing: Boolean get() = phase == TrailerPhase.Playing || phase == TrailerPhase.Buffering
}

/** Ce que la page envoie à Kotlin par le pont JavaScript. */
sealed interface TrailerEvent {
    data object Ready : TrailerEvent
    /** Code `YT.PlayerState` : -1 non démarrée, 0 fin, 1 lecture, 2 pause, 3 mise en mémoire, 5 prête. */
    data class PlayerState(val code: Int) : TrailerEvent
    data class Time(val positionS: Double, val durationS: Double) : TrailerEvent
    data class Error(val code: Int) : TrailerEvent
    /** Échec côté appli (WebView absente ou plantée, aucune réponse). */
    data class Failed(val kind: TrailerErrorKind) : TrailerEvent
    /** Saut demandé par l'utilisateur : la barre bouge tout de suite, le lecteur confirmera. */
    data class Seeked(val positionS: Double) : TrailerEvent
}

private fun phaseOf(code: Int): TrailerPhase? = when (code) {
    1 -> TrailerPhase.Playing
    2 -> TrailerPhase.Paused
    3 -> TrailerPhase.Buffering
    0 -> TrailerPhase.Ended
    else -> null
}

/** Nouvel état après un événement ; une erreur est définitive (plus aucun changement). */
fun TrailerState.reduce(e: TrailerEvent): TrailerState {
    if (phase == TrailerPhase.Error) return this
    return when (e) {
        TrailerEvent.Ready -> copy(ready = true)
        is TrailerEvent.PlayerState -> phaseOf(e.code)?.let { copy(phase = it) } ?: this
        is TrailerEvent.Time -> copy(
            positionS = if (e.positionS.isFinite() && e.positionS >= 0) e.positionS else positionS,
            durationS = if (e.durationS.isFinite() && e.durationS > 0) e.durationS else durationS,
        )
        is TrailerEvent.Error -> copy(phase = TrailerPhase.Error, error = trailerErrorKind(e.code))
        is TrailerEvent.Failed -> copy(phase = TrailerPhase.Error, error = e.kind)
        is TrailerEvent.Seeked -> copy(positionS = e.positionS)
    }
}

/** Position après un saut, bornée à 0 et (si la durée est connue) à la durée. */
fun clampSeek(positionS: Double, deltaS: Double, durationS: Double): Double {
    val p = (if (positionS.isFinite()) positionS else 0.0) + deltaS
    val max = if (durationS > 0) durationS else Double.MAX_VALUE
    return p.coerceIn(0.0, max)
}

/** Position (secondes) pour une fraction de la barre (0..1) ; 0 tant que la durée est inconnue. */
fun seekFromRatio(ratio: Float, durationS: Double): Double =
    if (durationS > 0 && ratio.isFinite()) ratio.coerceIn(0f, 1f) * durationS else 0.0

/** Fraction lue (0..1) pour la barre de progression. */
fun progressRatio(positionS: Double, durationS: Double): Float =
    if (durationS > 0) (positionS / durationS).toFloat().coerceIn(0f, 1f) else 0f

/** « m:ss » ou « h:mm:ss ». */
fun trailerClock(seconds: Double): String {
    val t = if (seconds.isFinite() && seconds > 0) seconds.toLong() else 0L
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/**
 * Masquage des commandes : vrai quand [delayMs] s'est écoulé sans activité, sauf si elles doivent rester
 * ([keepVisible] : pause, chargement, fin, erreur).
 */
fun osdShouldHide(lastActivityMs: Long, nowMs: Long, keepVisible: Boolean, delayMs: Long = TRAILER_OSD_MS): Boolean =
    !keepVisible && nowMs - lastActivityMs >= delayMs

/** Les commandes restent affichées tant que la vidéo ne joue pas. */
fun osdKeepVisible(state: TrailerState): Boolean = state.phase != TrailerPhase.Playing

enum class TrailerAction { Toggle, Play, Pause, SkipBack, SkipForward, Close, Reveal }

/**
 * Action d'une touche (télécommande ou clavier), ou null si la touche est laissée au focus Compose
 * (OK sur un bouton affiché : le bouton réagit lui-même). Gauche / Droite sautent toujours de ±10 s ;
 * commandes masquées : OK bascule lecture / pause, Haut / Bas réaffichent seulement.
 */
fun trailerKeyAction(keyCode: Int, osdVisible: Boolean): TrailerAction? = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_SPACE -> TrailerAction.Toggle
    KeyEvent.KEYCODE_MEDIA_PLAY -> TrailerAction.Play
    KeyEvent.KEYCODE_MEDIA_PAUSE -> TrailerAction.Pause
    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD, KeyEvent.KEYCODE_DPAD_LEFT -> TrailerAction.SkipBack
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT -> TrailerAction.SkipForward
    KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_ESCAPE -> TrailerAction.Close
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (osdVisible) null else TrailerAction.Toggle
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (osdVisible) null else TrailerAction.Reveal
    else -> null
}

/**
 * Ordres à envoyer pour « lecture / pause » : en lecture on met en pause ; à la fin on repart du début ; sinon on lance.
 */
fun toggleCommands(state: TrailerState): List<String> = when {
    state.playing -> listOf(TrailerJs.PAUSE)
    state.phase == TrailerPhase.Ended -> listOf(TrailerJs.seek(0.0), TrailerJs.PLAY)
    else -> listOf(TrailerJs.PLAY)
}

/** Ordres envoyés à la page (`evaluateJavascript`). */
object TrailerJs {
    const val PLAY = "ut.play()"
    const val PAUSE = "ut.pause()"
    fun seek(seconds: Double): String = String.format(Locale.ROOT, "ut.seek(%.2f)", if (seconds.isFinite()) seconds.coerceAtLeast(0.0) else 0.0)
}

private val KEY = Regex("[A-Za-z0-9_-]{11}")

/**
 * Page HTML locale qui crée le lecteur officiel (IFrame Player API, hôte youtube-nocookie) sans ses commandes
 * (`controls=0`), sans vidéos liées (`rel=0`), en lecture intégrée (`playsinline=1`). Elle envoie l'état, la position,
 * la durée et les erreurs au pont [TRAILER_BRIDGE], et reçoit nos ordres par `ut.play()`, `ut.pause()`, `ut.seek(s)`.
 * [key] est vérifiée (11 caractères sûrs) avant d'être écrite dans la page.
 */
fun trailerPageHtml(key: String, origin: String = TRAILER_ORIGIN): String {
    require(KEY.matches(key)) { "clé YouTube invalide" }
    require(origin.startsWith("https://") && origin.none { it == '\'' || it == '"' || it == '<' || it == '\\' || it.isWhitespace() }) { "origine invalide" }
    return """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
</head><body><div id="p"></div>
<script>
var B=window.$TRAILER_BRIDGE,P=null,T=0;
function sReady(){try{B.onReady()}catch(e){}}
function sState(c){try{B.onState(c)}catch(e){}}
function sTime(a,b){try{B.onTime(a,b)}catch(e){}}
function sErr(c){try{B.onError(c)}catch(e){}}
window.ut={
  play:function(){if(P)P.playVideo()},
  pause:function(){if(P)P.pauseVideo()},
  seek:function(s){if(P)P.seekTo(s,true)}
};
function tick(){if(!P||!P.getCurrentTime)return;sTime(P.getCurrentTime()||0,P.getDuration()||0)}
function onYouTubeIframeAPIReady(){
  P=new YT.Player('p',{
    videoId:'$key',host:'https://www.youtube-nocookie.com',width:'100%',height:'100%',
    playerVars:{autoplay:1,controls:0,rel:0,playsinline:1,modestbranding:1,iv_load_policy:3,cc_load_policy:0,disablekb:1,fs:0,origin:'$origin'},
    events:{
      onReady:function(){try{P.unloadModule('captions')}catch(e){}sReady();tick();T=setInterval(tick,250);try{P.playVideo()}catch(e){}},
      onStateChange:function(e){sState(e.data);tick()},
      onError:function(e){sErr(e.data)}
    }
  });
}
</script>
<script src="https://www.youtube.com/iframe_api" onerror="sErr(-1)"></script>
</body></html>"""
}

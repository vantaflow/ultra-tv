package com.ultratv.tv.nativeapp.ui.live

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.PlaybackResolver
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.streamProps
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.memory.ramState
import com.ultratv.tv.nativeapp.data.prefs.SubtitlePrefsStore
import com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.player.engine.LiveEnv
import com.ultratv.tv.nativeapp.ui.player.engine.LiveOwner
import com.ultratv.tv.nativeapp.ui.player.engine.PlaybackSession
import com.ultratv.tv.nativeapp.ui.player.engine.PrefsChannelPlaybackMemory
import com.ultratv.tv.nativeapp.ui.player.engine.SharedLiveSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import javax.inject.Singleton
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/**
 * Aperçu VIDÉO du Direct : la chaîne focalisée joue dans le cadre de droite après un court délai ; OK la passe en
 * plein écran sans recharger le flux (voir [SharedLiveSession]).
 *
 * Choix par défaut (1.2.73) : aperçu AVEC le son, demandé à l'usage (un aperçu muet passait pour une panne). Réglage
 * « Son de l'aperçu » pour le couper ; un choix déjà fait (clé « sound » présente) est conservé.
 * Délai par défaut 0,4 s (0,7 s avant : 2 à 3,3 s mesurées entre la touche et l'image sur une box 2 Go), réglable de
 * 0,3 à 3 s : plus court, il lance davantage de flux en parcourant la liste (abonnements à une seule connexion).
 * « Auto » : aperçu activé sauf sur les box à faible mémoire (un décodeur vidéo de plus en mémoire pendant la
 * navigation dans la liste). Tactile : jamais (pas de cadre d'aperçu fixe sur téléphone ; tablette inchangée).
 */
object LivePreviewPolicy {
    const val DEFAULT_DELAY_MS = 400
    /** Choix proposés dans Réglages › Lecture › Délai avant l'aperçu. */
    val DELAY_CHOICES = listOf(300, 400, 700, 1_000, 1_500, 3_000)
    const val MIN_DELAY_MS = 300
    const val MAX_DELAY_MS = 3_000

    /** Mode du réglage : "auto" | "on" | "off". */
    fun enabled(mode: String, lowRam: Boolean, touch: Boolean): Boolean = !touch && when (mode) {
        "on" -> true
        "off" -> false
        else -> !lowRam
    }

    fun delayMs(ms: Int): Long = ms.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS).toLong()

    /** Guide : on le parcourt ligne par ligne ; l'aperçu attend une vraie pause (1,5 s au moins), sinon chaque
     *  chaîne survolée se met à jouer, comme un zapping (retour utilisateur). */
    const val GUIDE_MIN_DELAY_MS = 1_500L
    fun guideDelayMs(ms: Int): Long = maxOf(delayMs(ms), GUIDE_MIN_DELAY_MS)

    enum class Action { KEEP, START, STOP }

    /**
     * Que faire quand le focus se pose (après le délai) sur la ligne [focusedRow] ?
     * - chaîne verrouillée : jamais d'aperçu (le code parental serait contourné) → arrêt ;
     * - plein écran en cours : rien (l'aperçu n'est pas affiché) ;
     * - la ligne joue déjà : rien (aucun rechargement) ;
     * - retour du plein écran ([holdAfterReturn]) : la chaîne du dernier zap reste tant que l'utilisateur n'a pas
     *   bougé dans la liste (la restauration du focus sur l'ancienne ligne ne doit pas relancer un flux) ;
     * - sinon : lancement.
     */
    fun decide(owner: LiveOwner, playingRow: String?, focusedRow: String, locked: Boolean, holdAfterReturn: Boolean): Action = when {
        locked -> if (owner == LiveOwner.PREVIEW) Action.STOP else Action.KEEP
        owner == LiveOwner.FULLSCREEN -> Action.KEEP
        owner == LiveOwner.PREVIEW && playingRow == focusedRow -> Action.KEEP
        owner == LiveOwner.PREVIEW && holdAfterReturn -> Action.KEEP
        else -> Action.START
    }
}

fun channelKey(c: ChannelEntity) = "${c.providerId}:${c.remoteId}"

/** Réglages de l'aperçu, à part des préférences générales (aucun conflit avec les autres chantiers). */
@Singleton
class LivePreviewPrefs @Inject constructor(@ApplicationContext ctx: Context) {
    data class Values(val mode: String = "auto", val sound: Boolean = true, val delayMs: Int = LivePreviewPolicy.DEFAULT_DELAY_MS)

    private val sp = ctx.getSharedPreferences("live_preview", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Values> = _state.asStateFlow()

    private fun read() = Values(
        mode = sp.getString("mode", "auto") ?: "auto",
        sound = sp.getBoolean("sound", true),
        delayMs = sp.getInt("delay_ms", LivePreviewPolicy.DEFAULT_DELAY_MS),
    )

    fun setMode(v: String) { sp.edit().putString("mode", v).apply(); _state.value = read() }
    fun setSound(v: Boolean) { sp.edit().putBoolean("sound", v).apply(); _state.value = read() }
    fun setDelayMs(v: Int) { sp.edit().putInt("delay_ms", v.coerceIn(LivePreviewPolicy.MIN_DELAY_MS, LivePreviewPolicy.MAX_DELAY_MS)).apply(); _state.value = read() }
}

/** Dépendances de la session partagée créée par l'aperçu (mêmes réglages que le lecteur plein écran). */
@UnstableApi
@HiltViewModel
class LivePreviewViewModel @Inject constructor(
    private val prefs: UserPreferencesStore,
    private val adaptive: AdaptiveProfile,
    private val memory: PrefsChannelPlaybackMemory,
    private val network: NetworkMonitor,
    subtitleStore: SubtitlePrefsStore,
    private val channelDao: ChannelDao,
    val previewPrefs: LivePreviewPrefs,
    private val memoryPrefs: com.ultratv.tv.nativeapp.data.memory.MemoryPrefs,
    @ApplicationContext private val appCtx: Context,
) : ViewModel() {
    val userPrefs: StateFlow<UserPrefs?> = prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val subtitles: StateFlow<SubtitleSettings> = subtitleStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleSettings())
    /** Mémoire limitée au sens du mode « économie de mémoire » (Réglages › Mémoire et stockage) : Auto = l'appareil. */
    val lowRam: Boolean get() = com.ultratv.tv.nativeapp.data.memory.EconomyMode.effective(
        memoryPrefs.state.value.economy,
        // Auto : seulement le profil « mémoire faible » d'avant. La règle « moins de 2 Go » de la rubrique Mémoire classait
        // les box 2 Go en appareil limité et coupait l'aperçu vidéo du Direct, qui y tournait très bien.
        adaptive.state.value.auto.lowRam,
    )

    fun env(): LiveEnv = LiveEnv(
        settings = { PlaybackResolver.resolve(adaptive.state.value.auto, userPrefs.value ?: UserPrefs(), adaptive.heapClassMb) },
        subtitles = { subtitles.value },
        uiLanguage = { (userPrefs.value?.language ?: "system").let { if (it == "system") java.util.Locale.getDefault().language else it } },
    )

    /** Session du Direct : réglages lus via [SharedLiveSession.env] (rebranchés par le lecteur plein écran). */
    fun createSession(ctx: Context, scope: CoroutineScope, shared: SharedLiveSession, textureView: Boolean): PlaybackSession {
        val p = userPrefs.value ?: UserPrefs()
        val fallback = env()
        return PlaybackSession(
            ctx = ctx, scope = scope, container = FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) },
            settings = { (shared.env ?: fallback).settings() }, memory = memory, network = network,
            isLive = true, autoFrameRate = p.autoFrameRate, userAgent = "UltraTV/1.0 (Android TV)",
            opener = { u -> com.ultratv.tv.nativeapp.data.stalker.StreamUrls.open(ctx, u) },
            presence = { u -> com.ultratv.tv.nativeapp.data.stalker.StreamUrls.presence(ctx, u) },   // stalker:// : lien + en-têtes MAG à chaque ouverture
            textureView = textureView,
            subtitles = { (shared.env ?: fallback).subtitles() },
            uiLanguage = { (shared.env ?: fallback).uiLanguage() },
            propsLookup = { k -> channelDao.streamProps(k) },
        )
    }

    suspend fun channelByKey(key: String): ChannelEntity? {
        val pid = key.substringBefore(':').toLongOrNull() ?: return null
        return channelDao.byRemoteId(pid, key.substringAfter(':'))
    }
}

class LivePreviewStrings(val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)
    val row get() = tx(R.string.lp_row)
    val hint get() = tx(R.string.lp_hint)
    val sound get() = tx(R.string.lp_sound)
    val delay get() = tx(R.string.lp_delay)
    val delayHint get() = tx(R.string.lp_delay_hint)
    fun seconds(ms: Int): String {
        val s = if (ms % 1_000 == 0) (ms / 1_000).toString() else "%.1f".format(java.util.Locale.ROOT, ms / 1_000f)
        return tx(R.string.lp_seconds, if (lang == AppLang.French) s.replace('.', ',') else s)
    }
    val autoLowRam get() = tx(R.string.lp_auto_low_ram)
    val on get() = tx(R.string.lp_on)
    val off get() = tx(R.string.lp_off)
}

@Composable
fun livePreviewStrings(): LivePreviewStrings {
    val lang = LocalDs.current.lang
    return remember(lang) { LivePreviewStrings(lang) }
}

/** Réglages › Lecture : « Aperçu vidéo dans le Direct » et « Son de l'aperçu ». */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LivePreviewSettingsRows() {
    val vm: LivePreviewViewModel = hiltViewModel()
    val v by vm.previewPrefs.state.collectAsState()
    val L = livePreviewStrings()
    val D = LocalDs.current
    var choice by remember { mutableStateOf(false) }
    var delayChoice by remember { mutableStateOf(false) }
    val label = when (v.mode) { "on" -> L.on; "off" -> L.off; else -> D.auto + " · " + (if (vm.lowRam) L.off else L.on) }
    com.ultratv.tv.nativeapp.ui.design.PrefRow(L.row, label, hint = L.hint) { choice = true }
    if (v.mode != "off") {
        com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow(L.sound, v.sound) { vm.previewPrefs.setSound(it) }
        com.ultratv.tv.nativeapp.ui.design.PrefRow(L.delay, L.seconds(v.delayMs), hint = L.delayHint) { delayChoice = true }
    }
    if (delayChoice) com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(
        L.delay, LivePreviewPolicy.DELAY_CHOICES.map { it to L.seconds(it) }, v.delayMs,
        { vm.previewPrefs.setDelayMs(it); delayChoice = false }, { delayChoice = false },
    )
    if (choice) com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(
        L.row, listOf("auto" to L.autoLowRam, "on" to L.on, "off" to L.off), v.mode,
        { vm.previewPrefs.setMode(it); choice = false }, { choice = false },
    )
}

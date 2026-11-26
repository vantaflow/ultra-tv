package com.ultratv.tv.nativeapp.ui.startup

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.MainActivity
import com.ultratv.tv.nativeapp.RemoteLog
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.db.ENSURE_DEFAULT_PROFILE_SQL
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Garde-fou du premier écran : tant que « Qui regarde ? » ou l'assistant n'ont pas répondu, l'application ne reste jamais
 * sur un écran vide. Écran de chargement après [LOADING_AFTER_MS], puis écran d'erreur avec « Réessayer » et
 * « Réinitialiser les données locales » après [ERROR_AFTER_MS].
 */
object StartupWatchdog {
    const val LOADING_AFTER_MS = 3_000L
    const val ERROR_AFTER_MS = 30_000L

    enum class Phase { BLANK, LOADING, ERROR }

    fun phase(elapsedMs: Long): Phase = when {
        elapsedMs >= ERROR_AFTER_MS -> Phase.ERROR
        elapsedMs >= LOADING_AFTER_MS -> Phase.LOADING
        else -> Phase.BLANK
    }

    class Words(
        val title: String, val body: String, val retry: String, val reset: String,
        val confirmPaired: String, val confirmUnpaired: String, val confirmYes: String, val cancel: String,
    )

    /** Textes de l'écran d'erreur selon la langue (code ISO) ; anglais par défaut. */
    fun text(lang: String): Words = when (lang.lowercase().take(2)) {
        "fr" -> Words("Le démarrage est bloqué", "Les données locales ne répondent pas. Réessayez ; sinon réinitialisez-les, votre configuration reviendra depuis le cloud.", "Réessayer", "Réinitialiser les données locales",
            "Effacer les données locales ? Vos sources et réglages reviendront depuis le cloud.",
            "Effacer les données locales ? Cet appareil n'est pas relié au cloud : vos sources, favoris et réglages seront perdus définitivement.",
            "Oui, tout effacer", "Annuler")
        "es" -> Words("El inicio está bloqueado", "Los datos locales no responden. Inténtelo de nuevo; si no, restablézcalos y su configuración volverá desde la nube.", "Reintentar", "Restablecer los datos locales",
            "¿Borrar los datos locales? Sus fuentes y ajustes volverán desde la nube.",
            "¿Borrar los datos locales? Este dispositivo no está vinculado a la nube: se perderán para siempre sus fuentes, favoritos y ajustes.",
            "Sí, borrar todo", "Cancelar")
        else -> Words("Startup is stuck", "Local data is not responding. Try again; otherwise reset it and your configuration will come back from the cloud.", "Retry", "Reset local data",
            "Erase local data? Your sources and settings will come back from the cloud.",
            "Erase local data? This device is not linked to the cloud: your sources, favorites and settings will be lost for good.",
            "Yes, erase everything", "Cancel")
    }
}

/**
 * Flux de démarrage sans null éternel : une exception est journalisée ([RemoteLog], tag « startup ») et remplacée par [fallback]
 * (le flux se termine alors sur cette valeur) au lieu de laisser un StateFlow sur null, donc un écran noir.
 */
fun <T> Flow<T>.catchWithFallback(name: String, fallback: T, log: (String) -> Unit = { RemoteLog.error("startup", it) }): Flow<T> =
    catch { e ->
        log("$name a échoué : ${e.javaClass.simpleName} ${e.message}")
        emit(fallback)
    }

@HiltViewModel
class StartupRecoveryViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: UltraDb,
    private val tokens: DeviceTokenStore,
) : ViewModel() {

    /** Appareil relié au cloud (jeton présent) ; faux tant que non lu, donc l'avertissement le plus fort par défaut. */
    val paired = MutableStateFlow(false)

    init {
        viewModelScope.launch { paired.value = withContext(Dispatchers.IO) { runCatching { tokens.token() != null }.getOrDefault(false) } }
    }

    /** Réessayer : repose le profil par défaut s'il manque, puis relance le processus (flux et ViewModels neufs). */
    fun retry() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL(ENSURE_DEFAULT_PROFILE_SQL) } }
                .onFailure { RemoteLog.error("startup", "retry : profil par défaut impossible : ${it.javaClass.simpleName} ${it.message}") }
            restart()
        }
    }

    /** Efface la base locale (le catalogue et la configuration se reconstruisent depuis le cloud), puis relance. */
    fun reset() {
        viewModelScope.launch {
            RemoteLog.warn("startup", "réinitialisation des données locales demandée depuis l'écran d'erreur")
            RemoteLog.flushNow()
            withContext(Dispatchers.IO) {
                delay(800) // laisse partir le journal ci-dessus : la file en mémoire est perdue à l'arrêt du processus
                runCatching { db.close() }
                runCatching { ctx.deleteDatabase(DB_NAME) }
                    .onFailure { RemoteLog.error("startup", "reset : suppression impossible : ${it.javaClass.simpleName} ${it.message}") }
            }
            restart()
        }
    }

    /**
     * Relance dans un processus neuf. Pas de délai entre l'ouverture de l'activité et l'arrêt du processus : avec
     * CLEAR_TASK, l'activité actuelle est détruite, son ViewModel aussi, et un `delay` dans viewModelScope était annulé
     * avant l'arrêt. Le processus survivait alors avec la base fermée (singleton Hilt) : « Database is closed » plus tard,
     * puis de nouveau l'écran « démarrage bloqué ». startActivity est un appel synchrone au système : la relance est
     * enregistrée quand il rend la main, le système la fait dans un nouveau processus.
     */
    private fun restart() {
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { ctx.startActivity(intent) }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object { const val DB_NAME = "ultra-tv.db" }
}

/** À montrer à la place d'un écran vide quand un flux de démarrage n'a pas encore émis ; [loading] = écran de chargement. */
@Composable
fun StartupGateWait(loading: @Composable () -> Unit) {
    val elapsed by produceState(0L) {
        val t0 = System.nanoTime()
        while (true) { delay(500); value = (System.nanoTime() - t0) / 1_000_000 }
    }
    when (StartupWatchdog.phase(elapsed)) {
        StartupWatchdog.Phase.BLANK -> Box(Modifier.fillMaxSize().background(Ux.Bg))
        StartupWatchdog.Phase.LOADING -> loading()
        StartupWatchdog.Phase.ERROR -> StartupErrorScreen()
    }
}

@Composable
private fun StartupErrorScreen(vm: StartupRecoveryViewModel = hiltViewModel()) {
    val t = remember { StartupWatchdog.text(java.util.Locale.getDefault().language) }
    val paired by vm.paired.collectAsState()
    var step by remember { mutableStateOf(ResetConfirmation.Step.IDLE) }
    androidx.compose.runtime.LaunchedEffect(Unit) { StartupTraceLog.logError() }
    Box(Modifier.fillMaxSize().background(Ux.Bg), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 900.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.design)) {
            val r = remember { FocusRequester() }
            var focused by remember { mutableStateOf(false) }
            RequestInitialFocus(r, hasFocus = { focused })
            if (step == ResetConfirmation.Step.IDLE) {
                Text(t.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, textAlign = TextAlign.Center)
                Text(t.body, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, textAlign = TextAlign.Center)
                PillButton(t.retry, vm::retry, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(r).onFocusChanged { focused = it.isFocused })
                PillButton(t.reset, { step = ResetConfirmation.onResetPressed(step) })
            } else {
                Text(t.reset, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, textAlign = TextAlign.Center)
                Text(ResetConfirmation.body(t, paired), color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, textAlign = TextAlign.Center)
                // Le focus par défaut reste sur l'action sûre (Annuler) : un OK réflexe ne détruit rien.
                PillButton(t.cancel, { step = ResetConfirmation.onCancel(step) }, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(r).onFocusChanged { focused = it.isFocused })
                PillButton(t.confirmYes, { if (ResetConfirmation.shouldReset(step)) vm.reset() })
            }
        }
    }
}

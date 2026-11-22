package com.ultratv.tv.nativeapp.ui.common

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.ultratv.tv.nativeapp.UtvLog
import com.ultratv.tv.nativeapp.data.net.NetErrors
import kotlin.coroutines.cancellation.CancellationException

/**
 * Journal des erreurs d'écran, commun à tous les écrans refaits. Une ligne par échec, jamais d'identifiant, de mot de passe
 * ni d'adresse de flux complète : tout passe par [UtvLog.mask] / [UtvLog.maskUrl] (donc [com.ultratv.tv.nativeapp.LogSanitizer]).
 * Tag `ui.<écran>` ; envoi par [UtvLog.w] (logcat masqué + journal de l'appareil, la version de l'appli est ajoutée par `RemoteLog`).
 */
object UiLog {
    /** Reçoit la même ligne MASQUÉE que le journal (tests seulement). */
    @VisibleForTesting
    internal var sink: ((tag: String, message: String) -> Unit)? = null

    /** `<action> · <catégorie> · <classe d'exception: message masqué>` puis ` · <adresse masquée>` si [url] est donnée. */
    fun failure(screen: String, action: String, t: Throwable, url: String? = null) {
        val base = "$action · ${NetErrors.classify(t)} · ${UtvLog.why(t)}"
        emit("ui.$screen", if (url != null) "$base · ${UtvLog.maskUrl(url)}" else base)
    }

    internal fun emit(tag: String, message: String) {
        val masked = UtvLog.mask(message)
        runCatching { sink?.invoke(tag, masked) }
        UtvLog.w(tag, masked)
    }
}

/**
 * Remplace `runCatching` dans le code d'écran : même résultat, mais [CancellationException] est relancée sans rien
 * journaliser, et tout autre échec laisse UNE ligne ([UiLog.failure]) avant de rendre `Result.failure`.
 */
suspend inline fun <T> uiAttempt(screen: String, action: String, url: String? = null, block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        UiLog.failure(screen, action, t, url)
        Result.failure(t)
    }

/**
 * Synthèse des images introuvables d'un écran : au plus UNE ligne par visite (au [flush]), jamais une par image.
 * Les hôtes sont masqués (aucun identifiant) et limités à trois.
 */
class ImageFailureTally(val screen: String) {
    private var count = 0
    private val hosts = LinkedHashSet<String>()

    fun note(url: String?) {
        count++
        if (hosts.size < 3 && !url.isNullOrBlank()) {
            val host = UtvLog.maskUrl(url).substringAfter("://").substringBefore('/').substringBefore('?')
            if (host.isNotBlank()) hosts += host
        }
    }

    fun flush() {
        if (count <= 0) return
        val n = count
        val h = hosts.joinToString(", ").ifEmpty { "-" }
        count = 0
        hosts.clear()
        UiLog.emit("ui.$screen", "images introuvables : $n (hôtes : $h)")
    }
}

/** Synthèse en cours de l'écran affiché ; null partout ailleurs (aucun coût hors des écrans refaits). */
val LocalImageFailures = staticCompositionLocalOf<ImageFailureTally?> { null }

/** Synthèse de l'écran, envoyée une fois quand il quitte la composition. */
@Composable
fun rememberImageFailureTally(screen: String): ImageFailureTally {
    val tally = remember(screen) { ImageFailureTally(screen) }
    DisposableEffect(tally) { onDispose { tally.flush() } }
    return tally
}

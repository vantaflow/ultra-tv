package com.ultratv.tv.nativeapp.update

import com.ultratv.tv.nativeapp.ui.design.Ux

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.StateCard
import com.ultratv.tv.nativeapp.ui.design.StateIcons
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invite de mise à jour (carte d'état de la maquette Etats) : « Installer » télécharge l'APK de la
 * release (vérifié avant installation) puis lance l'installation système ; « Plus tard » ferme.
 * Le message d'erreur affiché est celui, localisé, de l'application : jamais le texte brut d'une exception.
 */
@Composable
fun UpdateDialog() {
    val info by UpdateChecker.state.collectAsState()
    val update = info ?: return
    var dismissed by remember(update.tag) { mutableStateOf(false) }
    if (dismissed) return

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember(update.tag) { mutableStateOf(false) }
    var progress by remember(update.tag) { mutableFloatStateOf(0f) }
    var failed by remember(update.tag) { mutableStateOf(false) }
    var mismatch by remember(update.tag) { mutableStateOf(false) }
    var launched by remember(update.tag) { mutableStateOf(false) }
    var job by remember(update.tag) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // « Plus tard » et Retour ferment TOUJOURS, même pendant le téléchargement (annulé) :
    // avant, la carte restait bloquée sur « Téléchargement… » si l'installation n'aboutissait pas.
    val close: () -> Unit = { job?.cancel(); downloading = false; dismissed = true }
    val s = LocalStrings.current
    val D = LocalDs.current

    ModalFocusScope(onBack = close, modifier = Modifier.background(com.ultratv.tv.nativeapp.ui.design.Ux.Scrim)) {
        StateCard(
            title = D.updateTitle(update.versionName),
            body = when {
                mismatch -> D.updateSignatureMismatch
                failed -> D.updateFailed
                launched -> D.updateLaunched
                else -> D.updateDefaultBody
            },
            iconPath = StateIcons.Download,
            primaryLabel = if (downloading) s.updateDownloading else s.updateInstall,
            onPrimary = {
                if (downloading || mismatch) return@StateCard
                downloading = true; failed = false; launched = false
                job = scope.launch {
                    runCatching {
                        // Revérifier juste avant : si une version plus récente est sortie depuis l'invite
                        // (ex. 1.2.4 affichée, 1.2.5 publiée entre-temps), c'est elle qu'on installe.
                        val target = UpdateChecker.checkForUpdate()?.takeIf { it.versionCode >= update.versionCode } ?: update
                        withContext(Dispatchers.IO) { UpdateChecker.downloadAndInstall(ctx, target) { p -> progress = p } }
                    }.onSuccess { launched = true }
                     .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; if (e is SignatureMismatchException) mismatch = true else failed = true }
                    downloading = false
                }
            },
            secondaryLabel = s.updateLater,
            onSecondary = close,
            progress = if (downloading) progress else null,
            modifier = Modifier.width(820.design),
        )
    }
}

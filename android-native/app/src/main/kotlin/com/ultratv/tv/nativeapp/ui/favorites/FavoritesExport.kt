package com.ultratv.tv.nativeapp.ui.favorites

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import com.ultratv.tv.nativeapp.data.m3u.M3uExport
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.favExportButton
import com.ultratv.tv.nativeapp.i18n.favExportCancel
import com.ultratv.tv.nativeapp.i18n.favExportDone
import com.ultratv.tv.nativeapp.i18n.favExportFailed
import com.ultratv.tv.nativeapp.i18n.favExportNone
import com.ultratv.tv.nativeapp.i18n.favExportSave
import com.ultratv.tv.nativeapp.i18n.favExportShare
import com.ultratv.tv.nativeapp.i18n.favExportShareUnavailable
import com.ultratv.tv.nativeapp.i18n.favExportTitle
import com.ultratv.tv.nativeapp.i18n.favExportWarning
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.launchOrToast
import com.ultratv.tv.nativeapp.ui.common.responsiveWidth
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * Export M3U des chaînes favorites : un bouton, un avertissement (les liens contiennent l'identifiant et le mot de passe de
 * l'abonnement), puis « Enregistrer » (sélecteur système) ou « Partager » (feuille de partage). Beaucoup de box n'ont pas de
 * sélecteur de fichiers : le lancement passe par [launchOrToast] et le partage reste possible.
 */
@Composable
internal fun FavoritesExportButton(vm: FavoritesViewModel) {
    val D = LocalDs.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }
    // Contenu en attente de l'adresse choisie dans le sélecteur de fichier (texte, nombre de chaînes).
    var pending by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val fileName = remember { Calendar.getInstance().let { M3uExport.fileName(it[Calendar.YEAR], it[Calendar.MONTH] + 1, it[Calendar.DAY_OF_MONTH]) } }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val data = pending
        pending = null
        if (uri != null && data != null) scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(data.first.toByteArray(Charsets.UTF_8)) } ?: error("flux indisponible") }
                .onSuccess { Toaster.ok(D.favExportDone(data.second)) }
                .onFailure { Toaster.err(D.favExportFailed) }
        }
    }

    PillButton(D.favExportButton, { dialog = true }, heightPx = 64, hPadPx = 30, fontPx = 22)

    if (dialog) ExportDialog(
        title = D.favExportTitle, body = D.favExportWarning,
        saveLabel = D.favExportSave, shareLabel = D.favExportShare, cancelLabel = D.favExportCancel,
        onDismiss = { dialog = false },
        onSave = {
            dialog = false
            scope.launch {
                val data = runCatching { vm.exportM3u() }.getOrNull()
                if (data == null) { Toaster.err(D.favExportNone); return@launch }
                pending = data
                if (!save.launchOrToast(fileName, D.filePickerUnavailable)) pending = null
            }
        },
        onShare = {
            dialog = false
            scope.launch {
                val data = runCatching { vm.exportM3u() }.getOrNull()
                if (data == null) { Toaster.err(D.favExportNone); return@launch }
                val uri = withContext(Dispatchers.IO) {
                    runCatching {
                        // Dossier temporaire vidé à chaque partage : le fichier contient des identifiants, il ne doit pas traîner.
                        val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                        val f = File(dir, fileName).apply { writeText(data.first, Charsets.UTF_8) }
                        FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", f)
                    }.getOrNull()
                }
                if (uri == null) { Toaster.err(D.favExportFailed); return@launch }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/x-mpegurl"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                    clipData = ClipData.newRawUri(fileName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    ctx.startActivity(Intent.createChooser(send, D.favExportShare).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    Toaster.err(D.favExportShareUnavailable)
                }
            }
        },
    )
}

/** Avertissement + choix. « Annuler » est le premier bouton (focus initial) : jamais un partage lancé par un OK distrait. */
@Composable
private fun ExportDialog(
    title: String, body: String, saveLabel: String, shareLabel: String, cancelLabel: String,
    onDismiss: () -> Unit, onSave: () -> Unit, onShare: () -> Unit,
) {
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.responsiveWidth(820).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design),
            verticalArrangement = Arrangement.spacedBy(16.design),
        ) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 3)
            Text(body, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx)
            Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                PillButton(cancelLabel, onDismiss, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
                PillButton(saveLabel, onSave, heightPx = 64, hPadPx = 30, fontPx = 22)
                PillButton(shareLabel, onShare, heightPx = 64, hPadPx = 30, fontPx = 22)
            }
        }
    }
}

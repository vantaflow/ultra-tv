package com.ultratv.tv.nativeapp.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.TRANSLATE_URL
import com.ultratv.tv.nativeapp.i18n.translateHelp
import com.ultratv.tv.nativeapp.i18n.translateHelpClose
import com.ultratv.tv.nativeapp.i18n.translateHelpScan
import com.ultratv.tv.nativeapp.i18n.translateHelpSub
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.isTelevision
import com.ultratv.tv.nativeapp.ui.design.*

/** Ouvre l'adresse dans le navigateur ; false s'il n'y en a aucun (le dialogue QR prend alors le relais). */
internal fun openTranslateLink(ctx: Context): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TRANSLATE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Ligne « Aider à traduire Ultra TV » de Réglages › Langue. Mobile / tactile : ouvre le navigateur, avec repli sur le
 * dialogue QR si aucune appli ne gère le lien. TV (pas de navigateur fiable) : dialogue QR directement.
 */
@Composable
fun TranslateHelpRow(D: DesignStrings = LocalDs.current) {
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    PrefRow(D.translateHelp, "", hint = D.translateHelpSub) {
        if (isTelevision(ctx) || !openTranslateLink(ctx)) dialog = true
    }
    if (dialog) TranslateQrDialog(D) { dialog = false }
}

/** Dialogue : QR code vers la page Weblate, adresse en clair, bouton Fermer (focus initial). Retour ferme. */
@Composable
fun TranslateQrDialog(D: DesignStrings, onClose: () -> Unit) {
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.widthIn(min = 560.design, max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.design),
        ) {
            Text(D.translateHelp, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 2, textAlign = TextAlign.Center)
            Text(D.translateHelpScan, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 3, textAlign = TextAlign.Center)
            QrCode(TRANSLATE_URL, 260.design)
            Text(TRANSLATE_URL, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 2, textAlign = TextAlign.Center)
            val req = remember { FocusRequester() }
            var f by remember { mutableStateOf(false) }
            RequestInitialFocus(req, hasFocus = { f })
            PillButton(D.translateHelpClose, onClick = onClose, heightPx = 68, hPadPx = 40, fontPx = 24, weight = FontWeight.Bold, bg = Ux.Cta, modifier = Modifier.focusRequester(req).onFocusChanged { f = it.isFocused })
        }
    }
}

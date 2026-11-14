package com.ultratv.tv.nativeapp.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.SUPPORT_URL
import com.ultratv.tv.nativeapp.i18n.supportScan
import com.ultratv.tv.nativeapp.i18n.supportSub
import com.ultratv.tv.nativeapp.i18n.supportTitle
import com.ultratv.tv.nativeapp.i18n.translateHelpClose
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.isTelevision
import com.ultratv.tv.nativeapp.ui.design.*

/**
 * Ligne « Soutenir Ultra TV » de Réglages › À propos. Téléphone / tablette : ouvre la page Ko-fi dans le navigateur (repli
 * sur le QR code si aucune appli ne gère le lien). TV : QR code Ko-fi à scanner avec le téléphone.
 */
@Composable
fun SupportRow(D: DesignStrings = LocalDs.current) {
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    PrefRow(D.supportTitle, "", hint = D.supportSub) {
        val opened = !isTelevision(ctx) && try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: ActivityNotFoundException) { false }
        if (!opened) dialog = true
    }
    if (dialog) SupportQrDialog(D) { dialog = false }
}

/** Dialogue : QR code Ko-fi (image fournie par Ko-fi, embarquée : aucun accès réseau), adresse en clair, Fermer. */
@Composable
fun SupportQrDialog(D: DesignStrings, onClose: () -> Unit) {
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.widthIn(min = 560.design, max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.design),
        ) {
            Text(D.supportTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 2, textAlign = TextAlign.Center)
            Text(D.supportScan, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 3, textAlign = TextAlign.Center)
            Image(painterResource(R.drawable.kofi_qr), contentDescription = SUPPORT_URL, modifier = Modifier.size(300.design).clip(RoundedCornerShape(16.design)))
            Text(SUPPORT_URL.removePrefix("https://"), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, textAlign = TextAlign.Center)
            val req = remember { FocusRequester() }
            var f by remember { mutableStateOf(false) }
            RequestInitialFocus(req, hasFocus = { f })
            PillButton(D.translateHelpClose, onClick = onClose, heightPx = 68, hPadPx = 40, fontPx = 24, weight = FontWeight.Bold, bg = Ux.Cta, modifier = Modifier.focusRequester(req).onFocusChanged { f = it.isFocused })
        }
    }
}

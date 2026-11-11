package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason
import com.ultratv.tv.nativeapp.data.xmltv.EpgProgress
import com.ultratv.tv.nativeapp.data.xmltv.EpgState
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.epgProgressText
import com.ultratv.tv.nativeapp.i18n.epgReason
import com.ultratv.tv.nativeapp.i18n.guideLoadingHint
import com.ultratv.tv.nativeapp.i18n.guideLoadingTitle
import com.ultratv.tv.nativeapp.i18n.guideRetry
import com.ultratv.tv.nativeapp.i18n.guideUnavailable
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/** Ce que le guide montre quand il n'a (encore) rien à afficher. */
sealed interface GuideLoad {
    /** Rien en cours, aucun échec connu. */
    data object Idle : GuideLoad
    /** Téléchargement en cours ; [progress] null tant que le guide n'a pas commencé (catalogue en cours de synchro). */
    data class Loading(val progress: EpgProgress?) : GuideLoad
    /** Échec : « Guide indisponible » + raison courte + « Réessayer ». */
    data class Unavailable(val reason: EpgFailReason) : GuideLoad
}

/** Fonction pure (testée en JVM) : état du guide d'après l'état du guide seul et la présence d'une synchro quelconque. */
fun guideLoadState(epg: EpgState, syncRunning: Boolean): GuideLoad = when (epg) {
    is EpgState.Loading -> GuideLoad.Loading(epg.progress)
    is EpgState.Failed -> GuideLoad.Unavailable(epg.reason)
    EpgState.Idle -> if (syncRunning) GuideLoad.Loading(null) else GuideLoad.Idle
}

/** Ligne d'avancement affichée sous le titre quand des programmes sont déjà visibles (« Chargement du guide… 12 / 45 Mo »). */
fun guideProgressLine(D: DesignStrings, load: GuideLoad): String? {
    val l = load as? GuideLoad.Loading ?: return null
    val p = l.progress
    return if (p == null || p.bytesRead <= 0) D.guideLoadingTitle else "${D.guideLoadingTitle} ${epgProgressText(D, p.bytesRead, p.totalBytes, p.programmes)}"
}

/** Corps du guide vide : chargement avec progression, ou « Guide indisponible » avec raison et bouton « Réessayer ». */
@Composable
fun GuideEmptyState(load: GuideLoad, D: DesignStrings, onRetry: () -> Unit, touch: Boolean, modifier: Modifier = Modifier) {
    val unavailable = load !is GuideLoad.Loading
    val reason = (load as? GuideLoad.Unavailable)?.reason ?: EpgFailReason.EMPTY
    val title = if (unavailable) D.guideUnavailable else D.guideLoadingTitle
    val body = if (unavailable) epgReason(D, reason) else (guideProgressLine(D, load)?.removePrefix(D.guideLoadingTitle)?.trim().orEmpty().ifEmpty { D.guideLoadingHint })
    val focus = remember { FocusRequester() }
    if (unavailable && !touch) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (touch) 10.dp else 14.design)) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 20.sp else 34.spx, maxLines = 2)
        Text(body, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 15.sp else 24.spx, lineHeight = if (touch) 22.sp else 33.spx, maxLines = 3)
        if (unavailable) {
            if (touch) PillButton(D.guideRetry, onClick = onRetry, modifier = Modifier.focusRequester(focus).padding(top = 6.dp), heightPx = 44, hPadPx = 24, fontPx = 15, bg = Ux.Cta)
            else PillButton(D.guideRetry, onClick = onRetry, modifier = Modifier.focusRequester(focus), heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
        }
    }
}

package com.ultratv.tv.nativeapp.ui.detail

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.i18n.detailNotFound
import com.ultratv.tv.nativeapp.i18n.episodesLoadError
import com.ultratv.tv.nativeapp.ui.common.InputClock
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.movies.Skeleton
import kotlinx.coroutines.delay

/** Nombre d'essais (toutes les 50 ms) de la demande de focus initial des états de fiche. */
private const val STATE_FOCUS_ATTEMPTS = 30

/**
 * Ce que montre une fiche (film ou série, TV et toucher) tant que son titre n'est pas [DetailLoad.Ready].
 *
 * - [DetailLoad.Loading] : silhouette de la fiche (barre de titre, ligne d'infos, deux boutons en [Skeleton]), sans texte
 *   ni animation (le lecteur d'écran lit « Chargement… » par la description du bloc) ;
 * - [DetailLoad.NotFound] : « Ce titre n'est plus disponible » + Retour ;
 * - [DetailLoad.Failed] : cause (`S.sync.messageFor`) + Réessayer + Retour.
 *
 * Télécommande : le focus initial va à la première action, UNE fois, et s'arrête dès qu'une touche a été pressée depuis
 * l'ouverture (règle de l'accueil : jamais de focus déplacé sous les doigts de l'utilisateur). Au toucher, aucun focus
 * programmatique. `testTag` : `dt-state`, `dt-state-retry`, `dt-state-back`.
 */
@Composable
fun DetailStatePanel(state: DetailLoad, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val touch = LocalTouch.current
    val S = LocalStrings.current
    val D = LocalDs.current
    val M = LocalMobileStrings.current
    Box(modifier.fillMaxSize().background(Ux.Bg).testTag("dt-state")) {
        when (state) {
            DetailLoad.Ready -> Unit
            DetailLoad.Loading -> LoadingSilhouette(touch, S.detailLoading)
            DetailLoad.NotFound -> StateMessage(touch, D.detailNotFound, retryLabel = null, backLabel = M.a11yBack, onRetry = onRetry, onBack = onBack)
            is DetailLoad.Failed -> StateMessage(touch, S.sync.messageFor(state.kind), retryLabel = D.retry, backLabel = M.a11yBack, onRetry = onRetry, onBack = onBack)
        }
    }
}

@Composable
private fun LoadingSilhouette(touch: Boolean, description: String) {
    val pad = if (touch) 20.dp else 72.design
    Column(
        Modifier.fillMaxSize().padding(start = pad, end = pad, top = if (touch) 72.dp else 120.design).semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 22.design),
    ) {
        Skeleton(if (touch) Modifier.fillMaxWidth(0.7f).height(30.dp) else Modifier.width(520.design).height(48.design))
        Skeleton(if (touch) Modifier.fillMaxWidth(0.5f).height(16.dp) else Modifier.width(420.design).height(26.design))
        if (touch) {
            Skeleton(Modifier.fillMaxWidth().height(52.dp)); Skeleton(Modifier.fillMaxWidth().height(52.dp))
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
            Skeleton(Modifier.width(260.design).height(64.design)); Skeleton(Modifier.width(260.design).height(64.design))
        }
    }
}

@Composable
private fun StateMessage(touch: Boolean, message: String, retryLabel: String?, backLabel: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val openedAt = remember { SystemClock.uptimeMillis() }
    // Focus initial (télécommande) : une fois, sans jamais passer devant une touche pressée par l'utilisateur.
    if (!touch) LaunchedEffect(Unit) {
        repeat(STATE_FOCUS_ATTEMPTS) {
            if (focused || InputClock.lastKeyMs > openedAt) return@LaunchedEffect
            val ok = runCatching { first.requestFocus() }.getOrDefault(false)
            if (ok) return@LaunchedEffect
            delay(50)
        }
    }
    val firstMod = Modifier.focusRequester(first).onFocusChanged { focused = it.isFocused }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (touch) 24.dp else 72.design, vertical = if (touch) 24.dp else 56.design),
        verticalArrangement = Arrangement.spacedBy(if (touch) 20.dp else 36.design, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            fontSize = if (touch) 20.sp else 44.spx, lineHeight = if (touch) 26.sp else 56.spx,
            modifier = Modifier.then(if (touch) Modifier.fillMaxWidth() else Modifier.widthIn(max = 1100.design)),
        )
        if (touch) Column(Modifier.fillMaxWidth().widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (retryLabel != null) PillButton(retryLabel, onRetry, Modifier.fillMaxWidth().testTag("dt-state-retry"), heightPx = 96, fontPx = 30, bg = Ux.Cta)
            PillButton(backLabel, onBack, Modifier.fillMaxWidth().testTag("dt-state-back"), heightPx = 96, fontPx = 30)
        } else Row(horizontalArrangement = Arrangement.spacedBy(20.design), verticalAlignment = Alignment.CenterVertically) {
            if (retryLabel != null) {
                PillButton(retryLabel, onRetry, firstMod.testTag("dt-state-retry"), heightPx = 64, fontPx = 22, bg = Ux.Cta, ringWidth = 4.design)
                PillButton(backLabel, onBack, Modifier.testTag("dt-state-back"), heightPx = 64, fontPx = 22, ringWidth = 4.design)
            } else PillButton(backLabel, onBack, firstMod.testTag("dt-state-back"), heightPx = 64, fontPx = 22, ringWidth = 4.design)
        }
    }
}

/**
 * Colonne des épisodes sans épisode à montrer : chargement en cours, échec de chargement (cause + « Réessayer », seulement
 * quand [error] est donnée, c'est-à-dire qu'aucun épisode n'est connu), ou vraiment aucun épisode. `testTag` :
 * `dt-episodes-empty`, `dt-episodes-retry`.
 */
@Composable
fun EpisodesEmptyState(loading: Boolean, error: SyncErrorKind?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val touch = LocalTouch.current
    val S = LocalStrings.current
    val D = LocalDs.current
    val bodySize = if (touch) 14.sp else 24.spx
    if (loading || error == null) {
        Text(
            if (loading) S.detailLoading else S.seriesNoEpisodes, color = Ux.Text3, fontFamily = Manrope, fontSize = bodySize,
            modifier = modifier.testTag("dt-episodes-empty"),
        )
        return
    }
    Column(modifier.testTag("dt-episodes-empty"), verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 12.design)) {
        Text(D.episodesLoadError, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 16.sp else 28.spx)
        Text(S.sync.messageFor(error), color = Ux.Text3, fontFamily = Manrope, fontSize = bodySize)
        PillButton(
            D.retry, onRetry, Modifier.testTag("dt-episodes-retry").then(if (touch) Modifier.fillMaxWidth() else Modifier),
            heightPx = if (touch) 96 else 52, fontPx = if (touch) 30 else 18, bg = Ux.Cta, ringWidth = 4.design,
        )
    }
}

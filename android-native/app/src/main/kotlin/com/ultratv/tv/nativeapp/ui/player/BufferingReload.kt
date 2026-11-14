package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.streamInfoReload
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.player.engine.PlaybackSession
import kotlinx.coroutines.delay

/**
 * Mise en mémoire tampon prolongée (> [StreamInfoFormat.RELOAD_OFFER_MS]) : bouton « Recharger » qui relance le flux sur la même
 * chaîne / position. Il prend le focus (OK à la télécommande) et le rend à la racine du lecteur quand il disparaît.
 */
@Composable
internal fun BufferingReload(session: PlaybackSession, D: DesignStrings, rootFocus: FocusRequester) {
    val st by session.state.collectAsState()
    var offer by remember { mutableStateOf(false) }
    LaunchedEffect(st.buffering) {
        if (st.buffering) { delay(StreamInfoFormat.RELOAD_OFFER_MS); offer = true } else offer = false
    }
    if (!offer || !st.buffering) return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { runCatching { rootFocus.requestFocus() } } }
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        PillButton(D.streamInfoReload, { offer = false; session.reload() }, modifier = Modifier.focusRequester(focus), heightPx = 64, hPadPx = 36, fontPx = 24)
    }
}

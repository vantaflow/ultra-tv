package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.config.HandoffAccept
import com.ultratv.tv.nativeapp.data.config.HandoffInbox
import com.ultratv.tv.nativeapp.data.config.HandoffLogic
import com.ultratv.tv.nativeapp.data.config.HandoffRequest
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.handoffBody
import com.ultratv.tv.nativeapp.i18n.handoffNo
import com.ultratv.tv.nativeapp.i18n.handoffNotFound
import com.ultratv.tv.nativeapp.i18n.handoffTitle
import com.ultratv.tv.nativeapp.i18n.handoffYes
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.design.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HandoffViewModel @Inject constructor(
    val inbox: HandoffInbox,
    private val accept: HandoffAccept,
) : ViewModel() {
    /** « Oui » : lance la lecture dans le catalogue local ; [onMissing] si la source ou le titre n'existe pas ici. */
    fun yes(r: HandoffRequest, onMissing: () -> Unit) {
        inbox.resolve(r.id)
        viewModelScope.launch { if (!accept.accept(r)) onMissing() }
    }
    fun no(r: HandoffRequest) = inbox.resolve(r.id)
}

/**
 * « Reprendre sur cet appareil ? » : demande envoyée par un autre appareil du compte. Rien ne démarre seul, la lecture ne
 * part que sur « Oui » ; sans réponse, la question disparaît après 2 minutes.
 */
@Composable
fun HandoffHost(vm: HandoffViewModel = hiltViewModel()) {
    val D = LocalDs.current
    val pending by vm.inbox.pending.collectAsState()
    LaunchedEffect(pending) {
        if (pending.isEmpty()) return@LaunchedEffect
        val wait = pending.minOf { it.second + HandoffLogic.TTL_MS } - System.currentTimeMillis()
        delay(wait.coerceAtLeast(1_000)); vm.inbox.expire()
    }
    val r = pending.firstOrNull()?.first ?: return
    val first = remember(r.id) { FocusRequester() }
    LaunchedEffect(r.id) { runCatching { first.requestFocus() } }
    ModalFocusScope(onBack = { vm.no(r) }, modifier = Modifier.background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 1100.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.design)) {
            Text(D.handoffTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(D.handoffBody(r.fromName.ifBlank { "·" }, r.title), color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                PillButton(D.handoffYes, { vm.yes(r) { Toaster.err(D.handoffNotFound) } }, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(first))
                PillButton(D.handoffNo, { vm.no(r) })
            }
        }
    }
}

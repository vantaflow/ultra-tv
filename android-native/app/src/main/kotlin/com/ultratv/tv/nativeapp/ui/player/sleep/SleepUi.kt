package com.ultratv.tv.nativeapp.ui.player.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.player.PlayerExtraStrings

/** Choix de la minuterie : 30 / 60 / 90 min, fin du programme (si le guide en connaît un), arrêt. */
@Composable
fun SleepMenu(X: PlayerExtraStrings, programmeEndMs: Long?, active: Boolean, onPick: (SleepChoice?) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(min = 620.design), verticalArrangement = Arrangement.spacedBy(18.design), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(X.sleepTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 1)
            PillButton("30 min", { onPick(SleepChoice.Minutes(30)) }, modifier = Modifier.focusRequester(first))
            PillButton("60 min", { onPick(SleepChoice.Minutes(60)) })
            PillButton("90 min", { onPick(SleepChoice.Minutes(90)) })
            if (programmeEndMs != null && programmeEndMs > System.currentTimeMillis()) PillButton(X.sleepEndOfProgramme, { onPick(SleepChoice.UntilMs(programmeEndMs)) })
            if (active) PillButton(X.sleepOff, { onPick(null) })
            PillButton(X.close, onClose)
        }
    }
}

/** « Toujours là ? » : une minute avant l'arrêt. OK = je suis là (la minuterie repart) ; sans réponse, le flux s'arrête. */
@Composable
fun AreYouThereDialog(X: PlayerExtraStrings, secondsLeft: Int, onStay: () -> Unit, onStop: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    ModalFocusScope(onBack = onStay, modifier = Modifier.background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.design)) {
            Text(X.stillThere, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, maxLines = 1)
            Text(X.sleepStopsIn(secondsLeft), color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                PillButton(X.imHere, onStay, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(first))
                PillButton(X.sleepNow, onStop)
            }
        }
    }
}

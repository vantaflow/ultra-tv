package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * Fiche « programme » en surimpression (touche Info). Direct : programme en cours puis les deux suivants ; VOD : titre, année,
 * durée, résumé. Les données viennent de la base locale, lues UNE fois à l'ouverture (hors fil principal). Retour la ferme.
 */
@Composable
internal fun InfoCard(vm: PlayerViewModel, isLive: Boolean, title: String, durMs: Long, X: PlayerExtraStrings, thenLabel: String, onClose: () -> Unit) {
    val nowProg by vm.nowProgramme.collectAsState()
    var upcoming by remember { mutableStateOf<List<EpgEntity>>(emptyList()) }
    var facts by remember { mutableStateOf<VodFacts?>(null) }
    LaunchedEffect(Unit) { if (isLive) upcoming = vm.upcoming(2) else facts = vm.vodFacts() }
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 1100.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(48.design),
            verticalArrangement = Arrangement.spacedBy(14.design),
        ) {
            if (isLive) {
                val n = nowProg
                Text(X.infoNow, color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1)
                Text(n?.title ?: title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, lineHeight = 46.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n != null) {
                    val len = ProgrammeInfo.durationLabel(n.endMs - n.startMs)
                    Text(EpgClock.range(n.startMs, n.endMs) + (len?.let { " · $it" } ?: ""), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1)
                    n.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                } else Text(X.infoNoGuide, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, maxLines = 2)
                upcoming.forEach { u ->
                    Column(Modifier.padding(top = 8.design), verticalArrangement = Arrangement.spacedBy(4.design)) {
                        Text("$thenLabel ${EpgClock.hm(u.startMs)}", color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1)
                        Text(u.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, lineHeight = 46.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(facts?.year?.toString(), ProgrammeInfo.durationLabel(durMs), facts?.genre?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                facts?.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 8, overflow = TextOverflow.Ellipsis) }
            }
            PillButton(X.close, onClose, modifier = Modifier.padding(top = 12.design).focusRequester(close))
        }
    }
}

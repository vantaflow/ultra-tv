package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.reminders.ReminderBus
import com.ultratv.tv.nativeapp.data.reminders.ReminderEvent
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.startsInOneMin
import com.ultratv.tv.nativeapp.i18n.watchWord
import com.ultratv.tv.nativeapp.nav.DeepLinkHandler
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class ReminderBannerViewModel @Inject constructor(private val links: DeepLinkHandler) : ViewModel() {
    fun watch(e: ReminderEvent) { viewModelScope.launch { links.playLive(e.providerId, e.channelRemoteId) } }
}

/**
 * Bannière in-app d'un rappel arrivé à échéance (maquette GuideActions : « Commence dans 1 min · Regarder »).
 * Elle ne prend jamais le focus d'office (elle ne doit pas voler les touches au lecteur) ; la notification
 * système porte le même lien profond.
 */
@Composable
fun ReminderBannerHost(vm: ReminderBannerViewModel = hiltViewModel()) {
    val D = LocalDs.current
    var current by remember { mutableStateOf<ReminderEvent?>(null) }
    LaunchedEffect(Unit) { ReminderBus.events.collect { current = it } }
    LaunchedEffect(current) { if (current != null) { delay(12_000); current = null } }
    val e = current ?: return
    Box(Modifier.fillMaxSize().padding(end = 96.design, top = 54.design), contentAlignment = Alignment.TopEnd) {
        Row(
            Modifier.width(560.design).clip(RoundedCornerShape(22.design)).background(Ux.Surface).border(2.design, Ux.Accent, RoundedCornerShape(22.design)).padding(horizontal = 28.design, vertical = 24.design),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.design),
        ) {
            Box(Modifier.size(56.design).clip(CircleShape).background(Ux.Accent), contentAlignment = Alignment.Center) {
                DIcon("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 0 0 4 0", 28.design, Ux.White, strokeWidth = 2.2f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(D.startsInOneMin, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
                Text("${e.programmeTitle} · ${e.channelName}", color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FocusSurface(onClick = { vm.watch(e); current = null }, shape = RoundedCornerShape(24.design), bg = Ux.Cta, focusedScale = 1f, ringWidth = 4.design, modifier = Modifier.height(48.design)) { _ ->
                Box(Modifier.padding(horizontal = 20.design).height(48.design), contentAlignment = Alignment.Center) {
                    Text(D.watchWord, color = Ux.TextOnLight, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
                }
            }
        }
    }
}

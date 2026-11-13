package com.ultratv.tv.nativeapp.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import androidx.compose.runtime.produceState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.SimpleStrings
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.parental.PinPromptDialog

/**
 * Liste de chaînes du mode simple (maquette « Mode simple ») : deux onglets seulement (Toutes / Mes favoris), une
 * colonne de grandes lignes (numéro, logo, nom, programme en cours) et, sur TV, l'aperçu « En ce moment » de la chaîne
 * focalisée (horaire, progression, programme suivant). 120 chaînes au plus. OK lance la chaîne ; haut / bas du lecteur
 * passe à la voisine de CETTE liste ; Retour ramène aux grandes tuiles.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleChannelsScreen(
    onPlay: (url: String, title: String) -> Unit,
    vm: SimpleChannelsViewModel = hiltViewModel(),
) {
    val S = SimpleStrings(LocalDs.current.lang)
    val rows by vm.rows.collectAsStateWithLifecycle()
    val nowNext by vm.nowNext.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<SimpleChannelRow?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<Long?>(null) }
    val touch = LocalTouch.current
    val all = rows?.distinctBy { it.channel.id }
    val hasFavs = all?.any { it.favorite } == true
    val list = all?.let { l -> if (favOnly && hasFavs) l.filter { it.favorite } else l }

    val play: (SimpleChannelRow) -> Unit = { r -> onPlay(vm.prepare(r.channel, list.orEmpty()), r.channel.title) }
    val clock by produceState(System.currentTimeMillis()) { while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() } }

    Row(
        Modifier.fillMaxSize().background(Ux.Bg).then(if (touch) Modifier.padding(16.design) else Modifier.padding(start = 96.design, end = 96.design, top = 56.design, bottom = 48.design)),
        horizontalArrangement = Arrangement.spacedBy(56.design),
    ) {
        Column(if (touch) Modifier.fillMaxSize() else Modifier.width(1040.design).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.design)) {
            Text(S.tileLive, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 60.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hasFavs) Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                TabPill(S.tabAll, !favOnly) { favOnly = false }
                TabPill(S.tileFavorites, favOnly) { favOnly = true }
            }
            when {
                list == null -> Text(S.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx)
                list.isEmpty() -> Text(S.channelsEmpty, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx)
                else -> LazyColumn(
                    Modifier.fillMaxSize().focusRestorer().focusGroup(),
                    contentPadding = PaddingValues(top = 8.design, bottom = 40.design),
                    verticalArrangement = Arrangement.spacedBy(16.design),
                ) {
                    items(list, key = { it.channel.id }) { r ->
                        ChannelBigRow(
                            r, nowNext[r.channel.id]?.first?.title,
                            onFocus = { focusedId = r.channel.id },
                            onClick = { if (r.locked) pinFor = r else play(r) },
                        )
                    }
                }
            }
        }
        // Aperçu de la chaîne focalisée (TV) : ce qui passe, jusqu'à quand, et ce qui suit.
        if (!touch) {
            val r = list?.firstOrNull { it.channel.id == focusedId } ?: list?.firstOrNull()
            NowPanel(S, r, r?.let { nowNext[it.channel.id] }, clock, Modifier.weight(1f).fillMaxHeight())
        }
    }

    pinFor?.let { r -> PinPromptDialog(title = r.channel.title, onUnlocked = { pinFor = null; play(r) }, onCancel = { pinFor = null }) }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(999.design), bg = if (selected) Ux.Text else Ux.Surface2, ringWidth = 6.design, focusedScale = 1f, modifier = Modifier.height(72.designT)) { f ->
        Box(Modifier.fillMaxHeight().padding(horizontal = 32.design), contentAlignment = Alignment.Center) {
            Text(label, color = if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 30.spx, maxLines = 1)
        }
    }
}

@Composable
private fun ChannelBigRow(r: SimpleChannelRow, now: String?, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(28.design),
        bg = Ux.Surface2,
        ringWidth = 8.design,
        focusedScale = 1.0f,
        modifier = Modifier.fillMaxWidth().height(120.designT).onFocusChanged { if (it.isFocused) onFocus() },
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 32.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.design)) {
            val n = com.ultratv.tv.nativeapp.ui.player.zap.ZapLogic.numberOf(r.channel)
            Text(if (n > 0) "$n" else "", color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 48.spx, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(110.design))
            com.ultratv.tv.nativeapp.ui.design.LogoBox(r.channel.logo, r.channel.title, Modifier.width(128.design).height(76.design), radius = 16, pad = 8, bg = if (f) Ux.Line else Ux.Surface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(
                    (if (r.locked) "🔒 " else "") + r.channel.title,
                    color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!now.isNullOrBlank()) Text(now, color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 28.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (r.favorite) DIcon(Icons.Heart, 40.design, if (f) Ux.TextOnLight else Ux.AccentInk, fill = true)
        }
    }
}

/** Aperçu « En ce moment » : logo, programme en cours, horaire, progression, programme suivant, et les touches utiles. */
@Composable
private fun NowPanel(
    S: SimpleStrings, r: SimpleChannelRow?, nn: Pair<com.ultratv.tv.nativeapp.data.db.EpgEntity?, com.ultratv.tv.nativeapp.data.db.EpgEntity?>?, now: Long, modifier: Modifier,
) {
    Column(modifier.padding(top = 4.design), verticalArrangement = Arrangement.spacedBy(28.design)) {
        Box(Modifier.fillMaxWidth().height(360.design).clip(RoundedCornerShape(32.design)).background(Ux.Surface2), contentAlignment = Alignment.Center) {
            if (r != null) com.ultratv.tv.nativeapp.ui.design.LogoBox(r.channel.logo, r.channel.title, Modifier.width(320.design).height(190.design), radius = 24, pad = 20, bg = Ux.Surface)
        }
        val cur = nn?.first
        if (r != null) Text(r.channel.title, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (cur != null) {
            Text(S.nowLabel.uppercase(), color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.spx, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(999.design)).background(Ux.Accent).padding(horizontal = 18.design, vertical = 6.design))
            Text(cur.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 46.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${EpgClock.hm(cur.startMs)} – ${EpgClock.hm(cur.endMs)}", color = Ux.Text2, fontFamily = Manrope, fontSize = 30.spx, maxLines = 1)
            val frac = if (cur.endMs > cur.startMs) ((now - cur.startMs).toFloat() / (cur.endMs - cur.startMs)).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(12.design).clip(RoundedCornerShape(6.design)).background(Ux.Line)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(6.design)).background(Ux.Accent))
            }
            nn.second?.let { nx -> Text("${S.nextLabel} · ${EpgClock.hm(nx.startMs)} · ${nx.title}", color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(40.design)) {
            KeyHintBig("OK", S.keyWatch)
            KeyHintBig("↑ ↓", S.keyChange)
        }
    }
}

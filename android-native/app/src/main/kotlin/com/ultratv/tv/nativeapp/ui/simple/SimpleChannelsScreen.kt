package com.ultratv.tv.nativeapp.ui.simple

import androidx.compose.foundation.background
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
import androidx.tv.material3.Text
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
 * Liste de chaînes du mode simple : une colonne de lignes de 112 px au texte de 44 px, favoris d'abord. Aucun logo
 * (pas d'image à charger), 120 chaînes au plus. OK lance la chaîne ; haut / bas du lecteur passe à la voisine de
 * cette liste ; Retour ramène à l'écran des grandes tuiles.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleChannelsScreen(
    onPlay: (url: String, title: String) -> Unit,
    vm: SimpleChannelsViewModel = hiltViewModel(),
) {
    val S = SimpleStrings(LocalDs.current.lang)
    val rows by vm.rows.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<SimpleChannelRow?>(null) }
    val list = rows

    val play: (SimpleChannelRow) -> Unit = { r -> onPlay(vm.prepare(r.channel, list.orEmpty()), r.channel.title) }

    Column(Modifier.fillMaxSize().background(Ux.Bg).padding(horizontal = 72.design, vertical = 40.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
        Text(S.channelsTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (list?.any { it.favorite } == true) Text(S.channelsFavoritesFirst, color = Ux.Text2, fontFamily = Manrope, fontSize = 28.spx, maxLines = 1)
        Spacer(Modifier.height(12.design))
        when {
            list == null -> Text(S.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx)
            list.isEmpty() -> Text(S.channelsEmpty, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx)
            else -> LazyColumn(
                Modifier.fillMaxSize().focusRestorer().focusGroup(),
                contentPadding = PaddingValues(bottom = 40.design),
                verticalArrangement = Arrangement.spacedBy(14.design),
            ) {
                items(list.distinctBy { it.channel.id }, key = { it.channel.id }) { r ->
                    ChannelBigRow(r, onClick = { if (r.locked) pinFor = r else play(r) })
                }
            }
        }
    }

    pinFor?.let { r -> PinPromptDialog(title = r.channel.title, onUnlocked = { pinFor = null; play(r) }, onCancel = { pinFor = null }) }
}

@Composable
private fun ChannelBigRow(r: SimpleChannelRow, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(28.design),
        bg = Ux.Surface2,
        ringWidth = 8.design,
        focusedScale = 1.0f,
        modifier = Modifier.fillMaxWidth().height(112.designT),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 36.design), verticalAlignment = Alignment.CenterVertically) {
            // Cœur plein pour un favori ; case vide sinon : les titres restent alignés.
            if (r.favorite) DIcon(Icons.Heart, 44.design, if (f) Ux.TextOnLight else Ux.AccentInk, fill = true) else Spacer(Modifier.width(44.design))
            Spacer(Modifier.width(28.design))
            Text(
                (if (r.locked) "🔒 " else "") + r.channel.title,
                color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 44.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.design))
            DIcon(Icons.Play, 40.design, if (f) Ux.TextOnLight else Ux.Text2, fill = true)
        }
    }
}
